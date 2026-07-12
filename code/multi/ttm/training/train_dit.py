"""
DiT 预训练和微调脚本
支持分布式训练 (DDP) 和 EMA 权重更新
基于 ../dit_transformer/train.py 改编
"""
import os
import argparse
import yaml
import torch
import torch.distributed as dist
from torch.nn.parallel import DistributedDataParallel as DDP
from torch.utils.data import DataLoader
from torch.utils.data.distributed import DistributedSampler
from collections import OrderedDict
from copy import deepcopy
from time import time
import logging

import sys
sys.path.append('..')
from models.dit import DiT_models, DiT
from models.dit.diffusion import create_diffusion
from data.dataset import MelDataset, create_dataloader
from training.trainer import BaseTrainer


def requires_grad(model, flag=True):
    """设置所有参数 requires_grad 标志"""
    for p in model.parameters():
        p.requires_grad = flag


def update_ema(ema_model, model, decay=0.9999):
    """EMA 更新"""
    ema_params = OrderedDict(ema_model.named_parameters())
    model_params = OrderedDict(model.named_parameters())

    for name, param in model_params.items():
        ema_params[name].mul_(decay).add_(param.data, alpha=1 - decay)


def cleanup():
    """结束 DDP"""
    dist.destroy_process_group()


def create_logger(logging_dir):
    """创建日志"""
    if dist.is_initialized():
        rank = dist.get_rank()
    else:
        rank = 0

    if rank == 0:
        logging.basicConfig(
            level=logging.INFO,
            format='[\033[34m%(asctime)s\033[0m] %(message)s',
            datefmt='%Y-%m-%d %H:%M:%S',
            handlers=[
                logging.StreamHandler(),
                logging.FileHandler(f"{logging_dir}/log.txt")
            ]
        )
        logger = logging.getLogger(__name__)
    else:
        logger = logging.getLogger(__name__)
        logger.addHandler(logging.NullHandler())
    return logger


class DiTTrainer:
    """DiT 训练器"""

    def __init__(
        self,
        model: DiT,
        diffusion,
        vae,
        train_loader,
        device,
        config: dict,
        logger: logging.Logger,
        checkpoint_dir: str
    ):
        self.model = model
        self.diffusion = diffusion
        self.vae = vae  # 预训练的 VAE，用于编码得到 latents
        self.train_loader = train_loader
        self.device = device
        self.config = config
        self.logger = logger
        self.checkpoint_dir = checkpoint_dir

        self.learning_rate = config['training']['learning_rate']
        self.batch_size = config['training']['batch_size']
        self.max_steps = config['training']['max_steps']
        self.warmup_steps = config['training']['warmup_steps']
        self.use_ema = config['training'].get('use_ema', True)

        # EMA 模型
        if self.use_ema:
            self.ema = deepcopy(model).to(device)
            requires_grad(self.ema, False)
            update_ema(self.ema, model, decay=0)  # 初始化权重同步
            self.ema.eval()

        # 优化器
        self.opt = torch.optim.AdamW(
            model.parameters(),
            lr=self.learning_rate,
            weight_decay=config['training'].get('weight_decay', 0)
        )

        # 混合精度
        self.scaler = torch.cuda.amp.GradScaler()
        self.use_amp = config['training'].get('use_amp', True)

        # 训练状态
        self.global_step = 0
        self.epoch = 0

        # 打印参数
        total_params = sum(p.numel() for p in model.parameters())
        self.logger.info(f"DiT Parameters: {total_params:,}")

    def train_step(self, batch):
        """单步训练"""
        batch = batch.to(self.device)

        # 使用预训练 VAE 编码得到 latent
        with torch.no_grad():
            # VAE 编码得到 mu，直接用 mu 作为 latent
            # 因为我们训练 DiT 在 VAE latent 空间生成
            mu, log_var = self.vae.encode(batch)
            # 使用 reparemeterization 采样 z
            std = torch.exp(0.5 * log_var)
            eps = torch.randn_like(std)
            x_start = mu + std * eps  # [B, C, H, W]

            # VAE 输出形状是 [B, C, n_mels//16, time//16]
            # 我们 DiT 需要 [B, C, T] 其中 T = (n_mels//16) * (time//16)
            # 不对：mel-spectrogram 是 n_mels (频率) × time，压缩后还是 2D
            # DiT 这里处理 1D 时间序列，我们把频率和通道合并：B, C*H, W
            B, C, H, W = x_start.shape
            x_start = x_start.permute(0, 1, 3, 2)  # [B, C, W, H]
            x_start = x_start.reshape(B, C * H, W)  # [B, C*H, W] → 现在是 1D

        # 采样时间步
        t = torch.randint(
            0,
            self.diffusion.num_train_timesteps,
            (x_start.shape[0],),
            device=self.device
        )

        # 模型 kwargs：条件是语义 tokens，如果没有预先生成，我们用 K-means 量化结果
        # 这里简化处理：对于无标签预训练，我们使用全空条件，但实际上应该用数据依赖
        # 如果是微调配对数据，cond_tokens 已经存在，这里需要修改
        B = x_start.shape[0]
        # 临时：用全零作为条件 token（实际预训练无标签时应该改）
        # 在预训练阶段，我们可以使用无条件，即每个样本都是空条件
        cond_tokens = torch.full(
            (B, 1),
            self.model.y_embedder.num_semantic_tokens,
            dtype=torch.long,
            device=self.device
        )
        model_kwargs = dict(y=cond_tokens)

        # 计算损失
        with torch.cuda.amp.autocast(enabled=self.use_amp):
            loss_dict = self.diffusion.training_losses(
                self.model, x_start, t, model_kwargs
            )
            loss = loss_dict['loss'].mean()

        # 反向传播
        self.opt.zero_grad()
        if self.use_amp:
            self.scaler.scale(loss).backward()
            self.scaler.step(self.opt)
            self.scaler.update()
        else:
            loss.backward()
            self.opt.step()

        # 更新 EMA
        if self.use_ema:
            update_ema(self.ema, self.model.module if dist.is_initialized() else self.model)

        return loss.item()

    def train(self, epochs, log_every=100, ckpt_every=50000):
        """训练循环"""
        if self.use_ema:
            # 确保 EMA 初始同步
            update_ema(self.ema, self.model.module if dist.is_initialized() else self.model)

        self.model.train()
        running_loss = 0.0
        log_steps = 0
        start_time = time()

        for epoch in range(epochs):
            self.epoch = epoch
            if dist.is_initialized():
                self.train_loader.sampler.set_epoch(epoch)

            for batch in self.train_loader:
                loss = self.train_step(batch)
                running_loss += loss
                log_steps += 1
                self.global_step += 1

                if self.global_step % log_every == 0:
                    # 日志
                    torch.cuda.synchronize()
                    end_time = time()
                    steps_per_sec = log_steps / (end_time - start_time)
                    avg_loss = running_loss / log_steps

                    if dist.is_initialized():
                        avg_loss_tensor = torch.tensor(avg_loss, device=self.device)
                        dist.all_reduce(avg_loss_tensor)
                        avg_loss = avg_loss_tensor.item() / dist.get_world_size()

                    self.logger.info(
                        f"(step={self.global_step:07d}) "
                        f"Train Loss: {avg_loss:.4f}, "
                        f"Steps/Sec: {steps_per_sec:.2f}"
                    )

                    running_loss = 0.0
                    log_steps = 0
                    start_time = time()

                # 保存检查点
                if self.global_step % ckpt_every == 0 and self.global_step > 0:
                    if not dist.is_initialized() or dist.get_rank() == 0:
                        self.save_checkpoint(f"dit_{self.global_step:07d}.pt")
                    if dist.is_initialized():
                        dist.barrier()

        self.logger.info("Training completed!")
        if not dist.is_initialized() or dist.get_rank() == 0:
            self.save_checkpoint("dit_final.pt")
        cleanup()

    def save_checkpoint(self, filename):
        """保存检查点"""
        checkpoint = {
            'model': self.model.module.state_dict() if dist.is_initialized() else self.model.state_dict(),
            'optimizer': self.opt.state_dict(),
            'global_step': self.global_step,
            'epoch': self.epoch,
            'config': self.config
        }
        if self.use_ema:
            checkpoint['ema'] = self.ema.state_dict()

        path = os.path.join(self.checkpoint_dir, filename)
        torch.save(checkpoint, path)
        self.logger.info(f"Saved checkpoint to {path}")

    def load_checkpoint(self, path):
        """加载检查点"""
        checkpoint = torch.load(path, map_location=self.device)
        if dist.is_initialized():
            self.model.module.load_state_dict(checkpoint['model'])
        else:
            self.model.load_state_dict(checkpoint['model'])
        self.opt.load_state_dict(checkpoint['optimizer'])
        if self.use_ema and 'ema' in checkpoint:
            self.ema.load_state_dict(checkpoint['ema'])
        self.global_step = checkpoint.get('global_step', 0)
        self.epoch = checkpoint.get('epoch', 0)
        self.logger.info(f"Loaded checkpoint from step {self.global_step}")


def main():
    parser = argparse.ArgumentParser(description="DiT Training")
    parser.add_argument('--config', type=str, default='../configs/dit_model.yaml',
                      help='配置文件路径')
    parser.add_argument('--data-path', type=str, default='data_processed/train',
                      help='训练数据目录')
    parser.add_argument('--results-dir', type=str, default='results',
                      help='结果目录')
    parser.add_argument('--model', type=str, default='DiT-B/2',
                      choices=list(DiT_models.keys()), help='DiT 模型大小')
    parser.add_argument('--vae-ckpt', type=str, required=True,
                      help='预训练 VAE 检查点路径')
    parser.add_argument('--epochs', type=int, default=100,
                      help='训练 epochs')
    parser.add_argument('--global-batch-size', type=int, default=128,
                      help='全局 batch size')
    parser.add_argument('--global-seed', type=int, default=0,
                      help='随机种子')
    parser.add_argument('--num-workers', type=int, default=4,
                      help='数据加载 workers')
    parser.add_argument('--log-every', type=int, default=100,
                      help='日志频率')
    parser.add_argument('--ckpt-every', type=int, default=50000,
                      help='检查点保存频率')
    parser.add_argument('--resume', type=str, default=None,
                      help='恢复检查点路径')
    args = parser.parse_args()

    # 加载配置
    with open(args.config, 'r', encoding='utf-8') as f:
        config = yaml.safe_load(f)

    # 分布式训练初始化
    assert torch.cuda.is_available(), "Training currently requires at least one GPU."
    dist.init_process_group("nccl")
    rank = dist.get_rank()
    device = rank % torch.cuda.device_count()
    seed = args.global_seed * dist.get_world_size() + rank
    torch.manual_seed(seed)
    torch.cuda.set_device(device)
    print(f"Starting rank={rank}, seed={seed}, world_size={dist.get_world_size()}.")

    # 创建实验目录
    if rank == 0:
        os.makedirs(args.results_dir, exist_ok=True)
        experiment_index = len(os.listdir(args.results_dir))
        model_string_name = args.model.replace("/", "-")
        experiment_dir = os.path.join(
            args.results_dir,
            f"{experiment_index:03d}-{model_string_name}"
        )
        checkpoint_dir = os.path.join(experiment_dir, "checkpoints")
        os.makedirs(checkpoint_dir, exist_ok=True)
        logger = create_logger(experiment_dir)
        logger.info(f"Experiment directory created at {experiment_dir}")
    else:
        checkpoint_dir = None
        logger = create_logger(None)

    # 加载预训练 VAE
    logger.info(f"Loading pre-trained VAE from {args.vae_ckpt}")
    from models.vae import VAE
    vae_ckpt = torch.load(args.vae_ckpt, map_location=device)
    vae_config = vae_ckpt.get('config', {}).get('model', {})
    vae = VAE(
        in_channels=vae_config.get('in_channels', 1),
        base_channels=vae_config.get('base_channels', 64),
        latent_channels=vae_config.get('latent_channels', 32),
        num_downsampling_layers=vae_config.get('num_downsampling_layers', 4),
    ).to(device)
    if 'model_state_dict' in vae_ckpt:
        vae.load_state_dict(vae_ckpt['model_state_dict'])
    else:
        vae.load_state_dict(vae_ckpt)
    vae.eval()
    requires_grad(vae, False)
    logger.info("VAE loaded and frozen")

    # 获取 VAE latent 维度来创建 DiT
    # 输入 mel: [1, 128, 1024]
    # 经过 VAE 4 次下采样 → [32, 8, 64]
    # 我们 reshape 为 [32*8, 64] = [256, 64]
    model_config = config['model']
    num_semantic_tokens = config.get('num_semantic_tokens', 1024)

    # 计算输入 latent 长度
    # 输入 mel 时间长度假设是 1024 → 压缩 16 倍 → 64
    # 频率 128 → 压缩 16 倍 → 8
    # 所以最终 latent shape 是 [C*8, 64] = [32*8, 64] = [256, 64]
    input_size = 64  # 时间长度

    model = DiT_models[args.model](
        input_size=input_size,
        in_channels=model_config.get('in_channels', 32 * 8),  # 32 channels × 8 freq bins
        hidden_size=model_config.get('hidden_size', 768),
        depth=model_config.get('depth', 12),
        num_heads=model_config.get('num_heads', 12),
        patch_size=model_config.get('patch_size', 2),
        mlp_ratio=model_config.get('mlp_ratio', 4.0),
        num_semantic_tokens=num_semantic_tokens,
        cond_dropout_prob=model_config.get('cond_dropout_prob', 0.1),
        learn_sigma=True
    ).to(device)

    # 创建扩散过程
    diffusion_config = config['diffusion']
    diffusion = create_diffusion(
        num_train_timesteps=diffusion_config.get('num_train_timesteps', 1000),
        beta_start=diffusion_config.get('beta_start', 0.0001),
        beta_end=diffusion_config.get('beta_end', 0.02),
        scheduler_type=diffusion_config.get('scheduler_type', 'ddpm')
    )

    # 创建数据集
    train_dataset = MelDataset(args.data_path, augment=True)
    sampler = DistributedSampler(
        train_dataset,
        num_replicas=dist.get_world_size(),
        rank=rank,
        shuffle=True,
        seed=args.global_seed
    )
    batch_size = int(args.global_batch_size // dist.get_world_size())
    train_loader = DataLoader(
        train_dataset,
        batch_size=batch_size,
        shuffle=False,
        sampler=sampler,
        num_workers=args.num_workers,
        pin_memory=True,
        drop_last=True
    )
    logger.info(f"Dataset contains {len(train_dataset):,} images")

    # 创建训练器
    trainer = DiTTrainer(
        model=model,
        diffusion=diffusion,
        vae=vae,
        train_loader=train_loader,
        device=device,
        config=config,
        logger=logger,
        checkpoint_dir=checkpoint_dir
    )

    # 恢复检查点
    if args.resume is not None:
        trainer.load_checkpoint(args.resume)

    # 开始训练
    trainer.train(
        epochs=args.epochs,
        log_every=args.log_every,
        ckpt_every=args.ckpt_every
    )


if __name__ == "__main__":
    main()

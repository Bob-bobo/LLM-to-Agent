"""
VAE 预训练和微调脚本
"""
import os
import argparse
import yaml
import torch
import torch.nn as nn
import torch.optim as optim
from torch.utils.data import DataLoader
import numpy as np
from tqdm import tqdm
from datetime import datetime

# 导入项目模块
import sys
sys.path.append('..')
from models.vae import VAE, vae_loss
from data.dataset import MelDataset, create_dataloader, get_default_preprocessing_config
from training.trainer import BaseTrainer


class VAETrainer(BaseTrainer):
    """VAE 训练器"""

    def __init__(
        self,
        model: VAE,
        train_loader: DataLoader,
        val_loader: Optional[DataLoader],
        device: torch.device,
        config: dict,
        experiment_name: str = None
    ):
        super().__init__(
            model=model,
            device=device,
            config=config,
            experiment_name=experiment_name,
            log_dir=config.get('log_dir', 'logs'),
            checkpoint_dir=config.get('checkpoint_dir', 'checkpoints')
        )

        self.train_loader = train_loader
        self.val_loader = val_loader
        self.kl_weight = config['training']['kl_weight']
        self.learning_rate = config['training']['learning_rate']
        self.max_steps = config['training']['max_steps']
        self.warmup_steps = config['training']['warmup_steps']
        self.grad_clip = config['training']['grad_clip']
        self.batch_size = config['training']['batch_size']

        # 优化器
        self.optimizer = optim.AdamW(
            model.parameters(),
            lr=self.learning_rate,
            weight_decay=config['training'].get('weight_decay', 0.0)
        )

        # 学习率调度器（余弦衰减 + 热身）
        self.scheduler = self._get_scheduler()

        # 混合精度
        self.scaler = torch.cuda.amp.GradScaler()
        self.use_amp = config['training'].get('use_amp', True)

        # 打印模型信息
        total_params = sum(p.numel() for p in model.parameters())
        trainable_params = sum(p.numel() for p in model.parameters() if p.requires_grad)
        self.info(f"Total parameters: {total_params:,}")
        self.info(f"Trainable parameters: {trainable_params:,}")

    def _get_scheduler(self):
        """获取学习率调度器"""
        warmup_steps = self.warmup_steps
        total_steps = self.max_steps

        def lr_lambda(step):
            if step < warmup_steps:
                # 线性热身
                return float(step) / float(max(1, warmup_steps))
            # 余弦衰减
            progress = float(step - warmup_steps) / \
                float(max(1, total_steps - warmup_steps))
            return 0.5 * (1.0 + np.cos(np.pi * progress))

        return optim.lr_scheduler.LambdaLR(self.optimizer, lr_lambda)

    def train_epoch(self):
        """训练一个 epoch"""
        self.model.train()
        total_loss = 0.0
        total_recon = 0.0
        total_kl = 0.0
        num_batches = 0

        pbar = tqdm(self.train_loader, desc=f"Epoch {self.epoch+1}")
        for batch in pbar:
            batch = batch.to(self.device)

            # 前向传播
            with torch.cuda.amp.autocast(enabled=self.use_amp):
                x_recon, mu, log_var = self.model(batch)
                loss, recon_loss, kl_loss = vae_loss(
                    x_recon, batch, mu, log_var, self.kl_weight
                )

            # 反向传播
            self.optimizer.zero_grad()
            if self.use_amp:
                self.scaler.scale(loss).backward()
                if self.grad_clip > 0:
                    self.scaler.unscale_(self.optimizer)
                    nn.utils.clip_grad_norm_(self.model.parameters(), self.grad_clip)
                self.scaler.step(self.optimizer)
                self.scaler.update()
            else:
                loss.backward()
                if self.grad_clip > 0:
                    nn.utils.clip_grad_norm_(self.model.parameters(), self.grad_clip)
                self.optimizer.step()

            # 学习率更新
            self.scheduler.step()

            # 统计
            total_loss += loss.item()
            total_recon += recon_loss.item()
            total_kl += kl_loss.item()
            num_batches += 1

            # 日志
            avg_loss = total_loss / num_batches
            avg_recon = total_recon / num_batches
            avg_kl = total_kl / num_batches
            pbar.set_postfix({
                'loss': f'{avg_loss:.4f}',
                'recon': f'{avg_recon:.4f}',
                'kl': f'{avg_kl:.4f}',
                'lr': f'{self.scheduler.get_last_lr()[0]:.6f}'
            })

            # 记录到 TensorBoard
            if self.global_step % 100 == 0:
                self.log_scalar('train/loss', loss.item())
                self.log_scalar('train/recon_loss', recon_loss.item())
                self.log_scalar('train/kl_loss', kl_loss.item())
                self.log_scalar('train/lr', self.scheduler.get_last_lr()[0])

            self.global_step += 1

        #  epoch 平均
        avg_loss = total_loss / num_batches
        avg_recon = total_recon / num_batches
        avg_kl = total_kl / num_batches

        self.info(f"Epoch {self.epoch+1} - Train: loss={avg_loss:.4f}, recon={avg_recon:.4f}, kl={avg_kl:.4f}")

        self.epoch += 1
        return avg_loss, avg_recon, avg_kl

    @torch.no_grad()
    def validate(self):
        """验证"""
        if self.val_loader is None:
            return None, None

        self.model.eval()
        total_loss = 0.0
        total_recon = 0.0
        total_kl = 0.0
        num_batches = 0

        for batch in self.val_loader:
            batch = batch.to(self.device)
            x_recon, mu, log_var = self.model(batch)
            loss, recon_loss, kl_loss = vae_loss(
                x_recon, batch, mu, log_var, self.kl_weight
            )
            total_loss += loss.item()
            total_recon += recon_loss.item()
            total_kl += kl_loss.item()
            num_batches += 1

        avg_loss = total_loss / num_batches
        avg_recon = total_recon / num_batches
        avg_kl = total_kl / num_batches

        self.log_scalar('val/loss', avg_loss)
        self.log_scalar('val/recon_loss', avg_recon)
        self.log_scalar('val/kl_loss', avg_kl)

        self.info(f"Validation: loss={avg_loss:.4f}, recon={avg_recon:.4f}, kl={avg_kl:.4f}")

        return avg_loss, avg_recon, avg_kl

    def save(self, filename: str):
        """保存检查点"""
        checkpoint = {
            'model_state_dict': self.model.state_dict(),
            'optimizer_state_dict': self.optimizer.state_dict(),
            'scheduler_state_dict': self.scheduler.state_dict(),
            'global_step': self.global_step,
            'epoch': self.epoch,
            'config': self.config
        }
        return self.save_checkpoint(checkpoint, filename)

    def load(self, checkpoint_path: str):
        """加载检查点"""
        checkpoint = super().load(checkpoint_path)
        self.model.load_state_dict(checkpoint['model_state_dict'])
        self.optimizer.load_state_dict(checkpoint['optimizer_state_dict'])
        if 'scheduler_state_dict' in checkpoint:
            self.scheduler.load_state_dict(checkpoint['scheduler_state_dict'])
        self.global_step = checkpoint.get('global_step', 0)
        self.epoch = checkpoint.get('epoch', 0)
        self.info(f"Loaded checkpoint from step {self.global_step}")

    def train(self, epochs: int = None, save_every: int = None):
        """完整训练循环"""
        if epochs is None:
            epochs = self.config['training'].get('epochs', 100)
        if save_every is None:
            save_every = self.config['training'].get('save_every', 5)

        best_val_loss = float('inf')

        for epoch in range(epochs):
            self.train_epoch()

            # 验证
            if self.val_loader is not None and (epoch + 1) % 5 == 0:
                val_loss, _, _ = self.validate()
                if val_loss < best_val_loss:
                    best_val_loss = val_loss
                    self.save('vae_best.pt')
                    self.info(f"New best model saved with val loss {best_val_loss:.4f}")

            # 定期保存
            if (epoch + 1) % save_every == 0:
                self.save(f'veae_epoch_{epoch+1}.pt')

        # 保存最终模型
        self.save('vae_final.pt')
        self.info(f"Training completed! Best validation loss: {best_val_loss:.4f}")
        self.finalize()


def main():
    parser = argparse.ArgumentParser(description="VAE Training")
    parser.add_argument('--config', type=str, default='../configs/vae_model.yaml',
                      help='配置文件路径')
    parser.add_argument('--train-data', type=str, default='data_processed/train',
                      help='训练数据目录')
    parser.add_argument('--val-data', type=str, default=None,
                      help='验证数据目录')
    parser.add_argument('--experiment-name', type=str, default=None,
                      help='实验名称')
    parser.add_argument('--resume', type=str, default=None,
                        help='恢复训练的检查点路径')
    args = parser.parse_args()

    # 加载配置
    with open(args.config, 'r', encoding='utf-8') as f:
        config = yaml.safe_load(f)

    # 设置设备
    device = torch.device('cuda' if torch.cuda.is_available() else 'cpu')
    print(f"Using device: {device}")

    # 创建数据集
    train_dataset = MelDataset(args.train_data, augment=True)
    train_loader = create_dataloader(
        train_dataset,
        batch_size=config['training']['batch_size'],
        shuffle=True
    )

    if args.val_data is not None:
        val_dataset = MelDataset(args.val_data, augment=False)
        val_loader = create_dataloader(
            val_dataset,
            batch_size=config['training']['batch_size'],
            shuffle=False
        )
    else:
        val_loader = None

    # 创建模型
    model_config = config['model']
    model = VAE(
        in_channels=model_config.get('in_channels', 1),
        base_channels=model_config.get('base_channels', 64),
        latent_channels=model_config.get('latent_channels', 32),
        num_downsampling_layers=model_config.get('num_downsampling_layers', 4),
        dropout=model_config.get('dropout', 0.0)
    ).to(device)

    # 实验名称
    if args.experiment_name is None:
        exp_name = f"vae_{datetime.now().strftime('%Y%m%d_%H%M%S')}"
    else:
        exp_name = args.experiment_name

    # 创建训练器
    trainer = VAETrainer(
        model=model,
        train_loader=train_loader,
        val_loader=val_loader,
        device=device,
        config=config,
        experiment_name=exp_name
    )

    # 恢复训练
    if args.resume is not None:
        trainer.load(args.resume)

    # 开始训练
    trainer.train()


if __name__ == '__main__':
    main()

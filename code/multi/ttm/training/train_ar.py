"""
AR 模型（Qwen3）微调脚本
使用 LoRA 进行参数高效微调
"""
import os
import argparse
import yaml
import json
import torch
import torch.optim as optim
from torch.utils.data import DataLoader
from tqdm import tqdm
from datetime import datetime

import sys
sys.path.append('..')
from models.ar import ARQwen3
from data.dataset import ARTextTokenDataset
from utils.logger import Logger


class ARFineTuner:
    """AR Qwen3 微调器"""

    def __init__(
        self,
        model: ARQwen3,
        train_dataset,
        val_dataset,
        config: dict,
        device: torch.device,
        experiment_name: str = None,
        log_dir: str = "logs",
        checkpoint_dir: str = "checkpoints"
    ):
        self.model = model
        self.train_dataset = train_dataset
        self.val_dataset = val_dataset
        self.config = config
        self.device = device
        self.experiment_name = experiment_name or \
            f"ar_finetune_{datetime.now().strftime('%Y%m%d_%H%M%S')}"

        # 创建目录
        self.log_dir = os.path.join(log_dir, self.experiment_name)
        self.checkpoint_dir = os.path.join(checkpoint_dir, self.experiment_name)
        os.makedirs(self.log_dir, exist_ok=True)
        os.makedirs(self.checkpoint_dir, exist_ok=True)

        # 日志
        self.logger = Logger(self.log_dir)

        # 训练参数
        self.learning_rate = config['training']['learning_rate']
        self.batch_size = config['training']['batch_size']
        self.max_steps = config['training']['max_steps']
        self.warmup_steps = config['training']['warmup_steps']

        # 只有 LoRA 参数和新增的词表嵌入需要训练
        trainable_params = [p for p in model.parameters() if p.requires_grad]
        self.optimizer = optim.AdamW(
            trainable_params,
            lr=self.learning_rate,
            weight_decay=config['training'].get('weight_decay', 0.01)
        )

        # 学习率调度：线性热身 + 余弦衰减
        self.scheduler = self._get_scheduler()

        # 梯度累积
        self.gradient_accumulation_steps = config['training'].get(
            'gradient_accumulation_steps', 1
        )

        # 混合精度
        self.scaler = torch.cuda.amp.GradScaler()
        self.use_amp = config['training'].get('use_amp', True)

        # 训练状态
        self.global_step = 0
        self.epoch = 0

        # 打印训练信息
        total_trainable = sum(p.numel() for p in trainable_params)
        self.logger.info(f"Total trainable parameters: {total_trainable:,}")
        self.logger.info(f"Learning rate: {self.learning_rate}")
        self.logger.info(f"Batch size: {self.batch_size}")
        self.logger.info(f"Max steps: {self.max_steps}")

    def _get_scheduler(self):
        """学习率调度器"""
        warmup_steps = self.warmup_steps
        total_steps = self.max_steps

        def lr_lambda(step):
            if step < warmup_steps:
                return float(step) / float(max(1, warmup_steps))
            progress = float(step - warmup_steps) / \
                float(max(1, total_steps - warmup_steps))
            return 0.5 * (1.0 + np.cos(np.pi * progress))

        return optim.lr_scheduler.LambdaLR(self.optimizer, lr_lambda)

    def train_epoch(self, train_loader):
        """训练一个 epoch"""
        self.model.train()
        total_loss = 0.0
        num_batches = 0

        pbar = tqdm(train_loader, desc=f"Epoch {self.epoch+1}")
        for batch_idx, batch in enumerate(pbar):
            texts = [item['text'] for item in batch]
            semantic_tokens = [item['semantic_tokens'] for item in batch]

            # 前向计算损失
            with torch.cuda.amp.autocast(enabled=self.use_amp):
                loss = self.model(texts, semantic_tokens)
                loss = loss / self.gradient_accumulation_steps

            # 反向传播
            if self.use_amp:
                self.scaler.scale(loss).backward()
            else:
                loss.backward()

            # 梯度累积
            if (batch_idx + 1) % self.gradient_accumulation_steps == 0:
                if self.use_amp:
                    self.scaler.step(self.optimizer)
                    self.scaler.update()
                else:
                    self.optimizer.step()
                self.scheduler.step()
                self.optimizer.zero_grad()
                self.global_step += 1

            # 统计
            total_loss += loss.item() * self.gradient_accumulation_steps
            num_batches += 1

            # 日志
            avg_loss = total_loss / num_batches
            lr = self.scheduler.get_last_lr()[0]
            pbar.set_postfix({
                'loss': f'{avg_loss:.4f}',
                'lr': f'{lr:.6f}'
            })

            if self.global_step % 100 == 0:
                self.logger.log_scalar('train/loss', avg_loss, self.global_step)
                self.logger.log_scalar('train/lr', lr, self.global_step)

        avg_loss = total_loss / num_batches
        self.logger.info(f"Epoch {self.epoch+1} - Train loss: {avg_loss:.4f}")
        self.logger.log_scalar('train/epoch_loss', avg_loss, self.epoch)

        self.epoch += 1
        return avg_loss

    @torch.no_grad()
    def validate(self, val_loader):
        """验证"""
        self.model.eval()
        total_loss = 0.0
        num_batches = 0

        for batch in tqdm(val_loader, desc="Validation"):
            texts = [item['text'] for item in batch]
            semantic_tokens = [item['semantic_tokens'] for item in batch]

            loss = self.model(texts, semantic_tokens)
            total_loss += loss.item()
            num_batches += 1

        avg_loss = total_loss / num_batches
        self.logger.info(f"Validation loss: {avg_loss:.4f}")
        self.logger.log_scalar('val/loss', avg_loss, self.epoch)

        return avg_loss

    def train(
        self,
        epochs: int = None,
        batch_size: int = None,
        save_every: int = None,
        val_every: int = None
    ):
        """完整训练循环"""
        if epochs is None:
            epochs = self.config['training'].get('epochs', 5)
        if batch_size is None:
            batch_size = self.config['training']['batch_size']
        if save_every is None:
            save_every = self.config['training'].get('save_every', 1)
        if val_every is None:
            val_every = self.config['training'].get('val_every', 1)

        # 创建 DataLoader
        train_loader = DataLoader(
            self.train_dataset,
            batch_size=batch_size,
            shuffle=True,
            num_workers=config['training'].get('num_workers', 2),
            collate_fn=lambda x: x  # 自定义 collate
        )

        if self.val_dataset is not None:
            val_loader = DataLoader(
                self.val_dataset,
                batch_size=batch_size,
                shuffle=False,
                num_workers=config['training'].get('num_workers', 2),
                collate_fn=lambda x: x
            )
        else:
            val_loader = None

        best_val_loss = float('inf')

        for epoch in range(epochs):
            self.train_epoch(train_loader)

            # 验证
            if val_loader is not None and (epoch + 1) % val_every == 0:
                val_loss = self.validate(val_loader)
                if val_loss < best_val_loss:
                    best_val_loss = val_loss
                    self.save('ar_best')
                    self.logger.info(f"New best model saved with val loss {best_val_loss:.4f}")

            # 保存
            if (epoch + 1) % save_every == 0:
                self.save(f'ar_epoch_{epoch+1}')

        # 保存最终模型
        self.save('ar_final')
        self.logger.info(f"Training completed! Best validation loss: {best_val_loss:.4f}")

    def save(self, name: str):
        """保存检查点"""
        self.model.save(os.path.join(self.checkpoint_dir, f"{name}.pt"))
        self.logger.info(f"Saved checkpoint to {self.checkpoint_dir}/{name}.pt")


def main():
    parser = argparse.ArgumentParser(description="AR Qwen3 Fine-tuning")
    parser.add_argument('--config', type=str, default='../configs/ar_model.yaml',
                      help='配置文件路径')
    parser.add_argument('--train-metadata', type=str, required=True,
                      help='训练元数据 json 文件路径')
    parser.add_argument('--val-metadata', type=str, default=None,
                      help='验证元数据 json 文件路径')
    parser.add_argument('--experiment-name', type=str, default=None,
                      help='实验名称')
    parser.add_argument('--device', type=str, default='cuda',
                      help='计算设备')
    parser.add_argument('--load-in-4bit', action='store_true',
                      help='4bit 量化加载模型')
    parser.add_argument('--load-in-8bit', action='store_true',
                      help='8bit 量化加载模型')
    args = parser.parse_args()

    # 加载配置
    with open(args.config, 'r', encoding='utf-8') as f:
        config = yaml.safe_load(f)

    device = torch.device(args.device if torch.cuda.is_available() else 'cpu')
    print(f"Using device: {device}")

    # 加载数据集
    print(f"Loading training metadata from {args.train_metadata}")
    with open(args.train_metadata, 'r', encoding='utf-8') as f:
        train_metadata = json.load(f)

    max_text_length = config['model'].get('max_text_length', 512)
    max_token_length = config['model'].get('max_token_length', 1024)
    train_dataset = ARTextTokenDataset(
        args.train_metadata,
        max_text_length=max_text_length,
        max_token_length=max_token_length
    )
    print(f"Train dataset size: {len(train_dataset)}")

    if args.val_metadata is not None:
        val_dataset = ARTextTokenDataset(
            args.val_metadata,
            max_text_length=max_text_length,
            max_token_length=max_token_length
        )
        print(f"Val dataset size: {len(val_dataset)}")
    else:
        val_dataset = None

    # 创建模型
    model_config = config['model']
    lora_config = model_config.get('lora', {})
    model = ARQwen3(
        base_model_name=model_config.get('base_model_name', 'Qwen/Qwen3-7B'),
        num_semantic_tokens=model_config.get('num_semantic_tokens', 1024),
        max_sequence_length=model_config.get('max_sequence_length', 2048),
        lora_enable=lora_config.get('enable', True),
        lora_rank=lora_config.get('rank', 32),
        lora_alpha=lora_config.get('alpha', 64),
        lora_dropout=lora_config.get('dropout', 0.05),
        lora_target_modules=lora_config.get('target_modules', ["q_proj", "v_proj"]),
        device=device,
        load_in_4bit=args.load_in_4bit,
        load_in_8bit=args.load_in_8bit
    ).to(device)

    # 创建微调器
    trainer = ARFineTuner(
        model=model,
        train_dataset=train_dataset,
        val_dataset=val_dataset,
        config=config,
        device=device,
        experiment_name=args.experiment_name,
        log_dir='logs',
        checkpoint_dir='checkpoints'
    )

    # 开始训练
    trainer.train()


if __name__ == '__main__':
    main()

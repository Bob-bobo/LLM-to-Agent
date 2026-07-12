"""
基础 Trainer 类，包含日志、检查点保存等通用功能
"""
import os
import torch
import logging
from typing import Optional, Dict, Any
from datetime import datetime
from torch.utils.tensorboard import SummaryWriter


class BaseTrainer:
    """基础训练器"""

    def __init__(
        self,
        model: torch.nn.Module,
        device: torch.device,
        config: Dict[str, Any],
        experiment_name: Optional[str] = None,
        log_dir: str = "logs",
        checkpoint_dir: str = "checkpoints"
    ):
        self.model = model
        self.device = device
        self.config = config
        self.experiment_name = experiment_name or \
            f"{datetime.now().strftime('%Y%m%d_%H%M%S')}"

        # 创建目录
        self.log_dir = os.path.join(log_dir, self.experiment_name)
        self.checkpoint_dir = os.path.join(checkpoint_dir, self.experiment_name)
        os.makedirs(self.log_dir, exist_ok=True)
        os.makedirs(self.checkpoint_dir, exist_ok=True)

        # TensorBoard
        self.writer = SummaryWriter(self.log_dir)

        # 日志设置
        self.logger = self._setup_logger()

        # 训练状态
        self.global_step = 0
        self.epoch = 0

        self.logger.info(f"Experiment: {self.experiment_name}")
        self.logger.info(f"Log directory: {self.log_dir}")
        self.logger.info(f"Checkpoint directory: {self.checkpoint_dir}")

    def _setup_logger(self) -> logging.Logger:
        """设置日志"""
        logger = logging.getLogger(self.experiment_name)
        logger.setLevel(logging.INFO)

        # 避免重复添加 handler
        if not logger.handlers:
            formatter = logging.Formatter(
                '%(asctime)s - %(name)s - %(levelname)s - %(message)s'
            )
            # 文件 handler
            file_handler = logging.FileHandler(
                os.path.join(self.log_dir, 'train.log'), encoding='utf-8'
            )
            file_handler.setFormatter(formatter)
            logger.addHandler(file_handler)
            # 控制台 handler
            console_handler = logging.StreamHandler()
            console_handler.setFormatter(formatter)
            logger.addHandler(console_handler)

        return logger

    def save_checkpoint(
        self,
        state_dict: Dict[str, Any],
        filename: str
    ) -> str:
        """保存检查点"""
        path = os.path.join(self.checkpoint_dir, filename)
        torch.save(state_dict, path)
        self.logger.info(f"Saved checkpoint to {path}")
        return path

    def load_checkpoint(
        self,
        checkpoint_path: str
    ) -> Dict[str, Any]:
        """加载检查点"""
        self.logger.info(f"Loading checkpoint from {checkpoint_path}")
        checkpoint = torch.load(checkpoint_path, map_location=self.device)
        return checkpoint

    def log_scalar(
        self,
        tag: str,
        value: float,
        step: Optional[int] = None
    ):
        """记录标量到 TensorBoard"""
        if step is None:
            step = self.global_step
        self.writer.add_scalar(tag, value, step)

    def info(self, message: str):
        """输出信息日志"""
        self.logger.info(message)

    def finalize(self):
        """结束训练，关闭 writer"""
        self.writer.close()

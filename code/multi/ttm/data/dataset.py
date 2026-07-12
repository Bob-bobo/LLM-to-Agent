"""
PyTorch Dataset 实现，用于 VAE、DiT、AR 训练
"""
import os
import json
import torch
import numpy as np
from torch.utils.data import Dataset, DataLoader
from typing import Optional, Tuple, List, Dict, Any
from .preprocessing import AudioPreprocessor, PreprocessingConfig, SpecAugment


class MelDataset(Dataset):
    """
    用于 VAE/DiT 预训练的 mel-spectrogram 数据集
    加载预处理好的 .npy 文件
    """

    def __init__(
        self,
        data_dir: str,
        transform: Optional = None,
        augment: bool = False,
        spec_augment: Optional[SpecAugment] = None
    ):
        """
        参数:
            data_dir: 预处理好的 .npy 文件目录
            transform: 额外变换
            augment: 是否使用数据增强
            spec_augment: SpecAugment 实例，如果 None 则使用默认参数
        """
        self.data_dir = data_dir
        self.file_list = [f for f in os.listdir(data_dir)
                          if f.endswith('.npy')]
        self.transform = transform
        self.augment = augment
        if augment and spec_augment is None:
            self.spec_augment = SpecAugment()
        else:
            self.spec_augment = spec_augment

    def __len__(self) -> int:
        return len(self.file_list)

    def __getitem__(self, idx: int) -> torch.Tensor:
        file_path = os.path.join(self.data_dir, self.file_list[idx])
        # shape: [num_segments, 1, n_mels, time_frames]
        mel = np.load(file_path)

        # 如果文件包含多个片段，随机选一个
        if mel.shape[0] > 1:
            segment_idx = np.random.randint(0, mel.shape[0])
            mel = mel[segment_idx:segment_idx + 1]
        else:
            mel = mel[0:1]  # [1, n_mels, time_frames]

        mel_tensor = torch.tensor(mel, dtype=torch.float32)

        if self.augment and self.spec_augment is not None:
            mel_tensor = self.spec_augment(mel_tensor)

        if self.transform is not None:
            mel_tensor = self.transform(mel_tensor)

        # 返回 [1, n_mels, time_frames] 注意：这里时间是最后一维，适配卷积
        return mel_tensor


class TextMelPairedDataset(Dataset):
    """
    用于 AR/DiT/ 微调的文本-音乐配对数据集
    每个样本包含: 文本描述 + 预处理好的 mel-spectrogram
    """

    def __init__(
        self,
        metadata_path: str,
        data_dir: str,
        preprocessor: Optional[AudioPreprocessor] = None,
        transform = None,
        augment: bool = False
    ):
        """
        参数:
            metadata_path: 元数据 json 文件路径
                格式: [{"text": "描述", "audio_file": "xxx.npy"}, ...]
            data_dir: mel 文件目录
            preprocessor: 如果 mel 还没预处理，可以在这里处理
            transform: 额外变换
            augment: 是否数据增强
        """
        with open(metadata_path, 'r', encoding='utf-8') as f:
            self.metadata = json.load(f)
        self.data_dir = data_dir
        self.preprocessor = preprocessor
        self.transform = transform
        self.augment = augment
        if augment:
            self.spec_augment = SpecAugment()
        else:
            self.spec_augment = None

    def __len__(self) -> int:
        return len(self.metadata)

    def __getitem__(self, idx: int) -> Dict[str, Any]:
        item = self.metadata[idx]
        text = item['text']
        audio_file = item['audio_file']

        # 加载 mel
        if audio_file.endswith('.npy'):
            # 已经预处理好
            mel_path = os.path.join(self.data_dir, audio_file)
            mel = np.load(mel_path)
            if mel.shape[0] > 1:
                # 多个片段，选第一个
                mel = mel[0:1]
            mel_tensor = torch.tensor(mel, dtype=torch.float32)
        else:
            # 需要实时预处理
            assert self.preprocessor is not None
            audio_path = os.path.join(self.data_dir, audio_file)
            mel_tensor = self.preprocessor.process(
                audio_path, segment=False
            )  # [1, n_mels, time_frames]

        if self.augment and self.spec_augment is not None:
            mel_tensor = self.spec_augment(mel_tensor)

        if self.transform is not None:
            mel_tensor = self.transform(mel_tensor)

        return {
            'text': text,
            'mel': mel_tensor,
        }


class ARTextTokenDataset(Dataset):
    """
    用于 AR 模型微调的数据集：文本描述 → 语义 token 序列
    """

    def __init__(
        self,
        metadata_path: str,
        max_text_length: int = 512,
        max_token_length: int = 1024
    ):
        """
        参数:
            metadata_path: 元数据 json 文件路径
                格式: [{"text": "描述", "semantic_tokens": [1, 2, 3, ...]}, ...]
            max_text_length: 文本最大长度
            max_token_length: 语义 token 最大长度
        """
        with open(metadata_path, 'r', encoding='utf-8') as f:
            self.metadata = json.load(f)
        self.max_text_length = max_text_length
        self.max_token_length = max_token_length

    def __len__(self) -> int:
        return len(self.metadata)

    def __getitem__(self, idx: int) -> Dict[str, torch.Tensor]:
        item = self.metadata[idx]
        text = item['text']
        tokens = item['semantic_tokens']

        # 截断到最大长度
        tokens = tokens[:self.max_token_length]

        return {
            'text': text,
            'semantic_tokens': torch.tensor(tokens, dtype=torch.long),
        }


def create_dataloader(
    dataset: Dataset,
    batch_size: int,
    shuffle: bool = True,
    num_workers: int = 4,
    pin_memory: bool = True,
    drop_last: bool = True
) -> DataLoader:
    """创建 DataLoader 的便捷函数"""
    return DataLoader(
        dataset,
        batch_size=batch_size,
        shuffle=shuffle,
        num_workers=num_workers,
        pin_memory=pin_memory,
        drop_last=drop_last
    )


def get_default_preprocessing_config() -> PreprocessingConfig:
    """获取默认预处理配置"""
    return PreprocessingConfig(
        sample_rate=44100,
        n_fft=2048,
        hop_length=512,
        n_mels=128,
        segment_duration=10.0,
        normalize=True
    )

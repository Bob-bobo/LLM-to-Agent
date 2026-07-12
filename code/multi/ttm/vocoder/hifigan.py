"""
HiFi-GAN 声码器包装器
调用预训练的 HiFi-GAN 模型从 mel-spectrogram 生成高质量音频
"""
import torch
import torch.nn as nn
import numpy as np
from typing import Optional, Union


class HiFiGANVocoder:
    """
    HiFi-GAN 声码器包装器
    需要预训练的 HiFi-GAN 权重
    """

    def __init__(
        self,
        model_path: str,
        config_path: Optional[str] = None,
        device: str = 'cuda'
    ):
        """
        参数:
            model_path: 预训练模型权重路径
            config_path: 配置文件路径（可选，如果模型包含配置则不需要）
            device: 计算设备
        """
        self.device = device
        self.model = self._load_model(model_path, config_path)
        self.model.eval()
        self.model.to(device)

    def _load_model(self, model_path: str, config_path: Optional[str]) -> nn.Module:
        """
        加载 HiFi-GAN 模型
        这里预留接口，实际使用需要根据具体的 HiFi-GAN 实现来加载

        参数:
            model_path: 模型路径
            config_path: 配置路径

        返回:
            加载好的模型
        """
        # TODO: 实现具体的 HiFi-GAN 加载
        # 这里需要根据具体使用的 HiFi-GAN 版本（如 https://github.com/jik876/hifi-gan）来适配
        raise NotImplementedError(
            "HiFi-GAN 加载需要适配具体的模型实现，请提供预训练模型和加载代码。"
            "当前只提供 Griffin-Lim 作为默认实现。"
        )

    @torch.no_grad()
    def generate(
        self,
        mel: Union[np.ndarray, torch.Tensor],
        denormalize: bool = True
    ) -> np.ndarray:
        """
        从 mel-spectrogram 生成波形

        参数:
            mel: [n_mels, time] mel-spectrogram
            denormalize: 是否反归一化

        返回:
            waveform: 生成的音频波形
        """
        if isinstance(mel, np.ndarray):
            mel = torch.tensor(mel, dtype=torch.float32)
        mel = mel.unsqueeze(0).to(self.device)  # [1, n_mels, time]

        # 反归一化
        if denormalize:
            # 和 Griffin-Lim 相同的反归一化
            mel = (mel + 1) / 2
            min_log = -12
            max_log = 8
            mel = mel * (max_log - min_log) + min_log
            mel = torch.exp(mel)

        # 生成波形
        waveform = self.model(mel)
        waveform = waveform.squeeze().cpu().numpy()

        return waveform

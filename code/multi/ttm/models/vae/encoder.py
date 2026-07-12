"""
VAE 编码器：卷积下采样将 mel-spectrogram 压缩到低维 latent
"""
import torch
import torch.nn as nn
import torch.nn.functional as F


class DownBlock(nn.Module):
    """下采样残差块"""

    def __init__(
        self,
        in_channels: int,
        out_channels: int,
        kernel_size: int = 3,
        stride: int = 2,
        dropout: float = 0.0
    ):
        super().__init__()
        padding = (kernel_size - 1) // 2
        self.conv1 = nn.Conv2d(
            in_channels, out_channels,
            kernel_size=kernel_size,
            stride=stride,
            padding=padding
        )
        self.norm1 = nn.GroupNorm(32, out_channels) if out_channels >= 32 else nn.GroupNorm(1, out_channels)
        self.activation = nn.SiLU()
        self.conv2 = nn.Conv2d(
            out_channels, out_channels,
            kernel_size=kernel_size,
            stride=1,
            padding=padding
        )
        self.norm2 = nn.GroupNorm(32, out_channels) if out_channels >= 32 else nn.GroupNorm(1, out_channels)
        self.dropout = nn.Dropout(dropout) if dropout > 0 else nn.Identity()

        # 残差连接，如果通道数变化或下采样，需要 1x1 卷积投影
        if in_channels != out_channels or stride != 1:
            self.residual = nn.Conv2d(
                in_channels, out_channels,
                kernel_size=1,
                stride=stride,
                padding=0
            )
        else:
            self.residual = nn.Identity()

    def forward(self, x: torch.Tensor) -> torch.Tensor:
        residual = self.residual(x)
        out = self.conv1(x)
        out = self.norm1(out)
        out = self.activation(out)
        out = self.dropout(out)
        out = self.conv2(out)
        out = self.norm2(out)
        out = self.activation(out)
        out = self.dropout(out)
        return out + residual


class Encoder(nn.Module):
    """
    VAE 编码器：将 mel-spectrogram 编码到 latent 分布参数 (μ, log_σ²)

    输入: mel-spectrogram [batch, 1, n_mels, time_frames]
    输出: mu, log_var 每个形状 [batch, latent_channels, n_mels//compression, time_frames//compression]
    """

    def __init__(
        self,
        in_channels: int = 1,
        base_channels: int = 64,
        latent_channels: int = 32,
        num_downsampling_layers: int = 4,
        dropout: float = 0.0
    ):
        super().__init__()
        self.latent_channels = latent_channels

        # 输入卷积
        layers = [
            nn.Conv2d(in_channels, base_channels, kernel_size=3, stride=1, padding=1),
            nn.GroupNorm(32, base_channels) if base_channels >= 32 else nn.GroupNorm(1, base_channels),
            nn.SiLU()
        ]

        # 逐步下采样
        current_channels = base_channels
        for i in range(num_downsampling_layers):
            next_channels = base_channels * (2 ** (i + 1))
            layers.append(DownBlock(
                current_channels, next_channels,
                kernel_size=3, stride=2,
                dropout=dropout
            ))
            current_channels = next_channels

        # 输出 mu 和 log_var
        self.conv_out = nn.Conv2d(
            current_channels, 2 * latent_channels,
            kernel_size=3, stride=1, padding=1
        )

        self.layers = nn.Sequential(*layers)

    def forward(self, x: torch.Tensor) -> tuple[torch.Tensor, torch.Tensor]:
        """
        参数:
            x: [batch, 1, n_mels, time_frames] 输入 mel-spectrogram

        返回:
            mu: [batch, latent_channels, n_mels//compression, time_frames//compression] 均值
            log_var: [batch, latent_channels, n_mels//compression, time_frames//compression] 对数方差
        """
        out = self.layers(x)
        out = self.conv_out(out)  # [batch, 2*latent_channels, h, w]
        mu, log_var = torch.chunk(out, 2, dim=1)  # 分成两部分
        return mu, log_var

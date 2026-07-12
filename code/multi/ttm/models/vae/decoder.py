"""
VAE 解码器：从 latent 重构 mel-spectrogram
"""
import torch
import torch.nn as nn
import torch.nn.functional as F


class UpBlock(nn.Module):
    """上采样残差块"""

    def __init__(
        self,
        in_channels: int,
        out_channels: int,
        kernel_size: int = 3,
        scale_factor: int = 2,
        dropout: float = 0.0
    ):
        super().__init__()
        padding = (kernel_size - 1) // 2

        # 上采样 + 卷积
        self.upsample = nn.Upsample(scale_factor=scale_factor, mode='bilinear', align_corners=False)
        self.conv1 = nn.Conv2d(
            in_channels, out_channels,
            kernel_size=kernel_size,
            stride=1,
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

        # 残差连接
        if in_channels != out_channels:
            self.residual = nn.Sequential(
                self.upsample,
                nn.Conv2d(in_channels, out_channels, kernel_size=1, stride=1, padding=0)
            )
        else:
            self.residual = nn.Sequential(
                self.upsample,
                nn.Identity()
            )

    def forward(self, x: torch.Tensor) -> torch.Tensor:
        residual = self.residual(x)
        out = self.upsample(x)
        out = self.conv1(out)
        out = self.norm1(out)
        out = self.activation(out)
        out = self.dropout(out)
        out = self.conv2(out)
        out = self.norm2(out)
        out = self.activation(out)
        out = self.dropout(out)
        return out + residual


class Decoder(nn.Module):
    """
    VAE 解码器：从 latent 重构 mel-spectrogram

    输入: latent z [batch, latent_channels, n_mels//compression, time_frames//compression]
    输出: 重构 mel-spectrogram [batch, 1, n_mels, time_frames]
    """

    def __init__(
        self,
        latent_channels: int = 32,
        base_channels: int = 64,
        out_channels: int = 1,
        num_upsampling_layers: int = 4,
        dropout: float = 0.0
    ):
        super().__init__()

        # 输入卷积
        current_channels = base_channels * (2 ** num_upsampling_layers)
        layers = [
            nn.Conv2d(latent_channels, current_channels, kernel_size=3, stride=1, padding=1),
            nn.GroupNorm(32, current_channels) if current_channels >= 32 else nn.GroupNorm(1, current_channels),
            nn.SiLU()
        ]

        # 逐步上采样
        for i in range(num_upsampling_layers):
            next_channels = base_channels * (2 ** (num_upsampling_layers - i - 1))
            layers.append(UpBlock(
                current_channels, next_channels,
                kernel_size=3, scale_factor=2,
                dropout=dropout
            ))
            current_channels = next_channels

        # 输出卷积
        layers.append(
            nn.Conv2d(current_channels, out_channels, kernel_size=3, stride=1, padding=1)
        )

        self.layers = nn.Sequential(*layers)

    def forward(self, z: torch.Tensor) -> torch.Tensor:
        """
        参数:
            z: [batch, latent_channels, h, w] 输入 latent

        返回:
            x_recon: [batch, 1, n_mels, time_frames] 重构 mel-spectrogram
        """
        return self.layers(z)

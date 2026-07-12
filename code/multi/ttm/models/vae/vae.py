"""
VAE 变分自编码器主类
包含重参数化技巧和损失计算
"""
import torch
import torch.nn as nn
import torch.nn.functional as F
from .encoder import Encoder
from .decoder import Decoder


class VAE(nn.Module):
    """
    变分自编码器用于 mel-spectrogram 压缩

    关键：重参数化技巧 (Reparameterization Trick)
      采样 z ~ q(z|x) = N(μ, σ²) 本身是不可微的，
      所以我们改用：
        ε ~ N(0, I)
        z = μ + σ ⊙ ε
      这样梯度可以通过 μ 和 σ 反向传播。
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
        self.encoder = Encoder(
            in_channels=in_channels,
            base_channels=base_channels,
            latent_channels=latent_channels,
            num_downsampling_layers=num_downsampling_layers,
            dropout=dropout
        )
        self.decoder = Decoder(
            latent_channels=latent_channels,
            base_channels=base_channels,
            out_channels=in_channels,
            num_upsampling_layers=num_downsampling_layers,
            dropout=dropout
        )
        self.latent_channels = latent_channels
        self.compression_factor = 2 ** num_downsampling_layers

    def encode(self, x: torch.Tensor) -> tuple[torch.Tensor, torch.Tensor]:
        """
        编码：输入 mel → 输出 latent 分布参数 (μ, log_σ²)

        参数:
            x: [batch, 1, n_mels, time_frames]

        返回:
            mu: [batch, latent_channels, n_mels//compression, time//compression]
            log_var: [batch, latent_channels, n_mels//compression, time//compression]
        """
        return self.encoder(x)

    def reparameterize(self, mu: torch.Tensor, log_var: torch.Tensor) -> torch.Tensor:
        """
        重参数化技巧：让采样操作可微

        参数:
            mu: 均值
            log_var: 对数方差 log(σ²)

        返回:
            z: 采样得到的 latent
                z = μ + σ * ε,  ε ~ N(0, 1)
        """
        std = torch.exp(0.5 * log_var)  # σ = exp(log_var / 2)
        eps = torch.randn_like(std)      # ε ~ N(0, 1)
        z = mu + std * eps              # 重参数化
        return z

    def decode(self, z: torch.Tensor) -> torch.Tensor:
        """
        解码：从 latent z 重构 mel-spectrogram

        参数:
            z: [batch, latent_channels, h, w]

        返回:
            x_recon: [batch, 1, n_mels, time_frames]
        """
        return self.decoder(z)

    def forward(self, x: torch.Tensor) -> tuple[torch.Tensor, torch.Tensor, torch.Tensor]:
        """
        前向传播：编码 → 重参数化 → 解码

        返回:
            x_recon: 重构 mel-spectrogram
            mu: 编码器输出均值
            log_var: 编码器输出对数方差
        """
        mu, log_var = self.encode(x)
        z = self.reparameterize(mu, log_var)
        x_recon = self.decode(z)
        return x_recon, mu, log_var

    def sample(
        self,
        num_samples: int,
        shape: tuple[int, int],
        device: str = 'cuda'
    ) -> torch.Tensor:
        """
        从先验分布 p(z) = N(0,I) 采样并生成新的 mel-spectrogram

        参数:
            num_samples: 采样数量
            shape: (h, w) latent 空间尺寸
            device: 计算设备

        返回:
            samples: [num_samples, 1, n_mels, time_frames]
        """
        h, w = shape
        z = torch.randn(num_samples, self.latent_channels, h, w).to(device)
        with torch.no_grad():
            samples = self.decode(z)
        return samples

    def reconstruct(self, x: torch.Tensor) -> torch.Tensor:
        """重构输入 x"""
        with torch.no_grad():
            mu, log_var = self.encode(x)
            z = self.reparameterize(mu, log_var)
            x_recon = self.decode(z)
        return x_recon


def vae_loss(
    x_recon: torch.Tensor,
    x: torch.Tensor,
    mu: torch.Tensor,
    log_var: torch.Tensor,
    kl_weight: float = 0.0001
) -> tuple[torch.Tensor, torch.Tensor, torch.Tensor]:
    """
    VAE 损失函数 = 重构损失 + KL 散度

    本质上是在最大化证据下界 ELBO：
        ELBO = E_q[log p(x|z)] - KL(q(z|x) || p(z))

    我们最小化 -ELBO = 负对数似然 (重构损失) + KL 散度

    参数:
        x_recon: 重构 mel-spectrogram [batch, 1, n_mels, time]
        x: 原始 mel-spectrogram [batch, 1, n_mels, time]
        mu: [batch, latent_channels, h, w]
        log_var: [batch, latent_channels, h, w]
        kl_weight: KL 散度的权重

    返回:
        total_loss: 总损失
        recon_loss: 重构损失（L1）
        kl_loss: KL 散度
    """
    # 1. 重构损失：使用 L1 对 mel-spectrogram 效果更好
    # 因为我们已经归一化到 [-1, 1]，不需要做 sigmoid/BCE
    recon_loss = F.l1_loss(x_recon, x, reduction='sum')

    # 2. KL 散度 KL(q(z|x) || p(z))
    # 其中 q = N(μ, σ²), p = N(0, I)
    # 解析解: KL = -0.5 * Σ (1 + log(σ²) - μ² - σ²)
    #         = -0.5 * Σ (1 + log_var - mu² - exp(log_var))
    kl_loss = -0.5 * torch.sum(1 + log_var - mu.pow(2) - log_var.exp())

    # 总损失
    total_loss = recon_loss + kl_weight * kl_loss

    # 按 batch size 平均
    batch_size = x.shape[0]
    total_loss = total_loss / batch_size
    recon_loss = recon_loss / batch_size
    kl_loss = kl_loss / batch_size

    return total_loss, recon_loss, kl_loss

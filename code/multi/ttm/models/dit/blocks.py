"""
DiT Block 定义：带有 adaptive layer norm zero (adaLN-Zero) 条件的 Transformer block
基于官方 DiT 改编为 1D
"""
import torch
import torch.nn as nn
import torch.nn.functional as F
from timm.models.vision_transformer import Attention, Mlp


def modulate(x: torch.Tensor, shift: torch.Tensor, scale: torch.Tensor):
    """
    adaLN 调制：x * (1 + scale) + shift
    参数:
        x: [B, T, D]
        shift: [B, D]
        scale: [B, D]
    """
    return x * (1 + scale.unsqueeze(1)) + shift.unsqueeze(1)


class DiTBlock(nn.Module):
    """
    带有 adaLN-Zero 条件的 DiT Block。

    DiT Block 结构：
    - LayerNorm
    - Attention
    - adaLN 调制: shift + scale + gate
    -残差连接
    - LayerNorm
    - MLP
    - adaLN 调制
    - 残差连接
    """

    def __init__(
        self,
        hidden_size: int,
        num_heads: int,
        mlp_ratio: float = 4.0,
        **block_kwargs
    ):
        super().__init__()
        self.hidden_size = hidden_size
        self.num_heads = num_heads

        self.norm1 = nn.LayerNorm(hidden_size, elementwise_affine=False, eps=1e-6)
        self.attn = Attention(
            hidden_size,
            num_heads=num_heads,
            qkv_bias=True,
            **block_kwargs
        )

        self.norm2 = nn.LayerNorm(hidden_size, elementwise_affine=False, eps=1e-6)
        mlp_hidden_dim = int(hidden_size * mlp_ratio)
        approx_gelu = lambda: nn.GELU(approximate="tanh")
        self.mlp = Mlp(
            in_features=hidden_size,
            hidden_features=mlp_hidden_dim,
            act_layer=approx_gelu,
            drop=0
        )

        # adaLN-Zero：6 个调制参数来自条件 c，输出全零初始化
        self.adaLN_modulation = nn.Sequential(
            nn.SiLU(),
            nn.Linear(hidden_size, 6 * hidden_size, bias=True)
        )

    def forward(self, x: torch.Tensor, c: torch.Tensor):
        """
        参数:
            x: [B, T, D] 输入序列，B=batch, T=num_patches, D=hidden_size
            c: [B, D] 条件 embedding (timestep + condition)

        返回:
            x: [B, T, D] 输出
        """
        # 将 6 个参数切分
        shift_msa, scale_msa, gate_msa, shift_mlp, scale_mlp, gate_mlp = \
            self.adaLN_modulation(c).chunk(6, dim=1)

        # Attention 分支
        x = x + gate_msa.unsqueeze(1) * self.attn(modulate(self.norm1(x), shift_msa, scale_msa))

        # MLP 分支
        x = x + gate_mlp.unsqueeze(1) * self.mlp(modulate(self.norm2(x), shift_mlp, scale_mlp))

        return x


class FinalLayer(nn.Module):
    """
    DiT 最后的输出层：将 Transformer 输出 unpatchify 到最终预测
    """
    def __init__(
        self,
        hidden_size: int,
        patch_size: int,
        out_channels: int
    ):
        super().__init__()
        self.norm_final = nn.LayerNorm(hidden_size, elementwise_affine=False, eps=1e-6)
        self.linear = nn.Linear(
            hidden_size,
            patch_size * out_channels,
            bias=True
        )
        # 还是需要 adaLN 调制
        self.adaLN_modulation = nn.Sequential(
            nn.SiLU(),
            nn.Linear(hidden_size, 2 * hidden_size, bias=True)
        )

    def forward(self, x: torch.Tensor, c: torch.Tensor):
        """
        参数:
            x: [B, T, D]
            c: [B, D]
        返回:
            [B, T, patch_size * out_channels]
        """
        shift, scale = self.adaLN_modulation(c).chunk(2, dim=1)
        x = modulate(self.norm_final(x), shift, scale)
        x = self.linear(x)
        return x

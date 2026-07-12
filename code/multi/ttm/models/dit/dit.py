"""
DiT (Diffusion Transformer) 主类，适配 1D 音乐 latent
基于官方 DiT 改编，输入是 1D 时间序列，输出预测噪声
"""
import torch
import torch.nn as nn
import numpy as np
from .blocks import DiTBlock, FinalLayer, modulate
from .embeddings import TimestepEmbedder, SemanticEmbedder, get_1d_sincos_pos_embed


class DiT(nn.Module):
    """
    1D Diffusion Transformer 用于音乐 latent 生成

    输入是 VAE 编码后的 latent，形状: [B, C, T]
      - B: batch size
      - C: latent channels = 32 (和 VAE latent_channels 对应)
      - T: 时间长度 = time_frames // compression_ratio
    """

    def __init__(
        self,
        input_size: int = 64,  # 输入 latent 时间长度
        patch_size: int = 2,
        in_channels: int = 32,
        hidden_size: int = 768,
        depth: int = 12,
        num_heads: int = 12,
        mlp_ratio: float = 4.0,
        num_semantic_tokens: int = 1024,
        cond_dropout_prob: float = 0.1,
        learn_sigma: bool = True,
    ):
        super().__init__()
        self.learn_sigma = learn_sigma
        self.in_channels = in_channels
        self.out_channels = in_channels * 2 if learn_sigma else in_channels
        self.patch_size = patch_size
        self.num_heads = num_heads
        self.hidden_size = hidden_size

        # 1D Patch embedding: 将输入分成 patches
        # 输入形状 [B, C, T] → 输出 [B, num_patches, hidden_size]
        self.x_embedder = nn.Conv1d(
            in_channels,
            hidden_size,
            kernel_size=patch_size,
            stride=patch_size,
            bias=True
        )

        # 时间步嵌入
        self.t_embedder = TimestepEmbedder(hidden_size)

        # 语义 tokens 条件嵌入
        self.y_embedder = SemanticEmbedder(
            num_semantic_tokens,
            hidden_size,
            cond_dropout_prob
        )

        # 计算 patch 数量
        num_patches = input_size // patch_size
        self.num_patches = num_patches

        # 固定正弦余弦位置嵌入
        self.pos_embed = nn.Parameter(
            torch.zeros(1, num_patches, hidden_size),
            requires_grad=False
        )

        # DiT blocks
        self.blocks = nn.ModuleList([
            DiTBlock(hidden_size, num_heads, mlp_ratio=mlp_ratio)
            for _ in range(depth)
        ])

        # 最后的输出层
        self.final_layer = FinalLayer(
            hidden_size,
            patch_size,
            self.out_channels
        )

        # 初始化权重
        self.initialize_weights()

    def initialize_weights(self):
        """初始化权重"""
        # 初始化 transformer 层
        def _basic_init(module):
            if isinstance(module, nn.Linear):
                torch.nn.init.xavier_uniform_(module.weight)
                if module.bias is not None:
                    nn.init.constant_(module.bias, 0)
        self.apply(_basic_init)

        # 用正弦余弦初始化位置嵌入（已经是固定的，不需要梯度）
        pos_embed = get_1d_sincos_pos_embed(
            self.pos_embed.shape[-1],
            self.num_patches
        )
        self.pos_embed.data.copy_(torch.from_numpy(pos_embed).float().unsqueeze(0))

        # 初始化 patch embedding 卷积，类似 nn.Linear
        w = self.x_embedder.weight.data
        nn.init.xavier_uniform_(w.view([w.shape[0], -1]))
        nn.init.constant_(self.x_embedder.bias, 0)

        # 初始化语义 token 嵌入表
        nn.init.normal_(self.y_embedder.embedding_table.weight, std=0.02)

        # 初始化 timestep 嵌入的 MLP
        nn.init.normal_(self.t_embedder.mlp[0].weight, std=0.02)
        nn.init.normal_(self.t_embedder.mlp[2].weight, std=0.02)

        # Zero-out adaLN modulation layers
        for block in self.blocks:
            nn.init.constant_(block.adaLN_modulation[-1].weight, 0)
            nn.init.constant_(block.adaLN_modulation[-1].bias, 0)

        # Zero-out output layers
        nn.init.constant_(self.final_layer.adaLN_modulation[-1].weight, 0)
        nn.init.constant_(self.final_layer.adaLN_modulation[-1].bias, 0)
        nn.init.constant_(self.final_layer.linear.weight, 0)
        nn.init.constant_(self.final_layer.linear.bias, 0)

    def unpatchify(self, x: torch.Tensor) -> torch.Tensor:
        """
        将 patches 拼接回完整输出

        参数:
            x: [B, num_patches, patch_size * out_channels]

        返回:
            out: [B, out_channels, num_patches * patch_size]
        """
        B = x.shape[0]
        T = self.num_patches * self.patch_size
        out = x.reshape(B, self.num_patches, self.patch_size, self.out_channels)
        out = out.permute(0, 3, 1, 2)  # [B, C, num_patches, patch_size]
        out = out.reshape(B, self.out_channels, T)  # [B, C, T]
        return out

    def forward(
        self,
        x: torch.Tensor,
        t: torch.Tensor,
        y: torch.Tensor
    ) -> torch.Tensor:
        """
        前向传播

        参数:
            x: [B, C, T] 输入 noisy latent，C = in_channels
            t: [B,] 扩散时间步
            y: [B, L] 语义 tokens 条件序列

        返回:
            out: [B, out_channels, T] 预测的噪声（或噪声+方差）
        """
        # 1. patch embedding: [B, C, T] → [B, hidden_size, num_patches] → [B, num_patches, hidden_size]
        x = self.x_embedder(x)  # [B, D, num_patches]
        x = x.transpose(1, 2)    # [B, num_patches, D]

        # 2. 添加位置嵌入
        x = x + self.pos_embed  # [B, num_patches, D]

        # 3. 时间步嵌入
        t = self.t_embedder(t)  # [B, D]

        # 4. 条件语义 tokens 嵌入 → 池化为全局条件向量
        # 把 L 个 tokens 平均池化为单个条件向量
        # 这样和 timestep 可以直接相加
        y_emb = self.y_embedder(y, self.training)  # [B, L, D]
        y_emb = y_emb.mean(dim=1)  # [B, D] 平均池化

        # 5. 组合时间步和条件
        c = t + y_emb  # [B, D]

        # 6. DiT blocks
        for block in self.blocks:
            x = block(x, c)  # [B, num_patches, D]

        # 7. 最终输出层
        x = self.final_layer(x, c)  # [B, num_patches, patch_size * out_channels]

        # 8. unpatchify
        x = self.unpatchify(x)  # [B, out_channels, T]

        return x

    def forward_with_cfg(
        self,
        x: torch.Tensor,
        t: torch.Tensor,
        y: torch.Tensor,
        cfg_scale: float
    ) -> torch.Tensor:
        """
        带 Classifier-Free Guidance 的前向传播
        批处理同时计算条件和无条件，然后组合

        参数:
            x: [2*B, C, T] 前一半是 conditional，后一半是 unconditional
            t: [2*B,] 时间步
            y: [2*B, L] tokens
            cfg_scale: 指导强度

        返回:
            组合后的输出 [B, out_channels, T]
        """
        half = x[:len(x)//2]
        combined = torch.cat([half, half], dim=0)
        model_out = self.forward(combined, t, y)

        # 分离 eps 和 rest（如果 learn_sigma=True）
        eps, rest = model_out[:, :self.in_channels], model_out[:, self.in_channels:]
        cond_eps, uncond_eps = torch.split(eps, len(eps)//2, dim=0)

        # CFG 公式：eps = uncond_eps + cfg_scale * (cond_eps - uncond_eps)
        half_eps = uncond_eps + cfg_scale * (cond_eps - uncond_eps)
        eps = torch.cat([half_eps, half_eps], dim=0) if len(x) > len(half) else half_eps

        return torch.cat([eps, rest], dim=1)


# DiT 不同大小的模型变体
def DiT_S_2(**kwargs):
    return DiT(depth=12, hidden_size=384, patch_size=2, num_heads=6, **kwargs)

def DiT_S_4(**kwargs):
    return DiT(depth=12, hidden_size=384, patch_size=4, num_heads=6, **kwargs)

def DiT_B_2(**kwargs):
    return DiT(depth=12, hidden_size=768, patch_size=2, num_heads=12, **kwargs)

def DiT_B_4(**kwargs):
    return DiT(depth=12, hidden_size=768, patch_size=4, num_heads=12, **kwargs)

def DiT_L_2(**kwargs):
    return DiT(depth=24, hidden_size=1024, patch_size=2, num_heads=16, **kwargs)

def DiT_L_4(**kwargs):
    return DiT(depth=24, hidden_size=1024, patch_size=4, num_heads=16, **kwargs)

def DiT_XL_2(**kwargs):
    return DiT(depth=28, hidden_size=1152, patch_size=2, num_heads=16, **kwargs)

def DiT_XL_4(**kwargs):
    return DiT(depth=28, hidden_size=1152, patch_size=4, num_heads=16, **kwargs)


DiT_models = {
    'DiT-S/2': DiT_S_2,  'DiT-S/4': DiT_S_4,
    'DiT-B/2': DiT_B_2,  'DiT-B/4': DiT_B_4,
    'DiT-L/2': DiT_L_2,  'DiT-L/4': DiT_L_4,
    'DiT-XL/2': DiT_XL_2,  'DiT-XL/4': DiT_XL_4,
}

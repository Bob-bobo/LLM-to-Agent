"""
DiT 嵌入层：Timestep 嵌入和 Condition (语义 tokens) 嵌入
基于 ../dit_transformer/models.py 改编
"""
import torch
import torch.nn as nn
import math
import numpy as np


class TimestepEmbedder(nn.Module):
    """
    将标量时间步嵌入到向量表示
    直接复用官方 DiT 的实现
    """
    def __init__(self, hidden_size: int, frequency_embedding_size: int = 256):
        super().__init__()
        self.mlp = nn.Sequential(
            nn.Linear(frequency_embedding_size, hidden_size, bias=True),
            nn.SiLU(),
            nn.Linear(hidden_size, hidden_size, bias=True),
        )
        self.frequency_embedding_size = frequency_embedding_size

    @staticmethod
    def timestep_embedding(t: torch.Tensor, dim: int, max_period: int = 10000):
        """
        创建正弦时间步嵌入
        :param t: 形状 (N,) 每个 batch 元素的时间步索引
        :param dim: 输出维度
        :param max_period: 控制最小频率
        :return: 形状 (N, D) 位置嵌入
        """
        half = dim // 2
        freqs = torch.exp(
            -math.log(max_period) * torch.arange(start=0, end=half, dtype=torch.float32, device=t.device) / half
        )
        args = t[:, None].float() * freqs[None]
        embedding = torch.cat([torch.cos(args), torch.sin(args)], dim=-1)
        if dim % 2:
            embedding = torch.cat([embedding, torch.zeros_like(embedding[:, :1])], dim=-1)
        return embedding

    def forward(self, t: torch.Tensor) -> torch.Tensor:
        """
        参数:
            t: (N,) 时间步
        返回:
            (N, hidden_size) 时间步嵌入
        """
        t_freq = self.timestep_embedding(t, self.frequency_embedding_size)
        t_emb = self.mlp(t_freq)
        return t_emb


class SemanticEmbedder(nn.Module):
    """
    将离散语义 tokens 嵌入到向量表示
    支持 classifier-free guidance 的 token dropout
    """
    def __init__(
        self,
        num_semantic_tokens: int,
        hidden_size: int,
        dropout_prob: float = 0.1
    ):
        """
        参数:
            num_semantic_tokens: 语义 token 词汇表大小
            hidden_size: 输出嵌入维度
            dropout_prob: classifier-free guidance 的 dropout 概率
        """
        super().__init__()
        use_cfg_embedding = dropout_prob > 0
        # 额外增加一个 token 用于空条件（classifier-free guidance）
        self.embedding_table = nn.Embedding(
            num_semantic_tokens + use_cfg_embedding,
            hidden_size
        )
        self.num_semantic_tokens = num_semantic_tokens
        self.dropout_prob = dropout_prob

    def token_drop(self, tokens: torch.Tensor, force_drop_ids=None):
        """
        随机 dropout 条件 tokens 用于 classifier-free guidance
        """
        if force_drop_ids is None:
            # 对每个样本，有 dropout_prob 概率整个条件都被丢弃
            drop_ids = torch.rand(tokens.shape[0], device=tokens.device) < self.dropout_prob
        else:
            drop_ids = force_drop_ids == 1

        # 将被丢弃的 token 替换为特殊的空 token id
        dropped_tokens = torch.where(
            drop_ids[:, None].repeat(1, tokens.shape[1]),
            self.num_semantic_tokens * torch.ones_like(tokens),
            tokens
        )
        return dropped_tokens

    def forward(
        self,
        tokens: torch.Tensor,
        train: bool,
        force_drop_ids=None
    ) -> torch.Tensor:
        """
        参数:
            tokens: (B, L) 语义 token 序列，B 是 batch size，L 是序列长度
            train: 是否在训练阶段
            force_drop_ids: 强制指定哪些样本要 dropout，用于推理时的 cfg

        返回:
            (B, L, hidden_size) 嵌入向量序列
        """
        use_dropout = self.dropout_prob > 0
        if (train and use_dropout) or (force_drop_ids is not None):
            tokens = self.token_drop(tokens, force_drop_ids)

        # 嵌入每个 token
        embeddings = self.embedding_table(tokens)  # [B, L, hidden_size]
        return embeddings


def get_1d_sincos_pos_embed(embed_dim: int, grid_size: int):
    """
    生成 1D 正弦余弦位置嵌入

    参数:
        embed_dim: 嵌入维度
        grid_size: 长度 (patch 的数量)

    返回:
        pos_embed: (grid_size, embed_dim) 位置嵌入
    """
    grid = np.arange(grid_size, dtype=np.float32)
    pos_embed = get_1d_sincos_pos_embed_from_grid(embed_dim, grid)
    return pos_embed


def get_1d_sincos_pos_embed_from_grid(embed_dim: int, pos):
    """
    1D 正弦余弦位置嵌入

    embed_dim: 每个位置的输出维度
    pos: 位置坐标列表 (M,)
    output: (M, embed_dim)
    """
    assert embed_dim % 2 == 0
    omega = np.arange(embed_dim // 2, dtype=np.float64)
    omega /= embed_dim / 2.
    omega = 1. / 10000**omega  # (D/2,)

    pos = pos.reshape(-1)  # (M,)
    out = np.einsum('m,d->md', pos, omega)  # (M, D/2), outer product

    emb_sin = np.sin(out)  # (M, D/2)
    emb_cos = np.cos(out)  # (M, D/2)

    emb = np.concatenate([emb_sin, emb_cos], axis=1)  # (M, D)
    return emb

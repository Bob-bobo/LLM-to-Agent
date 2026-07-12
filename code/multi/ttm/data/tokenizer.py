"""
文本 tokenization + 语义 token 量化（K-means）
"""
import torch
import numpy as np
from typing import List, Tuple, Optional
from sklearn.cluster import KMeans
from transformers import AutoTokenizer


class TextTokenizer:
    """Qwen3 文本 tokenizer 包装"""

    def __init__(
        self,
        model_name: str = "Qwen/Qwen3-7B",
        max_length: int = 512
    ):
        self.tokenizer = AutoTokenizer.from_pretrained(model_name)
        self.max_length = max_length
        self.pad_token_id = self.tokenizer.pad_token_id
        self.eos_token_id = self.tokenizer.eos_token_id
        self.vocab_size = len(self.tokenizer)

    def tokenize(
        self,
        text: str,
        padding: bool = True,
        truncation: bool = True
    ) -> dict:
        """tokenize 单个文本"""
        return self.tokenizer(
            text,
            max_length=self.max_length,
            padding=padding if padding else False,
            truncation=truncation,
            return_tensors="pt"
        )

    def tokenize_batch(
        self,
        texts: List[str],
        padding: bool = True,
        truncation: bool = True
    ) -> dict:
        """tokenize 批量文本"""
        return self.tokenizer(
            texts,
            max_length=self.max_length,
            padding=padding if padding else "longest",
            truncation=truncation,
            return_tensors="pt"
        )


class LatentQuantizer:
    """
    使用 K-means 对 VAE latents 量化为离散语义 tokens
    用于 AR 模型训练数据准备
    """

    def __init__(
        self,
        num_codebook: int = 1024,
        seed: int = 42
    ):
        self.num_codebook = num_codebook
        self.seed = seed
        self.kmeans = KMeans(
            n_clusters=num_codebook,
            random_state=seed,
            n_init=10
        )
        self.codebook: Optional[np.ndarray] = None
        self.fitted = False

    def fit(self, latents: np.ndarray) -> None:
        """
        在 latents 上拟合 K-means

        参数:
            latents: [N, latent_dim] 扁平化的 latents
        """
        self.kmeans.fit(latents)
        self.codebook = self.kmeans.cluster_centers_
        self.fitted = True

    def quantize(self, latent: np.ndarray) -> np.ndarray:
        """
        量化单个 latent 为 token id

        参数:
            latent: [C, T] 或 [C, H, W] latent

        返回:
            tokens: [N] 量化后的 token ids，N = C*T 或 C*H*W
        """
        if not self.fitted:
            raise ValueError("K-means not fitted yet")

        # 扁平化
        latent_flat = latent.reshape(-1, latent.shape[-1]) \
            if len(latent.shape) > 2 else latent.reshape(1, -1)

        # 预测最近的中心
        tokens = self.kmeans.predict(latent_flat)
        return tokens

    def quantize_batch(
        self,
        latents: List[np.ndarray]
    ) -> List[np.ndarray]:
        """批量量化"""
        return [self.quantize(latent) for latent in latents]

    def dequantize(self, tokens: np.ndarray) -> np.ndarray:
        """从 token ids 重建 latent"""
        if not self.fitted:
            raise ValueError("K-means not fitted yet")
        return self.codebook[tokens]

    def save(self, path: str) -> None:
        """保存 kmeans 模型"""
        import joblib
        joblib.dump(self.kmeans, path)

    @classmethod
    def load(cls, path: str) -> 'LatentQuantizer':
        """加载 kmeans 模型"""
        import joblib
        kmeans = joblib.load(path)
        quantizer = cls(num_codebook=kmeans.n_clusters)
        quantizer.kmeans = kmeans
        quantizer.codebook = kmeans.cluster_centers_
        quantizer.fitted = True
        return quantizer


def collect_latents_for_kmeans(
    vae,
    dataloader,
    num_samples: int = 10000,
    device: str = 'cuda'
) -> np.ndarray:
    """
    从数据集中收集 latents 用于 K-means 训练

    参数:
        vae: 训练好的 VAE 模型
        dataloader: 数据加载器
        num_samples: 收集多少个 latent 向量
        device: 计算设备

    返回:
        latents: [num_samples, latent_channels] 收集的 latents
    """
    vae.eval()
    latents_list = []
    collected = 0

    with torch.no_grad():
        for batch in dataloader:
            if isinstance(batch, torch.Tensor):
                mel = batch.to(device)
            else:
                mel = batch['mel'].to(device)

            # VAE 编码得到 mu
            mu, log_var = vae.encode(mel)  # [B, C, H, W]

            # 扁平化收集每个 latent 向量
            # mu shape: [B, C, n_mels//16, time//16]
            B, C, H, W = mu.shape
            mu = mu.permute(0, 3, 2, 1).contiguous()  # [B, W, H, C]
            mu = mu.reshape(B * W * H, C)  # [B*W*H, C]

            latents_list.append(mu.cpu().numpy())
            collected += mu.shape[0]

            if collected >= num_samples:
                break

    latents = np.concatenate(latents_list, axis=0)[:num_samples]
    return latents

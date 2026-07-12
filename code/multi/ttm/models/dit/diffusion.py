"""
扩散过程：训练和采样逻辑
基于 DDPM/DDIM，支持分类器引导
"""
import torch
import torch.nn as nn
import numpy as np
from tqdm import tqdm
from typing import Optional


def create_diffusion(
    num_train_timesteps: int = 1000,
    beta_start: float = 0.0001,
    beta_end: float = 0.02,
    scheduler_type: str = 'ddpm'
):
    """创建扩散过程"""
    return GaussianDiffusion(
        num_train_timesteps=num_train_timesteps,
        beta_start=beta_start,
        beta_end=beta_end,
        scheduler_type=scheduler_type
    )


class GaussianDiffusion:
    """高斯扩散过程"""

    def __init__(
        self,
        num_train_timesteps: int = 1000,
        beta_start: float = 0.0001,
        beta_end: float = 0.02,
        scheduler_type: str = 'ddpm'
    ):
        self.num_train_timesteps = num_train_timesteps
        self.scheduler_type = scheduler_type

        # 线性调度 beta
        self.betas = torch.linspace(
            beta_start,
            beta_end,
            num_train_timesteps,
            dtype=torch.float32
        )

        # 计算扩散过程需要的各个量
        self.alphas = 1.0 - self.betas
        self.alphas_cumprod = torch.cumprod(self.alphas, dim=0)
        self.alphas_cumprod_prev = torch.cat(
            [torch.tensor([1.0]), self.alphas_cumprod[:-1]]
        )

        # 计算 q(x_t | x_{t-1}) 的参数
        # q(x_t | x_{t-1}) = N(x_t; sqrt(1-beta_t) x_{t-1}, beta_t I)
        self.sqrt_alphas_cumprod = torch.sqrt(self.alphas_cumprod)
        self.sqrt_one_minus_alphas_cumprod = torch.sqrt(1.0 - self.alphas_cumprod)
        self.posterior_variance = self.betas * (1.0 - self.alphas_cumprod_prev) / (1.0 - self.alphas_cumprod)

    def _get_variance(self, t):
        """获取后验方差"""
        variance = self.posterior_variance[t]
        # 避免 log(0)
        log_variance = torch.log(
            torch.clamp(variance, min=1e-20)
        )
        return variance, log_variance

    def q_sample(
        self,
        x_start: torch.Tensor,
        t: torch.Tensor,
        noise: Optional[torch.Tensor] = None
    ) -> torch.Tensor:
        """
        前向扩散过程：q(x_t | x_start)
        从 x_start 加噪声得到 x_t

        参数:
            x_start: 干净数据 x_0
            t: 时间步
            noise: 可选噪声，默认随机生成

        返回:
            x_t: 加噪后的数据
        """
        if noise is None:
            noise = torch.randn_like(x_start)

        sqrt_ac = self.sqrt_alphas_cumprod[t].reshape(-1, *([1] * (len(x_start.shape) - 1)))
        sqrt_one_minus_ac = self.sqrt_one_minus_alphas_cumprod[t].reshape(-1, *([1] * (len(x_start.shape) - 1)))

        return sqrt_ac * x_start + sqrt_one_minus_ac * noise

    def training_losses(
        self,
        model: nn.Module,
        x_start: torch.Tensor,
        t: torch.Tensor,
        model_kwargs: dict = None
    ) -> dict:
        """
        计算训练损失

        参数:
            model: 预测噪声的模型
            x_start: 干净数据 x_0
            t: 时间步
            model_kwargs: 传给模型的额外参数（条件）

        返回:
            损失字典
        """
        if model_kwargs is None:
            model_kwargs = {}

        # 采样噪声
        noise = torch.randn_like(x_start)
        # 前向加噪得到 x_t
        x_t = self.q_sample(x_start, t, noise=noise)
        # 模型预测噪声
        model_output = model(x_t, t, **model_kwargs)

        # 如果模型学习 sigma，分开输出
        if model.learn_sigma:
            # 输出前半是 eps，后半是 sigma
            eps, _ = torch.split(model_output, model.in_channels, dim=1)
        else:
            eps = model_output

        # MSE 损失
        loss = (eps - noise).pow(2).mean()

        return {
            'loss': loss,
            'mse': loss
        }

    @torch.no_grad()
    def p_sample(
        self,
        model: nn.Module,
        x: torch.Tensor,
        t: int,
        cond_tokens: torch.Tensor,
        cfg_scale: float = 1.0,
        device: str = 'cuda'
    ) -> torch.Tensor:
        """
        DDPM 单步采样：p(x_{t-1} | x_t)

        参数:
            model: 模型
            x: 当前 x_t [B, C, T]
            t: 当前时间步
            cond_tokens: 条件 tokens [B, L]
            cfg_scale: classifier-free guidance 强度
            device: 计算设备

        返回:
            x_{t-1}
        """
        B, C, T = x.shape
        t_tensor = torch.tensor([t] * B, device=device)

        if cfg_scale > 1.0:
            # 同时做条件和无条件前向
            # 重复 x 和 t，前半是条件，后半是无条件（空 token）
            x_in = torch.cat([x, x], dim=0)
            t_in = torch.cat([t_tensor, t_tensor], dim=0)
            # 后半条件 token 用空 token id = num_semantic_tokens
            cond_in = torch.cat([
                cond_tokens,
                torch.full_like(cond_tokens, model.y_embedder.num_semantic_tokens)
            ], dim=0)
            eps = model.forward_with_cfg(x_in, t_in, cond_in, cfg_scale)[:B]
        else:
            eps = model(x, t_tensor, cond_tokens)
            if model.learn_sigma:
                eps, _ = torch.split(eps, model.in_channels, dim=1)

        # 计算各个系数
        alpha = self.alphas[t]
        alpha_cumprod = self.alphas_cumprod[t]
        alpha_cumprod_prev = self.alphas_cumprod_prev[t]
        sqrt_one_minus_ac = self.sqrt_one_minus_alphas_cumprod[t]
        sqrt_recip_alpha = 1 / torch.sqrt(alpha)

        # 预测 x_0
        x_0 = (x - sqrt_one_minus_ac * eps) / torch.sqrt(alpha_cumprod)

        # 计算系数
        coeff1 = torch.sqrt(alpha_cumprod_prev) * self.betas[t] / (1 - alpha_cumprod)
        coeff2 = torch.sqrt(alpha) * (1 - alpha_cumprod_prev) / (1 - alpha_cumprod)

        # 后验均值
        mean = coeff1 * x_0 + coeff2 * x

        # 后验方差
        variance, log_variance = self._get_variance(t)

        # 如果 t > 0，加噪声，否则就是确定的 mean
        if t > 0:
            noise = torch.randn_like(x)
            x_prev = mean + torch.exp(0.5 * log_variance) * noise
        else:
            x_prev = mean

        return x_prev

    @torch.no_grad()
    def sample(
        self,
        model: nn.Module,
        num_samples: int,
        shape: tuple,
        cond_tokens: torch.Tensor,
        cfg_scale: float = 2.0,
        num_inference_steps: int = 50,
        device: str = 'cuda'
    ) -> torch.Tensor:
        """
        完整采样过程

        参数:
            model: DiT 模型
            num_samples: 采样数量
            shape: latent 形状 (channels, length)
            cond_tokens: 条件语义 tokens [num_samples, seq_len]
            cfg_scale: classifier-free guidance 强度
            num_inference_steps: 推理步数，可以比训练步数少
            device: 计算设备

        返回:
            x_0: 采样得到的 latent [num_samples, channels, length]
        """
        # 从 N(0,1) 开始
        x = torch.randn(num_samples, *shape, device=device)

        # 时间步从最后一步往回走
        # 如果推理步数少于训练步数，需要跳步
        if num_inference_steps < self.num_train_timesteps:
            step_ratio = self.num_train_timesteps // num_inference_steps
            timesteps = list(range(self.num_train_timesteps - 1, -1, -step_ratio))
        else:
            timesteps = list(range(self.num_train_timesteps - 1, -1, -1))

        # 逐步采样
        for t in tqdm(timesteps, desc="Sampling", total=len(timesteps)):
            x = self.p_sample(
                model=model,
                x=x,
                t=t,
                cond_tokens=cond_tokens,
                cfg_scale=cfg_scale,
                device=device
            )

        return x

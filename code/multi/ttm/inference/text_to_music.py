"""
完整的文本到音乐生成推理 pipeline
端到端：文本 → AR → 语义 tokens → DiT → latent → VAE → mel → 声码器 → 音频
"""
import os
import torch
import yaml
import numpy as np
from typing import Optional, Tuple, Union

# 导入项目模块
import sys
sys.path.append('..')
from models.vae import VAE
from models.dit import DiT_models
from models.dit.diffusion import create_diffusion
from models.ar import ARQwen3
from vocoder import GriffinLimVocoder, HiFiGANVocoder


class TextToMusicPipeline:
    """
    完整的文本到音乐生成推理 pipeline

    三阶段生成:
    1. AR 模型 (Qwen3) 从文本描述自回归生成语义 tokens
    2. DiT 扩散模型从语义 tokens 生成 VAE latent
    3. VAE 解码 latent 得到 mel-spectrogram
    4. 声码器从 mel 生成音频波形
    """

    def __init__(
        self,
        config_path: str,
        vae_ckpt_path: str,
        dit_ckpt_path: str,
        ar_ckpt_path: Optional[str] = None,
        device: str = 'cuda',
        vocoder_type: str = 'griffin_lim',
        vocoder_ckpt_path: Optional[str] = None,
    ):
        """
        参数:
            config_path: 推理配置文件路径
            vae_ckpt_path: VAE 检查点路径
            dit_ckpt_path: DiT 检查点路径
            ar_ckpt_path: AR LoRA 检查点路径（如果不使用 AR 可以为 None，生成无条件音乐）
            device: 计算设备
            vocoder_type: 声码器类型 'griffin_lim' 或 'hifigan'
            vocoder_ckpt_path: HiFi-GAN 检查点路径
        """
        with open(config_path, 'r', encoding='utf-8') as f:
            self.config = yaml.safe_load(f)

        self.device = torch.device(device if torch.cuda.is_available() else 'cpu')

        # 加载所有模型
        self._load_vae(vae_ckpt_path)
        self._load_dit(dit_ckpt_path)
        self._load_ar(ar_ckpt_path)
        self._load_vocoder(vocoder_type, vocoder_ckpt_path)

        # 创建扩散过程
        diffusion_config = self.config.get('diffusion', {})
        self.diffusion = create_diffusion(
            num_train_timesteps=diffusion_config.get('num_train_timesteps', 1000),
            beta_start=diffusion_config.get('beta_start', 0.0001),
            beta_end=diffusion_config.get('beta_end', 0.02),
            scheduler_type=diffusion_config.get('scheduler_type', 'ddim')
        )

        self.num_semantic_tokens = self.config['model'].get('num_semantic_tokens', 1024)

    def _load_vae(self, ckpt_path: str):
        """加载 VAE"""
        print(f"Loading VAE from {ckpt_path}")
        model_config = self.config['vae']
        self.vae = VAE(
            in_channels=model_config.get('in_channels', 1),
            base_channels=model_config.get('base_channels', 64),
            latent_channels=model_config.get('latent_channels', 32),
            num_downsampling_layers=model_config.get('num_downsampling_layers', 4),
        ).to(self.device)

        checkpoint = torch.load(ckpt_path, map_location=self.device)
        if 'model_state_dict' in checkpoint:
            self.vae.load_state_dict(checkpoint['model_state_dict'])
        else:
            self.vae.load_state_dict(checkpoint)
        self.vae.eval()
        print("VAE loaded")

    def _load_dit(self, ckpt_path: str):
        """加载 DiT"""
        print(f"Loading DiT from {ckpt_path}")
        model_config = self.config['dit']
        model_name = model_config.get('model_name', 'DiT-B/2')
        input_size = model_config.get('input_size', 64)
        in_channels = model_config.get('in_channels', 256)

        self.dit = DiT_models[model_name](
            input_size=input_size,
            in_channels=in_channels,
            hidden_size=model_config.get('hidden_size', 768),
            depth=model_config.get('depth', 12),
            num_heads=model_config.get('num_heads', 12),
            patch_size=model_config.get('patch_size', 2),
            mlp_ratio=model_config.get('mlp_ratio', 4.0),
            num_semantic_tokens=self.num_semantic_tokens + 1,  # +1 for null token
            cond_dropout_prob=model_config.get('cond_dropout_prob', 0.1),
            learn_sigma=True
        ).to(self.device)

        checkpoint = torch.load(ckpt_path, map_location=self.device)
        if 'model' in checkpoint:
            self.dit.load_state_dict(checkpoint['model'])
        else:
            self.dit.load_state_dict(checkpoint)
        self.dit.eval()
        print("DiT loaded")

    def _load_ar(self, ckpt_path: Optional[str]):
        """加载 AR 模型（Qwen3 LoRA）"""
        if ckpt_path is None:
            print("No AR checkpoint provided, will use unconditional generation")
            self.ar = None
            return

        print(f"Loading AR from {ckpt_path}")
        model_config = self.config['ar']
        lora_config = model_config.get('lora', {})

        self.ar = ARQwen3(
            base_model_name=model_config.get('base_model_name', 'Qwen/Qwen3-7B'),
            num_semantic_tokens=self.num_semantic_tokens,
            max_sequence_length=model_config.get('max_sequence_length', 2048),
            lora_enable=True,
            lora_rank=lora_config.get('rank', 32),
            lora_alpha=lora_config.get('alpha', 64),
            lora_dropout=lora_config.get('dropout', 0.05),
            lora_target_modules=lora_config.get('target_modules', ["q_proj", "v_proj"]),
            device=self.device,
            load_in_4bit=model_config.get('load_in_4bit', False),
            load_in_8bit=model_config.get('load_in_8bit', False)
        ).to(self.device)

        self.ar.load(ckpt_path)
        self.ar.eval()
        print("AR loaded")

    def _load_vocoder(self, vocoder_type: str, ckpt_path: Optional[str]):
        """加载声码器"""
        if vocoder_type == 'griffin_lim':
            vocoder_config = self.config.get('griffin_lim', {})
            self.vocoder = GriffinLimVocoder(
                sample_rate=vocoder_config.get('sample_rate', 44100),
                n_fft=vocoder_config.get('n_fft', 2048),
                hop_length=vocoder_config.get('hop_length', 512),
                n_mels=vocoder_config.get('n_mels', 128),
                f_min=vocoder_config.get('f_min', 0.0),
                f_max=vocoder_config.get('f_max', 22050.0),
                n_iter=vocoder_config.get('n_iter', 32),
            )
        elif vocoder_type == 'hifigan':
            assert ckpt_path is not None, "HiFi-GAN requires checkpoint path"
            self.vocoder = HiFiGANVocoder(
                model_path=ckpt_path,
                device=str(self.device)
            )
        else:
            raise ValueError(f"Unknown vocoder type: {vocoder_type}")
        print(f"{vocoder_type} vocoder loaded")

    def generate_semantic_tokens(
        self,
        text_prompt: str,
        max_tokens: int = 64,
        temperature: float = 0.8,
        top_p: float = 0.9,
    ) -> torch.Tensor:
        """
        使用 AR 模型从文本生成语义 tokens

        参数:
            text_prompt: 文本描述
            max_tokens: 最大生成语义 token 数量
            temperature: 采样温度
            top_p: top-p 采样

        返回:
            semantic_tokens: [1, L] 语义 tokens
        """
        if self.ar is None:
            # 无条件生成，使用空 token 序列
            print("No AR model, using unconditional generation (all null tokens)")
            return torch.full(
                (1, 1),
                self.num_semantic_tokens,
                dtype=torch.long,
                device=self.device
            )

        with torch.no_grad():
            tokens = self.ar.generate(
                text_prompt=text_prompt,
                max_tokens=max_tokens,
                temperature=temperature,
                top_p=top_p
            )

        # 添加批量维度 [L] → [1, L]
        if tokens.ndim == 1:
            tokens = tokens.unsqueeze(0)

        return tokens

    def generate_latent(
        self,
        semantic_tokens: torch.Tensor,
        cfg_scale: float = 2.0,
        num_inference_steps: int = 50,
    ) -> torch.Tensor:
        """
        使用 DiT 扩散模型从语义 tokens 生成 latent

        参数:
            semantic_tokens: [B, L] 语义 tokens
            cfg_scale: classifier-free guidance 强度
            num_inference_steps: 扩散采样步数

        返回:
            latent: [B, C, T] VAE latent
        """
        B = semantic_tokens.shape[0]
        # latent 形状: [C, T] = [256, 64]
        # 256 = 32 (VAE latent) × 8 (频率压缩)
        # 64 = 1024 时间帧 / 16 压缩
        C = self.config['dit'].get('in_channels', 256)
        T = self.config['dit'].get('input_size', 64)

        shape = (C, T)
        latent = self.diffusion.sample(
            model=self.dit,
            num_samples=B,
            shape=shape,
            cond_tokens=semantic_tokens,
            cfg_scale=cfg_scale,
            num_inference_steps=num_inference_steps,
            device=self.device
        )

        return latent

    def latent_to_mel(self, latent: torch.Tensor) -> np.ndarray:
        """
        从 DiT latent 解码得到 mel-spectrogram

        参数:
            latent: [B, C, T] DiT 输出 latent
                B = batch size
                C = 256 = 32 × 8
                T = 64

        返回:
            mel: [n_mels, time_frames] mel-spectrogram (10 秒 × 44100 / 512 = 860 帧 → 压缩到 64)
        """
        # DiT latent 形状 [B, 256, 64] → reshape 回 VAE 需要的 [B, 32, 8, 64]
        B, C, T = latent.shape
        # 256 = 32 channels × 8 freq bins
        vae_latent = latent.reshape(B, 32, 8, T)

        # VAE 解码
        with torch.no_grad():
            mel = self.vae.decode(vae_latent)  # [B, 1, 128, 1024]

        # 取第一个样本，去掉批量和通道维度 [128, 1024]
        mel = mel[0, 0].cpu().numpy()
        return mel

    @torch.no_grad()
    def generate(
        self,
        text_prompt: str,
        max_semantic_tokens: int = 64,
        temperature: float = 0.8,
        top_p: float = 0.9,
        cfg_scale: float = 2.0,
        num_inference_steps: int = 50,
    ) -> Tuple[np.ndarray, np.ndarray]:
        """
        完整的端到端文本到音乐生成

        参数:
            text_prompt: 文本描述
            max_semantic_tokens: AR 生成的最大语义 token 数（对应时间长度）
            temperature: AR 采样温度
            top_p: AR top-p 采样
            cfg_scale: DiT classifier-free guidance 强度
            num_inference_steps: DiT 扩散采样步数

        返回:
            waveform: 生成的音频波形
            mel: mel-spectrogram [n_mels, time]
        """
        print(f"Generating music for prompt: {text_prompt}")

        # 阶段 1: AR 生成语义 tokens
        print("Stage 1: Generating semantic tokens with AR...")
        semantic_tokens = self.generate_semantic_tokens(
            text_prompt,
            max_tokens=max_semantic_tokens,
            temperature=temperature,
            top_p=top_p
        )

        # 阶段 2: DiT 生成 latent
        print("Stage 2: Generating latent with DiT diffusion...")
        latent = self.generate_latent(
            semantic_tokens,
            cfg_scale=cfg_scale,
            num_inference_steps=num_inference_steps
        )

        # 阶段 3: VAE 解码得到 mel
        print("Stage 3: Decoding mel-spectrogram with VAE...")
        mel = self.latent_to_mel(latent)

        # 阶段 4: 声码器生成音频
        print("Stage 4: Generating audio with vocoder...")
        waveform = self.vocoder.generate(mel, denormalize=True)

        print("Generation complete!")
        return waveform, mel

    def generate_unconditional(
        self,
        cfg_scale: float = 1.0,
        num_inference_steps: int = 50,
    ) -> Tuple[np.ndarray, np.ndarray]:
        """
        无条件生成（不需要文本提示）

        返回:
            waveform: 音频波形
            mel: mel-spectrogram
        """
        semantic_tokens = torch.full(
            (1, 1),
            self.num_semantic_tokens,
            dtype=torch.long,
            device=self.device
        )

        print("Stage 1: Unconditional generation (empty condition)")
        print("Stage 2: Generating latent with DiT diffusion...")
        latent = self.generate_latent(
            semantic_tokens,
            cfg_scale=cfg_scale,
            num_inference_steps=num_inference_steps
        )

        print("Stage 3: Decoding mel-spectrogram with VAE...")
        mel = self.latent_to_mel(latent)

        print("Stage 4: Generating audio with vocoder...")
        waveform = self.vocoder.generate(mel, denormalize=True)

        print("Generation complete!")
        return waveform, mel

    def save_audio(self, waveform: np.ndarray, output_path: str):
        """保存生成的音频"""
        # 使用 librosa 保存
        import librosa
        import soundfile as sf
        sf.write(output_path, waveform, self.vocoder.sample_rate)
        print(f"Audio saved to {output_path}")

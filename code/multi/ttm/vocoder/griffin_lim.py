"""
Griffin-Lim 算法声码器
从 mel-spectrogram 重建音频波形
不需要训练，直接可用，作为 baseline
"""
import torch
import numpy as np
import librosa
from typing import Optional


class GriffinLimVocoder:
    """
    Griffin-Lim 迭代算法从 mel-spectrogram 重建波形
    """

    def __init__(
        self,
        sample_rate: int = 44100,
        n_fft: int = 2048,
        hop_length: int = 512,
        n_mels: int = 128,
        f_min: float = 0.0,
        f_max: float = 22050.0,
        n_iter: int = 32,
    ):
        """
        参数:
            sample_rate: 采样率
            n_fft: FFT 窗口大小
            hop_length: 帧移
            n_mels: mel 滤波器数量
            f_min: 最低频率
            f_max: 最高频率
            n_iter: Griffin-Lim 迭代次数
        """
        self.sample_rate = sample_rate
        self.n_fft = n_fft
        self.hop_length = hop_length
        self.n_mels = n_mels
        self.f_min = f_min
        self.f_max = f_max
        self.n_iter = n_iter

        # 创建 mel 滤波器矩阵（从 mel 回到线性频谱）
        self.mel_basis = librosa.filters.mel(
            sr=sample_rate,
            n_fft=n_fft,
            n_mels=n_mels,
            fmin=f_min,
            fmax=f_max
        )
        # 伪逆，用于从 mel 转回线性频谱
        self.mel_basis_inv = np.linalg.pinv(self.mel_basis)

    def decompress_normalization(self, mel: np.ndarray) -> np.ndarray:
        """
        解压 [-1, 1] 归一化回到对数 mel 空间

        参数:
            mel: [n_mels, time] 归一化到 [-1, 1] 的 mel-spectrogram

        返回:
            log_mel: [n_mels, time] 对数 mel-spectrogram
        """
        # 从 [-1, 1] 转换回 [0, 1] 再到对数空间
        # 我们在预处理中做了: mel = (mel - min) / (max - min) * 2 - 1
        # 这里是逆过程
        mel = (mel + 1) / 2  # [0, 1]
        # 注意: 预处理中我们做了 log(clamp(mel, min=1e-5)))
        # 所以 log(mel) 范围大约从 -11.5 到大约 6（因为原 mel 是幅值，范围 0 到 ~400）
        # 我们只需要逆归一化到 0-1，然后指数得到 mel 幅值
        # 这里假设原始对数范围大约是 [-12, 8]，反变换回去
        min_log = -12
        max_log = 8
        mel = mel * (max_log - min_log) + min_log
        mel = np.exp(mel)
        return mel

    def mel_to_linear(self, mel: np.ndarray) -> np.ndarray:
        """
        将 mel-spectrogram 转换回线性频谱

        参数:
            mel: [n_mels, time] mel-spectrogram（幅值，不是对数）

        返回:
            linear: [n_fft//2 + 1, time] 线性频谱
        """
        linear = self.mel_basis_inv @ mel
        linear = np.maximum(linear, 1e-10)  # 避免负值
        return linear

    def griffin_lim(self, linear_spec: np.ndarray) -> np.ndarray:
        """
        Griffin-Lim 迭代算法从幅度谱重建波形

        参数:
            linear_spec: [n_fft//2 + 1, time] 幅度谱

        返回:
            waveform: 重建的波形
        """
        angles = np.exp(2j * np.pi * np.random.rand(*linear_spec.shape))
        complex_spec = linear_spec * angles

        for i in range(self.n_iter):
            # 逆 STFT
            waveform = librosa.istft(
                complex_spec,
                hop_length=self.hop_length,
                n_fft=self.nfft,
                window='hann'
            )
            # STFT
            new_complex = librosa.stft(
                waveform,
                n_fft=self.nfft,
                hop_length=self.hop_length,
                window='hann'
            )
            # 保持相位，更新幅度
            angles = np.angle(new_complex)
            complex_spec = linear_spec * np.exp(1j * angles)

        # 最后一次逆 STFT
        waveform = librosa.istft(
            complex_spec,
            hop_length=self.hop_length,
            n_fft=self.nfft,
            window='hann'
        )
        return waveform

    @torch.no_grad()
    def generate(
        self,
        mel: np.ndarray,
        denormalize: bool = True
    ) -> np.ndarray:
        """
        从 mel-spectrogram 生成波形

        参数:
            mel: [n_mels, time] mel-spectrogram，如果是归一化到 [-1, 1] 需要设置 denormalize=True
            denormalize: 是否反归一化

        返回:
            waveform: 生成的音频波形
        """
        if denormalize:
            mel = self.decompress_normalization(mel)

        # mel 转线性频谱
        linear_spec = self.mel_to_linear(mel)

        # Griffin-Lim 迭代
        waveform = self.griffin_lim(linear_spec)

        return waveform

    def save_wav(self, waveform: np.ndarray, path: str):
        """保存音频文件"""
        librosa.output.write_wav(path, waveform, self.sample_rate)

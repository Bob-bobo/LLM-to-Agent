"""
音频预处理流水线：加载音频 → 重采样 → 转换单声道 → 提取 mel-spectrogram → 归一化
"""
import torch
import torchaudio
import librosa
import numpy as np
from dataclasses import dataclass
from typing import Tuple, Optional, Union


@dataclass
class PreprocessingConfig:
    """预处理配置"""
    sample_rate: int = 44100
    n_fft: int = 2048
    hop_length: int = 512
    n_mels: int = 128
    f_min: float = 0.0
    f_max: float = 22050.0
    segment_duration: float = 10.0  # 每个片段的持续时间（秒）
    normalize: bool = True
    mean: float = 0.0
    std: float = 1.0


class AudioPreprocessor:
    """音频预处理器"""

    def __init__(self, config: PreprocessingConfig = None):
        self.config = config or PreprocessingConfig()
        self.mel_transform = torchaudio.transforms.MelSpectrogram(
            sample_rate=self.config.sample_rate,
            n_fft=self.config.n_fft,
            hop_length=self.config.hop_length,
            n_mels=self.config.n_mels,
            f_min=self.config.f_min,
            f_max=self.config.f_max,
            power=1.0,  # 使用幅值，不是能量
        )

    def load_audio(self, file_path: str) -> Tuple[torch.Tensor, int]:
        """
        加载音频文件

        返回:
            waveform: [1, time] 单声道张量
            sample_rate: 采样率
        """
        waveform, sr = torchaudio.load(file_path)
        return waveform, sr

    def resample(self, waveform: torch.Tensor, orig_sr: int) -> torch.Tensor:
        """重采样到目标采样率"""
        if orig_sr == self.config.sample_rate:
            return waveform
        resampler = torchaudio.transforms.Resample(
            orig_sr, self.config.sample_rate
        )
        return resampler(waveform)

    def convert_to_mono(self, waveform: torch.Tensor) -> torch.Tensor:
        """转换为单声道"""
        if waveform.shape[0] > 1:
            # 多声道平均为单声道
            waveform = torch.mean(waveform, dim=0, keepdim=True)
        return waveform

    def extract_mel_spectrogram(self, waveform: torch.Tensor) -> torch.Tensor:
        """
        提取 mel-spectrogram

        参数:
            waveform: [1, time] 单声道波形

        返回:
            mel: [n_mels, time_frames] mel-spectrogram
        """
        mel = self.mel_transform(waveform)  # [1, n_mels, time_frames]
        mel = mel.squeeze(0)  # [n_mels, time_frames]

        # 对数压缩，类似音频处理中的标准做法
        mel = torch.log(torch.clamp(mel, min=1e-5))

        return mel

    def normalize_mel(self, mel: torch.Tensor) -> torch.Tensor:
        """归一化 mel-spectrogram 到 [-1, 1]"""
        if self.config.normalize:
            mel_min = mel.min()
            mel_max = mel.max()
            # 缩放到 [0, 1] 然后到 [-1, 1]
            mel = (mel - mel_min) / (mel_max - mel_min + 1e-8) * 2 - 1
        return mel

    def segment_mel(
        self,
        mel: torch.Tensor,
        hop_duration: Optional[float] = None
    ) -> torch.Tensor:
        """
        将长 mel-spectrogram 切分为固定长度片段

        参数:
            mel: [n_mels, time_frames]
            hop_duration: 滑动窗口步长（秒），None 表示不重叠

        返回:
            segments: [num_segments, 1, n_mels, segment_frames]
                注意：维度顺序是 [batch, channel, n_mels, time]，方便卷积处理
        """
        n_mels, total_frames = mel.shape
        segment_frames = int(self.config.segment_duration *
                             self.config.sample_rate / self.config.hop_length)

        if hop_duration is None:
            hop_frames = segment_frames  # 不重叠
        else:
            hop_frames = int(hop_duration * self.config.sample_rate /
                             self.config.hop_length)

        segments = []
        for start in range(0, total_frames - segment_frames + 1, hop_frames):
            end = start + segment_frames
            segment = mel[:, start:end]
            segments.append(segment)

        # 如果最后一段不够长，填充
        if total_frames > 0 and total_frames < segment_frames:
            padding = segment_frames - total_frames
            mel_padded = torch.nn.functional.pad(mel, (0, padding))
            segments.append(mel_padded)
        elif total_frames > segment_frames and (total_frames - segment_frames) % hop_frames != 0:
            # 处理最后一个不完整窗口
            start = total_frames - segment_frames
            end = total_frames
            segment = mel[:, start:end]
            segments.append(segment)

        # 转换为 [num_segments, 1, n_mels, segment_frames]
        if len(segments) == 0:
            return torch.empty((0, 1, n_mels, segment_frames))

        segments_tensor = torch.stack(segments, dim=0)  # [N, n_mels, T]
        segments_tensor = segments_tensor.unsqueeze(1)  # [N, 1, n_mels, T]

        return segments_tensor

    def process(
        self,
        file_path: Union[str, torch.Tensor],
        segment: bool = True
    ) -> Union[torch.Tensor, Tuple[torch.Tensor, torch.Tensor]]:
        """
        完整预处理流水线

        参数:
            file_path: 音频文件路径 或者 已经加载的 waveform
            segment: 是否切分为固定长度片段

        返回:
            如果 segment=True: [num_segments, 1, n_mels, segment_frames]
            如果 segment=False: [1, n_mels, time_frames] (完整 mel)
        """
        # 1. 加载音频
        if isinstance(file_path, str):
            waveform, orig_sr = self.load_audio(file_path)
        else:
            waveform = file_path
            orig_sr = self.config.sample_rate

        # 2. 重采样
        waveform = self.resample(waveform, orig_sr)

        # 3. 转换单声道
        waveform = self.convert_to_mono(waveform)

        # 4. 提取 mel-spectrogram
        mel = self.extract_mel_spectrogram(waveform)

        # 5. 归一化
        mel = self.normalize_mel(mel)

        # 6. 切分片段
        if segment:
            segments = self.segment_mel(mel)
            return segments
        else:
            # 返回 [1, n_mels, time_frames]
            return mel.unsqueeze(0)

    def process_to_numpy(
        self,
        file_path: str,
        segment: bool = True
    ) -> Union[np.ndarray, np.ndarray]:
        """处理并保存为 numpy 数组"""
        tensor = self.process(file_path, segment=segment)
        return tensor.cpu().numpy()


# SpecAugment 数据增强
class SpecAugment:
    """SpecAugment 数据增强：时间掩码和频率掩码"""

    def __init__(
        self,
        time_mask_param: int = 50,
        freq_mask_param: int = 10,
        n_time_masks: int = 2,
        n_freq_masks: int = 2
    ):
        self.time_mask_param = time_mask_param
        self.freq_mask_param = freq_mask_param
        self.n_time_masks = n_time_masks
        self.n_freq_masks = n_freq_masks

    def __call__(self, mel: torch.Tensor) -> torch.Tensor:
        """
        参数:
            mel: [..., n_mels, time_frames] mel-spectrogram

        返回:
            掩码后的 mel
        """
        # 频率掩码
        for _ in range(self.n_freq_masks):
            mel = self._freq_mask(mel)

        # 时间掩码
        for _ in range(self.n_time_masks):
            mel = self._time_mask(mel)

        return mel

    def _freq_mask(self, mel: torch.Tensor) -> torch.Tensor:
        n_mels = mel.shape[-2]
        f = torch.randint(0, self.freq_mask_param + 1, ()).item()
        f_start = torch.randint(0, n_mels - f + 1, ()).item()
        mel[..., f_start:f_start + f, :] = 0
        return mel

    def _time_mask(self, mel: torch.Tensor) -> torch.Tensor:
        time_frames = mel.shape[-1]
        t = torch.randint(0, self.time_mask_param + 1, ()).item()
        t_start = torch.randint(0, time_frames - t + 1, ()).item()
        mel[..., :, t_start:t_start + t] = 0
        return mel


def pitch_shift(
    mel: torch.Tensor,
    n_semitones: float,
    sample_rate: int,
    hop_length: int
) -> torch.Tensor:
    """
    对 mel-spectrogram 做 pitch shift 数据增强

    参数:
        mel: [n_mels, time_frames]
        n_semitones: 移位半音数，可以是小数

    返回:
        shifted_mel: [n_mels, time_frames]
    """
    mel_np = mel.cpu().numpy()
    shifted = librosa.effects.pitch_shift(
        mel_np,
        sr=sample_rate // hop_length,  # 注意这里是帧速率
        n_steps=n_semitones,
        bins_per_octave=12
    )
    return torch.tensor(shifted, dtype=mel.dtype, device=mel.device)

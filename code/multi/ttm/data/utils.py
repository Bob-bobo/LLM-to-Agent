"""
数据处理工具函数
"""
import os
import numpy as np
from typing import List, Tuple
from tqdm import tqdm
from .preprocessing import AudioPreprocessor, PreprocessingConfig


def preprocess_dataset(
    input_dir: str,
    output_dir: str,
    config: PreprocessingConfig = None,
    extensions: List[str] = ('.mp3', '.wav', '.flac')
) -> None:
    """
    批量预处理整个数据集目录

    参数:
        input_dir: 原始音频文件目录
        output_dir: 输出预处理后的 .npy 文件目录
        config: 预处理配置
        extensions: 要处理的音频扩展名
    """
    os.makedirs(output_dir, exist_ok=True)
    preprocessor = AudioPreprocessor(config or PreprocessingConfig())

    # 收集所有音频文件
    audio_files = []
    for root, _, files in os.walk(input_dir):
        for f in files:
            if f.lower().endswith(extensions):
                audio_files.append(os.path.join(root, f))

    print(f"Found {len(audio_files)} audio files")

    # 批量处理
    for audio_path in tqdm(audio_files, desc="Preprocessing"):
        try:
            segments = preprocessor.process_to_numpy(audio_path, segment=True)

            # 输出文件名保持一致，只是扩展名改为 .npy
            rel_path = os.path.relpath(audio_path, input_dir)
            base_name = os.path.splitext(rel_path)[0].replace(os.path.sep, '_')
            output_path = os.path.join(output_dir, f"{base_name}.npy")

            np.save(output_path, segments)
        except Exception as e:
            print(f"Error processing {audio_path}: {e}")
            continue

    print(f"Preprocessing complete. Files saved to {output_dir}")


def create_metadata_json(
    data_dir: str,
    output_path: str,
    has_text: bool = False
) -> None:
    """
    创建数据集元数据 json 文件

    参数:
        data_dir: 预处理后的 .npy 文件目录
        output_path: 输出 json 文件路径
        has_text: 是否包含文本描述（此时文件名应该就是描述）
    """
    import json
    files = sorted([f for f in os.listdir(data_dir) if f.endswith('.npy')])

    metadata = []
    for f in files:
        if has_text:
            # 如果文件名就是描述，去掉扩展名
            text = os.path.splitext(f)[0].replace('_', ' ')
            metadata.append({
                'text': text,
                'audio_file': f
            })
        else:
            metadata.append({
                'audio_file': f
            })

    with open(output_path, 'w', encoding='utf-8') as f:
        json.dump(metadata, f, indent=2, ensure_ascii=False)

    print(f"Created metadata with {len(metadata)} entries at {output_path}")


def get_dataset_stats(data_dir: str) -> Tuple[float, float]:
    """
    计算整个数据集的均值和标准差，用于归一化

    返回:
        mean, std
    """
    import numpy as np
    files = [os.path.join(data_dir, f) for f in os.listdir(data_dir)
             if f.endswith('.npy')]

    total_sum = 0.0
    total_sum_sq = 0.0
    total_count = 0

    for file in tqdm(files, desc="Computing stats"):
        data = np.load(file)
        total_sum += data.sum()
        total_sum_sq += (data ** 2).sum()
        total_count += data.size

    mean = total_sum / total_count
    variance = (total_sum_sq / total_count) - mean ** 2
    std = np.sqrt(max(variance, 1e-8))

    print(f"Dataset stats: mean={mean:.4f}, std={std:.4f}")
    return mean, std

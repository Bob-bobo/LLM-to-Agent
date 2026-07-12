#!/usr/bin/env python
"""
音频数据预处理脚本
遍历输入目录，提取 mel-spectrogram 并保存为 .npy 文件
"""
import os
import argparse
import yaml
import numpy as np
from tqdm import tqdm

import sys
sys.path.append('..')
from data.preprocessing import AudioPreprocessor, PreprocessingConfig


def main():
    parser = argparse.ArgumentParser(description="Audio Preprocessing")
    parser.add_argument('--config', type=str, default='../configs/preprocessing.yaml',
                        help='预处理配置文件路径')
    parser.add_argument('--input-dir', type=str, required=True,
                        help='输入音频文件目录')
    parser.add_argument('--output-dir', type=str, required=True,
                        help='输出预处理后 .npy 文件目录')
    args = parser.parse_args()

    # 加载配置
    with open(args.config, 'r', encoding='utf-8') as f:
        config = yaml.safe_load(f)

    # 创建输出目录
    os.makedirs(args.output_dir, exist_ok=True)

    # 创建预处理器
    preproc_config = PreprocessingConfig(
        sample_rate=config['preprocessing'].get('sample_rate', 44100),
        n_fft=config['preprocessing'].get('n_fft', 2048),
        hop_length=config['preprocessing'].get('hop_length', 512),
        n_mels=config['preprocessing'].get('n_mels', 128),
        f_min=config['preprocessing'].get('f_min', 0.0),
        f_max=config['preprocessing'].get('f_max', 22050.0),
        segment_duration=config['preprocessing'].get('segment_duration', 10.0),
        normalize=config['preprocessing'].get('normalize', True),
    )

    preprocessor = AudioPreprocessor(preproc_config)

    # 遍历所有音频文件
    audio_extensions = ('.wav', '.mp3', '.flac', '.ogg', '.m4a')
    audio_files = []
    for root, _, files in os.walk(args.input_dir):
        for file in files:
            if file.lower().endswith(audio_extensions):
                audio_files.append(os.path.join(root, file))

    print(f"Found {len(audio_files)} audio files")

    # 处理每个文件
    for audio_path in tqdm(audio_files, desc="Processing"):
        # 获取相对路径保持目录结构
        rel_path = os.path.relpath(audio_path, args.input_dir)
        base_name = os.path.splitext(rel_path)[0].replace(os.path.sep, '_')

        # 处理
        try:
            segments = preprocessor.process_to_numpy(audio_path, segment=True)
        except Exception as e:
            print(f"Error processing {audio_path}: {e}")
            continue

        # 保存
        output_path = os.path.join(args.output_dir, f"{base_name}.npy")
        np.save(output_path, segments)

    print(f"Done! Processed {len(audio_files)} files, output to {args.output_dir}")


if __name__ == '__main__':
    main()

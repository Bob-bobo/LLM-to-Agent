#!/usr/bin/env python
"""
使用预训练好的 VAE 编码音乐，并使用 K-means 量化为语义 tokens
流程:
1. 加载预训练 VAE
2. 遍历处理好的 mel-spectrogram 文件
3. VAE 编码得到 latent
4. 收集所有 latents 拟合 K-means
5. 使用 K-means 对每个文件量化得到语义 tokens
6. 保存码本和量化结果
"""
import os
import argparse
import yaml
import numpy as np
import joblib
from tqdm import tqdm

import sys
script_dir = os.path.dirname(os.path.abspath(__file__))
project_root = os.path.dirname(script_dir)
sys.path.append(project_root)

import torch
from models.vae import VAE
from data.tokenizer import LatentQuantizer


def main():
    parser = argparse.ArgumentParser(description="VAE Encode + K-means Quantization")
    parser.add_argument('--config', type=str, default='../configs/vae_model.yaml',
                        help='VAE 模型配置文件')
    parser.add_argument('--vae-ckpt', type=str, required=True,
                        help='预训练 VAE 检查点路径')
    parser.add_argument('--input-dir', type=str, required=True,
                        help='预处理好的 mel-spectrogram 目录（.npy 文件）')
    parser.add_argument('--output-dir', type=str, required=True,
                        help='输出量化结果目录')
    parser.add_argument('--kmeans-codebook', type=str, default='../checkpoints/kmeans_codebook.joblib',
                        help='K-means 码本输出路径')
    parser.add_argument('--n-clusters', type=int, default=1024,
                        help='K-means 聚类数量（码本大小）')
    parser.add_argument('--batch-size', type=int, default=32,
                        help='批处理大小')
    parser.add_argument('--device', type=str, default='cuda',
                        help='计算设备')
    args = parser.parse_args()

    # 创建输出目录
    os.makedirs(args.output_dir, exist_ok=True)
    os.makedirs(os.path.dirname(args.kmeans_codebook), exist_ok=True)

    # 加载配置
    with open(args.config, 'r', encoding='utf-8') as f:
        config = yaml.safe_load(f)

    device = torch.device(args.device if torch.cuda.is_available() else 'cpu')
    print(f"Using device: {device}")

    # 创建 VAE 模型
    model_config = config['model']
    vae = VAE(
        in_channels=model_config.get('in_channels', 1),
        base_channels=model_config.get('base_channels', 64),
        latent_channels=model_config.get('latent_channels', 32),
        num_downsampling_layers=model_config.get('num_downsampling_layers', 4),
        dropout=model_config.get('dropout', 0.0)
    ).to(device)

    # 加载检查点
    checkpoint = torch.load(args.vae_ckpt, map_location=device)
    if 'model_state_dict' in checkpoint:
        vae.load_state_dict(checkpoint['model_state_dict'])
    else:
        vae.load_state_dict(checkpoint)
    vae.eval()
    print(f"Loaded VAE checkpoint from {args.vae_ckpt}")

    # 收集所有 .npy 文件
    npy_files = []
    for root, _, files in os.walk(args.input_dir):
        for file in files:
            if file.endswith('.npy'):
                npy_files.append(os.path.join(root, file))

    print(f"Found {len(npy_files)} .npy files")

    # Step 1: 收集所有 latents 用于拟合 K-means
    print("\nStep 1: Collecting all latents for K-means fitting...")
    all_latents = []

    with torch.no_grad():
        for npy_path in tqdm(npy_files):
            # 加载 mel [num_segments, 1, n_mels, time_frames]
            mel_np = np.load(npy_path)
            mel = torch.tensor(mel_np).to(device)

            # 对每个片段编码
            for i in range(mel.shape[0]):
                mel_segment = mel[i:i+1]  # [1, 1, n_mels, time_frames]
                mu, log_var = vae.encode(mel_segment)
                latent = vae.reparameterize(mu, log_var)  # [1, C, H, T]

                # 展平为向量，添加到集合
                # latent: [1, 32, 8, 64] → 我们对每个时间步做聚类，所以取出每个时间步
                # 每个时间步是 32 × 8 = 256 维向量
                # latent shape: [batch=1, C=32, H=8, T=64]
                # → reshape to [256, 64] → 转置 [64, 256] → 每个时间步是一个 256 维向量
                latent_reshaped = latent.permute(0, 3, 1, 2).reshape(
                    latent.shape[3], -1
                )  # [T, 256]
                all_latents.append(latent_reshaped.cpu().numpy())

    # 拼接所有向量
    all_latents = np.concatenate(all_latents, axis=0)
    print(f"Collected {all_latents.shape[0]} vectors, each with dimension {all_latents.shape[1]}")

    # Step 2: 拟合 K-means
    print(f"\nStep 2: Fitting K-means with {args.n_clusters} clusters...")
    quantizer = LatentQuantizer(n_clusters=args.n_clusters)
    quantizer.fit(all_latents)

    # 保存码本
    joblib.dump(quantizer, args.kmeans_codebook)
    print(f"Saved K-means codebook to {args.kmeans_codebook}")

    # Step 3: 量化每个文件保存语义 tokens
    print("\nStep 3: Quantizing all files...")
    token_counts = []

    for npy_path in tqdm(npy_files):
        # 加载 mel
        mel_np = np.load(npy_path)
        mel = torch.tensor(mel_np).to(device)

        all_tokens = []

        with torch.no_grad():
            for i in range(mel.shape[0]):
                mel_segment = mel[i:i+1]
                mu, log_var = vae.encode(mel_segment)
                latent = vae.reparameterize(mu, log_var)

                # 量化得到 tokens
                # latent: [1, 32, 8, 64]
                tokens = quantizer.quantize_latent(latent)  # [T]
                all_tokens.extend(tokens.tolist())

        # 保存量化结果
        base_name = os.path.splitext(os.path.basename(npy_path))[0]
        output_path = os.path.join(args.output_dir, f"{base_name}_tokens.npy")
        np.save(output_path, np.array(all_tokens))
        token_counts.append(len(all_tokens))

    print(f"\nDone! Quantized {len(npy_files)} files")
    print(f"Average tokens per file: {np.mean(token_counts):.1f}")
    print(f"Output saved to {args.output_dir}")


if __name__ == '__main__':
    main()

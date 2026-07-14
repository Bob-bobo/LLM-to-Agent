#!/usr/bin/env python
"""
从 MusicCaps 或其他文本-音乐配对数据集构建 AR 训练所需的 metadata JSON
输出格式:
[
  {
    "ytid": "xxxxxxxx",
    "text": "A peaceful piano melody",
    "semantic_tokens": [12, 45, 789, ...]
  },
  ...
]
"""
import os
import argparse
import json
import pandas as pd
import numpy as np
from sklearn.model_selection import train_test_split


def main():
    parser = argparse.ArgumentParser(description="Build metadata JSON for AR training")
    parser.add_argument('--musiccaps-csv', type=str, required=True,
                        help='MusicCaps CSV 文件路径')
    parser.add_argument('--tokens-dir', type=str, required=True,
                        help='量化后的语义 tokens 目录（.npy 文件）')
    parser.add_argument('--output', type=str, required=True,
                        help='输出 metadata JSON 路径')
    parser.add_argument('--val-split', type=float, default=0.1,
                        help='验证集比例')
    parser.add_argument('--seed', type=int, default=42,
                        help='随机种子')
    parser.add_argument('--filename-prefix', type=str, default='',
                        help='文件名前缀，如果你的文件名是 ytid + 扩展名，留空')
    args = parser.parse_args()

    # 读取 MusicCaps CSV
    print(f"Reading MusicCaps from {args.musiccaps_csv}")
    df = pd.read_csv(args.musiccaps_csv)
    print(f"Total {len(df)} entries")

    # 遍历每个样本，找到对应的 tokens 文件
    metadata = []
    missing = 0

    for idx, row in df.iterrows():
        ytid = row['ytid']
        caption = row['caption']  # 文本描述

        # tokens 文件名
        filename = f"{args.filename_prefix}{ytid}_tokens.npy"
        tokens_path = os.path.join(args.tokens_dir, filename)

        if not os.path.exists(tokens_path):
            missing += 1
            continue

        # 加载 tokens
        tokens = np.load(tokens_path)
        tokens_list = tokens.tolist()

        metadata.append({
            'ytid': ytid,
            'text': caption,
            'semantic_tokens': tokens_list
        })

    print(f"Found {len(metadata)} valid entries, {missing} missing")

    # 分割训练验证
    if args.val_split > 0:
        train_data, val_data = train_test_split(
            metadata,
            test_size=args.val_split,
            random_state=args.seed
        )
        print(f"Train: {len(train_data)}, Val: {len(val_data)}")

        # 保存训练
        base, ext = os.path.splitext(args.output)
        train_output = f"{base}_train{ext}"
        val_output = f"{base}_val{ext}"

        with open(train_output, 'w', encoding='utf-8') as f:
            json.dump(train_data, f, indent=2, ensure_ascii=False)
        with open(val_output, 'w', encoding='utf-8') as f:
            json.dump(val_data, f, indent=2, ensure_ascii=False)

        print(f"Saved train metadata to {train_output}")
        print(f"Saved val metadata to {val_output}")
    else:
        # 不分割，全部保存
        with open(args.output, 'w', encoding='utf-8') as f:
            json.dump(metadata, f, indent=2, ensure_ascii=False)
        print(f"Saved metadata to {args.output}")


if __name__ == '__main__':
    main()

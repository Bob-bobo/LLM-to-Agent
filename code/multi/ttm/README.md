# TTM: Text-to-Music Generation with DiT and AR

基于 **VAE + 扩散 (DiT) + AR 大语言模型 (Qwen3)** 的三阶段文本到音乐生成实现。

## 整体架构

这个项目实现了一个三阶段的文本到音乐生成pipeline：

1. **VAE (变分自编码器)** - 将 mel-spectrogram 压缩到低维 latent 空间
   - 输入: `[B, 1, 128, 1024]` (batch, channel, n_mels, time_frames)
   - 输出: `[B, 32, 8, 64]` (latent_channels, n_mels/16, time/16)
   - 压缩比: 16×

2. **DiT (Diffusion Transformer)** - 在 VAE latent 空间基于语义条件生成音乐
   - 输入: 语义 tokens 条件 + 随机噪声
   - 输出: VAE latent
   - 使用 1D Diffusion Transformer 适配音乐序列
   - 支持 Classifier-Free Guidance

3. **AR Qwen3 (自回归大语言模型)** - 从文本描述生成语义 tokens
   - 输入: 文本描述
   - 输出: 语义 token 序列 (K-means 聚类得到的离散码本)
   - 使用 LoRA 进行参数高效微调
   - 扩展 Qwen3 词表加入语义 tokens

4. **声码器** - 从 mel-spectrogram 重建音频波形
   - 默认: Griffin-Lim 算法（不需要训练）
   - 支持: HiFi-GAN 接口（需要预训练模型）

## 项目结构

```
ttm/
├── configs/                # 配置文件
│   ├── vae_model.yaml     # VAE 训练配置
│   ├── dit_model.yaml     # DiT 训练配置
│   ├── ar_model.yaml      # AR 微调配置
│   ├── preprocessing.yaml # 音频预处理配置
│   └── inference.yaml     # 推理配置
├── data/                  # 数据处理
│   ├── preprocessing.py   # 音频预处理（提取 mel-spectrogram）
│   ├── dataset.py         # PyTorch Dataset 实现
│   ├── tokenizer.py       # 文本 tokenizer + K-means 量化
│   └── utils.py
├── models/                # 模型定义
│   ├── vae/               # VAE
│   │   ├── encoder.py
│   │   ├── decoder.py
│   │   └── vae.py
│   ├── dit/               # DiT 扩散模型
│   │   ├── embeddings.py
│   │   ├── blocks.py
│   │   ├── dit.py
│   │   └── diffusion.py
│   └── ar/                # AR 自回归模型
│       ├── qwen3_wrapper.py
│       └── ar_model.py
├── training/              # 训练脚本
│   ├── trainer.py         # 基础 Trainer
│   ├── train_vae.py       # VAE 预训练
│   ├── train_dit.py       # DiT 训练
│   └── train_ar.py        # AR 微调
├── inference/             # 推理
│   ├── text_to_music.py   # 端到端 pipeline
│   └── run_inference.py   # 推理脚本示例
├── vocoder/               # 声码器
│   ├── griffin_lim.py     # Griffin-Lim 算法
│   └── hifigan.py         # HiFi-GAN 包装
└── utils/
    └── logger.py          # 日志工具
```

## 训练流程

### 1. 数据准备

```bash
# 预处理音频，提取 mel-spectrogram
python scripts/preprocess.py \
    --input-dir data_raw/ \
    --output-dir data_processed/ \
    --config configs/preprocessing.yaml
```

### 2. 预训练 VAE

```bash
cd training
python train_vae.py \
    --config ../configs/vae_model.yaml \
    --train-data ../data_processed/train \
    --val-data ../data_processed/val
```

### 3. K-means 量化（准备 AR 训练数据）

训练完 VAE 后，对所有 latent 进行 K-means 聚类得到语义码本：

```python
from data.tokenizer import LatentQuantizer, collect_latents_for_kmeans

# 收集 latents
latents = collect_latents_for_kmeans(vae, dataloader, num_samples=100000)

# 训练 K-means
quantizer = LatentQuantizer(num_codebook=1024)
quantizer.fit(latents)
quantizer.save("checkpoints/kmeans_1024.joblib")
```

### 4. 训练 DiT

```bash
cd training
python -m torch.distributed.run --nproc_per_node=NUM_GPUS train_dit.py \
    --config ../configs/dit_model.yaml \
    --data-path ../data_processed/train \
    --vae-ckpt ../checkpoints/vae_best.pt \
    --results-dir ../results
```

DiT 支持分布式训练（DDP），使用 EMA 权重更新。

### 5. 微调 AR Qwen3

```bash
cd training
python train_ar.py \
    --config ../configs/ar_model.yaml \
    --train-metadata metadata/train.json \
    --val-metadata metadata/val.json \
    --load-in-4bit
```

使用 LoRA 微调，支持 4bit/8bit 量化。

元数据 JSON 格式示例：
```json
[
  {
    "text": "A peaceful piano melody with gentle chords",
    "semantic_tokens": [12, 45, 233, ...]
  }
]
```

## 推理

使用训练好的模型进行端到端文本到音乐生成：

```bash
cd inference
python run_inference.py \
    --config ../configs/inference.yaml \
    --vae-ckpt ../checkpoints/vae_best.pt \
    --dit-ckpt ../checkpoints/dit_final.pt \
    --ar-ckpt ../checkpoints/ar_best.pt \
    --prompt "A calm ambient music with soft synth pads" \
    --output output.wav \
    --cfg-scale 2.0 \
    --temperature 0.8
```

### Python API

```python
from inference.text_to_music import TextToMusicPipeline

pipeline = TextToMusicPipeline(
    config_path="configs/inference.yaml",
    vae_ckpt_path="checkpoints/vae_best.pt",
    dit_ckpt_path="checkpoints/dit_final.pt",
    ar_ckpt_path="checkpoints/ar_best.pt",
    device="cuda"
)

waveform, mel = pipeline.generate(
    text_prompt="An upbeat electronic dance beat",
    max_semantic_tokens=64,
    temperature=0.8,
    cfg_scale=2.0,
    num_inference_steps=50
)

pipeline.save_audio(waveform, "output.wav")
```

## 配置说明

### 模型大小选择

DiT 模型变体：
- `DiT-S/2`: depth=12, hidden_size=384, 约 50M 参数
- `DiT-B/2`: depth=12, hidden_size=768, 约 150M 参数（默认）
- `DiT-L/2`: depth=24, hidden_size=1024, 约 300M 参数
- `DiT-XL/2`: depth=28, hidden_size=1152, 约 450M 参数

AR 模型：
- 默认使用 Qwen3-7B，LoRA 只微调几M参数

## 依赖

```
torch
torchaudio
librosa
numpy
scikit-learn
joblib
tqdm
pyyaml
transformers
peft
timm
soundfile
```

## 论文参考

- DiT: [Scalable Diffusion Models with Transformers](https://arxiv.org/abs/2212.09748)
- VAE: [Auto-Encoding Variational Bayes](https://arxiv.org/abs/1312.6115)
- LoRA: [LoRA: Low-Rank Adaptation of Large Language Models](https://arxiv.org/abs/2106.09685)

## License

MIT

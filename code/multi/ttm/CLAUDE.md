# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Overview

TTM (Text-to-Music) is a three-stage text-to-music generation project using:
1. **VAE** - Compresses mel-spectrogram into a low-dimensional latent space
2. **DiT (Diffusion Transformer)** - Generates latents conditioned on semantic tokens
3. **AR Qwen3** - Autoregressive generates semantic tokens from text prompts
4. **Vocoder** - Converts mel-spectrogram back to audio waveform

## Code Architecture

```
ttm/
├── configs/              # YAML configuration files
│   ├── vae_model.yaml    # VAE training
│   ├── dit_model.yaml    # DiT diffusion training
│   ├── ar_model.yaml     # AR Qwen3 LoRA fine-tuning
│   ├── inference.yaml    # End-to-end inference
│   └── preprocessing.yaml # Audio preprocessing
├── data/
│   ├── preprocessing.py  # Audio → mel-spectrogram, SpecAugment
│   ├── dataset.py       # Dataset implementations (MelDataset, ARTextTokenDataset)
│   └── tokenizer.py      # K-means quantization for semantic tokens
├── models/
│   ├── vae/              # VAE with residual down/up blocks
│   ├── dit/              # 1D Diffusion Transformer (adapted from official DiT)
│   │   ├── dit.py        # DiT model with CFG support
│   │   ├── diffusion.py  # Gaussian diffusion training/sampling
│   │   ├── embeddings.py # Timestep + semantic token embeddings
│   │   └── blocks.py     # DiT blocks with adaLN-Zero modulation
│   └── ar/               # Qwen3 wrapper with LoRA
├── training/
│   ├── trainer.py        # Base trainer with logging/checkpointing
│   ├── train_vae.py      # VAE pre-training script
│   ├── train_dit.py      # DiT distributed training with DDP + EMA
│   └── train_ar.py       # AR Qwen3 LoRA fine-tuning
├── inference/
│   ├── text_to_music.py  # End-to-end TextToMusicPipeline
│   └── run_inference.py  # CLI inference script
├── vocoder/
│   ├── griffin_lim.py    # Griffin-Lim algorithm (no training needed)
│   └── hifigan.py        # HiFi-GAN wrapper for higher quality
└── utils/
    └── logger.py         # Scalar logging
```

## Key Design Points

- **VAE**: 4-layer residual downsampling encoder, 4-layer residual upsampling decoder, output mu and log_var for reparameterization
- **DiT**: 1D adaptation of Diffusion Transformer, processes [B, C, T] latent sequences, uses adaptive layer norm zero (adaLN-Zero) for conditioning
- **AR**: Extends Qwen3 vocabulary with `num_semantic_tokens` new tokens, only trains LoRA adapters, keeps base model frozen
- **Diffusion**: Supports DDPM/DDIM sampling, classifier-free guidance with configurable strength

## Common Commands

### Data Preprocessing

```bash
cd scripts
python preprocess.py \
    --config ../configs/preprocessing.yaml \
    --input-dir ../data_raw/train \
    --output-dir ../data_processed/train
```

### Training

**VAE**:
```bash
cd training
python train_vae.py \
    --config ../configs/vae_model.yaml \
    --train-data ../data_processed/train \
    --val-data ../data_processed/val
```

**DiT (distributed)**:
```bash
cd training
torchrun --nproc_per_node=NUM_GPUS train_dit.py \
    --config ../configs/dit_model.yaml \
    --data-path ../data_processed/train \
    --vae-ckpt ../checkpoints/vae_best.pt \
    --results-dir ../results
```

**AR LoRA fine-tuning**:
```bash
cd training
python train_ar.py \
    --config ../configs/ar_model.yaml \
    --train-metadata ../metadata/train.json \
    --val-metadata ../metadata/val.json \
    --load-in-4bit  # for memory efficiency
```

### Inference

```bash
cd inference
python run_inference.py \
    --config ../configs/inference.yaml \
    --vae-ckpt ../checkpoints/vae_best.pt \
    --dit-ckpt ../checkpoints/dit_final.pt \
    --ar-ckpt ../checkpoints/ar_best.pt \
    --prompt "A peaceful piano melody" \
    --output output.wav \
    --cfg-scale 2.0 \
    --temperature 0.8
```

## Important Notes

- VAE compresses mel-spectrogram `[1, 128, 1024]` → `[32, 8, 64]` (16× compression)
- DiT reshapes VAE output to `[B, 256, 64]` for 1D processing (256 = 32 × 8)
- AR model input format: `<text>BOS description EOS token1 token2 ... tokenN EOS`
- Semantic tokens are obtained by K-means clustering on VAE latents after VAE pre-training
- K-means codebook size defaults to 1024, stored as a separate joblib file

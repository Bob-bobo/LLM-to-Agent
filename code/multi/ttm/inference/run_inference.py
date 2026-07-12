#!/usr/bin/env python
"""
示例推理脚本：文本到音乐生成
用法:
    python run_inference.py \
        --config ../configs/inference.yaml \
        --vae-ckpt ../checkpoints/vae_best.pt \
        --dit-ckpt ../checkpoints/dit_0000100.pt \
        --ar-ckpt ../checkpoints/ar_best.pt \
        --prompt "A peaceful piano melody with gentle chords" \
        --output output.wav \
        --cfg-scale 2.0 \
        --temperature 0.8
"""
import os
import argparse
import torch
from text_to_music import TextToMusicPipeline


def main():
    parser = argparse.ArgumentParser(description="Text-to-Music Generation Inference")
    parser.add_argument('--config', type=str, default='../configs/inference.yaml',
                        help='推理配置文件路径')
    parser.add_argument('--vae-ckpt', type=str, required=True,
                        help='VAE 检查点路径')
    parser.add_argument('--dit-ckpt', type=str, required=True,
                        help='DiT 检查点路径')
    parser.add_argument('--ar-ckpt', type=str, default=None,
                        help='AR LoRA 检查点路径（可选，无条件生成不需要）')
    parser.add_argument('--prompt', type=str, default=None,
                        help='文本提示（如果不提供则无条件生成）')
    parser.add_argument('--output', type=str, default='output.wav',
                        help='输出音频路径')
    parser.add_argument('--device', type=str, default='cuda',
                        help='计算设备')
    parser.add_argument('--vocoder', type=str, default='griffin_lim',
                        choices=['griffin_lim', 'hifigan'],
                        help='声码器类型')
    parser.add_argument('--vocoder-ckpt', type=str, default=None,
                        help='HiFi-GAN 检查点路径')
    parser.add_argument('--cfg-scale', type=float, default=2.0,
                        help='Classifier-free guidance 强度')
    parser.add_argument('--temperature', type=float, default=0.8,
                        help='AR 采样温度')
    parser.add_argument('--top-p', type=float, default=0.9,
                        help='AR top-p 采样')
    parser.add_argument('--max-tokens', type=int, default=64,
                        help='最大语义 token 数（控制生成长度）')
    parser.add_argument('--num-steps', type=int, default=50,
                        help='DiT 采样步数')
    args = parser.parse_args()

    # 创建 pipeline
    pipeline = TextToMusicPipeline(
        config_path=args.config,
        vae_ckpt_path=args.vae_ckpt,
        dit_ckpt_path=args.dit_ckpt,
        ar_ckpt_path=args.ar_ckpt,
        device=args.device,
        vocoder_type=args.vocoder,
        vocoder_ckpt_path=args.vocoder_ckpt
    )

    # 生成
    if args.prompt is not None:
        waveform, mel = pipeline.generate(
            text_prompt=args.prompt,
            max_semantic_tokens=args.max_tokens,
            temperature=args.temperature,
            top_p=args.top_p,
            cfg_scale=args.cfg_scale,
            num_inference_steps=args.num_steps
        )
    else:
        print("No prompt provided, generating unconditional music...")
        waveform, mel = pipeline.generate_unconditional(
            cfg_scale=args.cfg_scale,
            num_inference_steps=args.num_steps
        )

    # 保存
    pipeline.save_audio(waveform, args.output)
    print(f"Done! Output saved to {args.output}")


if __name__ == '__main__':
    main()

# LLM-to-Agent

<div align="center">

[![GitHub stars](https://img.shields.io/github/stars/yourusername/awesome-llm-learning?style=social)](https://github.com/yourusername/awesome-llm-learning/stargazers)
[![GitHub forks](https://img.shields.io/github/forks/yourusername/awesome-llm-learning?style=social)](https://github.com/yourusername/awesome-llm-learning/network/members)
[![License](https://img.shields.io/badge/License-MIT-blue.svg)](https://opensource.org/licenses/MIT)
[![PRs Welcome](https://img.shields.io/badge/PRs-welcome-brightgreen.svg)](CONTRIBUTING.md)
[![Last Commit](https://img.shields.io/github/last-commit/yourusername/awesome-llm-learning/main)](https://github.com/yourusername/awesome-llm-learning/commits/main)

> **大模型（LLM & Multimodal）到Agent的学习路线、实战经验与面试指南**  
> 从入门到进阶，从理论到实践，一起探索大模型的技术边界。

[English](./README.md) · [简体中文](./README_CN.md) · [快速导航](#内容概览) · [贡献指南](./CONTRIBUTING.md)

</div>

---

## 📖 项目介绍

### 为什么存在这个项目

大模型（Large Language Model / Multimodal Model）正在深刻改变人工智能的落地范式。然而：

- **学习路线模糊**：资料繁杂、迭代迅速，容易陷入碎片化学习或"从入门到放弃"的困境
- **实践门槛高**：从模型训练到推理部署，涉及分布式系统、加速优化、MLOps 等多方面知识
- **面试缺少参考**：大模型相关岗位（算法/工程/Infra）缺乏系统的面试经验分享
- **从大语言到Agent工程化资料少**：缺少以工程化思维来解决大模型部署架构以及稳定性的资料解析
> 本项目旨在构建一个 **结构化的大模型学习社区**，汇集：
> - 系统性的学习路线与核心知识体系
> - 真实的一线实战经验与踩坑记录
> - 大模型岗位（训练/推理/Infra）的面试题库与经验分享
> - 基于社区交流迸发的 Infra 组件与工具实现

### 核心特色

| 特色 | 说明 |
|------|------|
| 🔰 **系统学习** | 从理论到实践，覆盖 LLM/多模态全链路知识体系 |
| 💼 **面试指南** | 整理大模型相关岗位的真实面试经验与高频考点 |
| 🛠️ **Infra 组件** | 孵化基于实践需求的推理优化、训练加速等工具 |
| 🤝 **社区共建** | 开源协作，欢迎每一个人的贡献与分享 |

---

## <a name="content-overview"></a>📚 内容概览

```
awesome-llm-learning/
├── 📖 learning-paths/          # 学习路线
│   ├── LLM-fundamentals/       # LLM 基础理论
│   ├── multimodal/            # 多模态模型
│   ├── training/              # 模型训练
│   ├── inference/             # 模型推理与部署
│   └── infra/                 # 大模型 Infra
├── 💼 interview/              # 面试经验
│   ├── algorithm/             # 算法岗位
│   ├── engineering/           # 工程岗位
│   └── infra/                 # Infra 岗位
├── 🛠️ tools/                  # Infra 工具组件
│   ├── inference-optimization/# 推理优化
│   ├── training-optimization/ # 训练加速
│   └── evaluation/            # 模型评测
├── 📝 notes/                  # 实战笔记
├── 📚 resources/              # 学习资源
└── CONTRIBUTING.md            # 贡献指南
```

---

## 🗺️ 学习路线

### 大模型基础路线

```
┌─────────────────────────────────────────────────────────────────┐
│                        基础知识储备                              │
├─────────────────────────────────────────────────────────────────┤
│  Python/C++ · 机器学习基础 · 深度学习基础 · 分布式计算             │
└─────────────────────────────────────────────────────────────────┘
                                  │
                                  ▼
┌─────────────────────────────────────────────────────────────────┐
│                       LLM 核心知识                               │
├─────────────────────────────────────────────────────────────────┤
│  Transformer 架构 · Attention 机制 · Tokenizer · GPT/BERT/LLaMA  │
└─────────────────────────────────────────────────────────────────┘
                                  │
                    ┌─────────────┼─────────────┐
                    ▼             ▼             ▼
          ┌──────────────┐ ┌─────────────┐ ┌──────────────┐
          │   模型训练    │ │   模型推理  │ │   多模态      │
          ├──────────────┤ ├─────────────┤ ├──────────────┤
          │ Pre-training │ │ 量化压缩     │ │  Visual LM   │
          │ SFT/RLHF     │ │ 推理优化     │ │  Audio LM    │
          │ PEFT         │ │ 部署架构     │ │  Fusion      │
          └──────────────┘ └─────────────┘ └──────────────┘
                    │             │             │
                    └─────────────┼─────────────┘
                                  ▼
┌──────────────────────────────────────────────────────────────────┐
│                        大模型 Infra                               │
├──────────────────────────────────────────────────────────────────┤
│  分布式训练（张量并行/流水线并行）· 推理加速（Triton/FlashAttention）│
│  模型服务化 · MLOps · 云原生部署                                   │
└──────────────────────────────────────────────────────────────────┘
```

### 推荐学习阶段

#### Stage 1: 入门基础（1-2 个月）

| 模块 | 内容 | 推荐资源 |
|------|------|----------|
| Python 进阶 | 面向对象、装饰器、异步编程 | [Python 官方文档](https://docs.python.org/3/) |
| 深度学习基础 | CNN/RNN/优化器/Loss | 吴恩达深度学习课程 |
| Transformers | Attention / Self-Attention 原理 | [Attention Is All You Need](https://arxiv.org/abs/1706.03762) |
| LLM 基础 | GPT 系列 / BERT / LLaMA 架构 | [Andrej Karpathy 课程](https://karpathy.ai/zero-to-hero/) |

#### Stage 2: 进阶实战（2-3 个月）

| 模块 | 内容 | 推荐资源 |
|------|------|----------|
| 模型训练 | Pre-training / SFT / RLHF / LoRA | [HuggingFace 文档](https://huggingface.co/docs) |
| 推理优化 | INT8/FP8 量化、KV Cache、Continuous Batching | [vLLM](https://github.com/vllm-project/vllm) |
| 分布式训练 | DeepSpeed / Megatron-LM / FSDP | [DeepSpeed Examples](https://github.com/microsoft/DeepSpeed) |
| 多模态 | CLIP / BLIP / LLaVA / SD | [多模态论文列表](https://github.com/BradyFU/Awesome-Multimodal-Large-Language-Models) |

#### Stage 3: 专项深入（持续）

- **算法方向**：模型架构创新、长上下文建模、高效注意力
- **工程方向**：推理引擎开发、硬件加速、自研框架
- **Infra 方向**：超大规模分布式训练、异构计算、MLOps 平台

---

## 💼 面试经验

### 常见岗位与考察重点

| 岗位方向 | 核心考察点 | 占比 |
|----------|-----------|------|
| **LLM 算法工程师** | 模型架构理解 / 训练技巧 / 论文理解能力 | 40% |
| **大模型 Infra** | 分布式训练 / 推理优化 / 系统设计 | 50% |
| **多模态算法** | 视觉/语言基础 / 跨模态对齐 / 训练策略 | 45% |
| **模型部署工程师** | 量化压缩 / 推理引擎 / CUDA 优化 | 55% |

### 面试题库精选

<details>
<summary><b>基础理论（必考）</b></summary>

- [ ] Transformer 的核心组件有哪些？Self-Attention 的计算复杂度？
- [ ] LLaMA 相比 GPT 系列做了哪些改进？
- [ ] RLHF 的三个阶段（PPO/SFT/Reward Model）各自的作用？
- [ ] LoRA 的核心思想和参数计算？
- [ ] 什么是 KV Cache？为什么能加速推理？
- [ ] BF16 vs FP16 vs FP32 的区别和使用场景？

</details>

<details>
<summary><b>工程实践（高频）</b></summary>

- [ ] 如何在有限显存下微调百亿参数模型？
- [ ] DeepSpeed ZeRO 的三种Stage优化策略？
- [ ] 解释 Continuous Batching 和 Static Batching 的区别？
- [ ] 如何做 INT8 量化？Post-Training Quantization vs QAT？
- [ ] CUDA 编程中，如何优化矩阵乘法 GEMM？
- [ ] Flash Attention 的核心优化点是什么？

</details>

<details>
<summary><b>系统设计（进阶）</b></summary>

- [ ] 设计一个支持千亿参数模型推理的系统架构
- [ ] 如何设计一个高效的模型评测平台？
- [ ] 当 GPU 显存不足时，有哪些解决思路？
- [ ] 如何保证分布式训练中的负载均衡？

</details>

> 📎 查看完整面试题库：[interview/README.md](./interview/README.md)

---

## 🛠️ Infra 组件

基于社区实践需求，我们正在孵化的工具组件：

| 组件 | 描述 | 状态 |
|------|------|------|
| **llm-serve** | 轻量级 LLM 推理服务框架 | 🚧 开发中 |
| **quant-toolkit** | 量化训练与评估工具集 | 🚧 开发中 |
| **train-benchmark** | 分布式训练性能评测工具 | 📋 规划中 |
| **prompt-eval** | Prompt 效果评测平台 | 📋 规划中 |

> ⚠️ 组件仍在积极开发中，欢迎贡献！

---

## 📂 快速导航

| 分类 | 内容 |
|------|------|
| [LLM 基础理论](./learning-paths/LLM-fundamentals/) | Transformer / Attention / 主流模型架构 |
| [多模态学习](./learning-paths/multimodal/) | Vision-Language / Audio-Language / 多模态融合 |
| [模型训练](./learning-paths/training/) | Pre-training / SFT / RLHF / PEFT |
| [推理部署](./learning-paths/inference/) | 量化 / 优化 / 服务化 / 硬件加速 |
| [Infra 实践](./learning-paths/infra/) | 分布式训练 / 推理引擎 / MLOps |
| [面试经验](./interview/) | 岗位面经 / 题库 / 系统设计 |

---

## 🤝 参与贡献

我们欢迎每一位热爱大模型技术的同学参与贡献！

### 贡献方式

- 📝 **完善文档**：修正错误、补充内容、改进表达
- 🔧 **贡献代码**：开发 Infra 工具组件、修复 Bug
- 📚 **分享经验**：提交面试经验、学习心得
- 🐛 **报告问题**：发现文档错误或内容过时请提交 Issue

### 贡献流程

```bash
# 1. Fork 本仓库
# 2. 创建你的分支
git checkout -b feature/your-feature-name

# 3. 提交你的更改
git commit -m "feat: add xxx"

# 4. 推送到你的分支
git push origin feature/your-feature-name

# 5. 创建 Pull Request
```

> 📖 详细贡献指南：[CONTRIBUTING.md](./CONTRIBUTING.md)

---

## 📜 开源协议

本项目基于 [MIT License](./LICENSE) 开源，你可以：

- ✅ 自由使用、修改、分享本项目内容
- ✅ 商业用途
- ✅ 合并、发行、传播

需遵守：

- ⚠️ 引用来源
- ⚠️ 明确标注修改内容
- ⚠️ 在相同协议下分发

---

## 🙏 致谢

本项目受以下开源项目启发：

- [Awesome-LLM](https://github.com/Hannibal046/Awesome-LLM) - LLM 资源汇总
- [LLMReadingList](https://github.com/Zacci/LLMReadingList) - LLM 论文阅读列表
- [transformers](https://github.com/huggingface/transformers) - Hugging Face Transformers
- [DeepSpeed](https://github.com/microsoft/DeepSpeed) - 深度学习优化库
- [vLLM](https://github.com/vllm-project/vllm) - 高效 LLM 推理引擎

---

## 📬 联系方式

- 🐛 问题反馈：[GitHub Issues](https://github.com/Bob-bobo/lm-Interview/issues)
- 💬 讨论交流：[GitHub Discussions]()
- 📧 邮件联系：1297802531@qq.com

---

<div align="center">

**如果这个项目对你有帮助，请给我们一个 ⭐**

*Built by Bowen.❤️*

</div>

# LLM-to-Agent

<div align="center">

[![GitHub stars](https://github.com/Bob-bobo/LLM-to-Agent?style=social)](https://github.com/Bob-bobo/LLM-to-Agent/stargazers)
[![GitHub forks](https://github.com/Bob-bobo/LLM-to-Agent?style=social)](https://github.com/Bob-bobo/LLM-to-Agent/network/members)
[![License](https://img.shields.io/badge/License-MIT-blue.svg)](https://opensource.org/licenses/MIT)
[![PRs Welcome](https://img.shields.io/badge/PRs-welcome-brightgreen.svg)](CONTRIBUTING.md)
[![Last Commit](https://img.shields.io/github/last-commit/yourusername/awesome-llm-learning/main)](https://github.com/yourusername/awesome-llm-learning/commits/main)

> **Learning Route, Practical Experience, and Interview Guide from Large Models (LLM & Multimodal) to Agents**
> From beginner to advanced, from theory to practice, let's explore the technical boundaries of large models together.

[Simplified Chinese](./README.md) · [English](README_EN.md) · [Quick Navigation](#content-overview) · [Contribution Guide](./CONTRIBUTING.md)

</div>

---

## 📖 Project Introduction

### Why This Project Exists

Large Models (Large Language Models / Multimodal Models) are profoundly changing the paradigm of Artificial Intelligence implementation. However:

- **Vague Learning Path**: Abundant and rapidly evolving resources can lead to fragmented learning or the "giving up after starting" dilemma.
- **High Barrier to Practice**: From model training to inference deployment, it involves knowledge in distributed systems, acceleration optimization, MLOps, and more.
- **Lack of Interview References**: There's a scarcity of systematic interview experience sharing for large model-related positions (Algorithm/Engineering/Infra).
- **Limited Resources for Large Language to Agent Engineering**: There's a lack of material analysis that approaches large model deployment architecture and stability from an engineering perspective.
> This project aims to build a **structured learning community for large models**, gathering:
> - Systematic learning paths and core knowledge systems.
> - Real-world, frontline practical experience and pitfall records.
> - Interview question banks and experience sharing for large model positions (training/inference/Infra).
> - Infra components and tool implementations sparked by community exchange.

### Core Features

| Feature | Description |
|------|------|
| 🔰 **Systematic Learning** | From theory to practice, covering the entire LLM/multimodal knowledge chain. |
| 💼 **Interview Guide** | Compiled real interview experiences and high-frequency exam points for large model-related positions. |
| 🛠️ **Infra Components** | Incubating tools for inference optimization, training acceleration, etc., based on practical needs. |
| 🤝 **Community Co-building** | Open-source collaboration, welcoming contributions and sharing from everyone. |

---

## <a name="content-overview"></a>📚 Content Overview

```
awesome-llm-learning/
├── 📖 learning-paths/          # Learning Paths
│   ├── LLM-fundamentals/       # LLM Fundamentals
│   ├── multimodal/            # Multimodal Models
│   ├── training/              # Model Training
│   ├── inference/             # Model Inference and Deployment
│   └── infra/                 # Large Model Infra
├── 💼 interview/              # Interview Experience
│   ├── algorithm/             # Algorithm Positions
│   ├── engineering/           # Engineering Positions
│   └── infra/                 # Infra Positions
├── 🛠️ tools/                  # Infra Tool Components
│   ├── inference-optimization/# Inference Optimization
│   ├── training-optimization/ # Training Acceleration
│   └── evaluation/            # Model Evaluation
├── 📝 notes/                  # Practical Notes
├── 📚 resources/              # Learning Resources
└── CONTRIBUTING.md            # Contribution Guide
```

---

## 🗺️ Learning Path

### Large Model Fundamentals Path

```
┌─────────────────────────────────────────────────────────────────┐
│                        Foundational Knowledge                     │
├─────────────────────────────────────────────────────────────────┤
│  Python/C++ · Machine Learning Basics · Deep Learning Basics · Distributed Computing             │
└─────────────────────────────────────────────────────────────────┘
                                  │
                                  ▼
┌─────────────────────────────────────────────────────────────────┐
│                       LLM Core Knowledge                          │
├─────────────────────────────────────────────────────────────────┤
│  Transformer Architecture · Attention Mechanism · Tokenizer · GPT/BERT/LLaMA  │
└─────────────────────────────────────────────────────────────────┘
                                  │
                    ┌─────────────┼─────────────┐
                    ▼             ▼             ▼
          ┌──────────────┐ ┌──────────────┐ ┌──────────────┐
          │ Model Training│ │ Model Inference│ │ Multimodal    │
          ├──────────────┤ ├──────────────┤ ├──────────────┤
          │ Pre-training│ │ Quantization & Compression    │ │  Visual LM   │
          │ SFT/RLHF    │ │ Inference Optimization    │ │  Audio LM   │
          │ PEFT        │ │ Deployment Architecture    │ │  Fusion      │
          └──────────────┘ └──────────────┘ └──────────────┘
                    │             │             │
                    └─────────────┼─────────────┘
                                  ▼
┌─────────────────────────────────────────────────────────────────┐
│                        Large Model Infra                          │
├─────────────────────────────────────────────────────────────────┤
│  Distributed Training (Tensor Parallelism/Pipeline Parallelism) · Inference Acceleration (Triton/FlashAttention)│
│  Model Serving · MLOps · Cloud-Native Deployment                                 │
└─────────────────────────────────────────────────────────────────┘
```

### Recommended Learning Stages

#### Stage 1: Beginner Fundamentals (1-2 Months)

| Module | Content | Recommended Resources |
|------|------|----------|
| Advanced Python | Object-Oriented Programming, Decorators, Asynchronous Programming | [Python Official Documentation](https://docs.python.org/3/) |
| Deep Learning Basics | CNN/RNN/Optimizers/Loss | Andrew Ng's Deep Learning Course |
| Transformers | Attention / Self-Attention Principles | [Attention Is All You Need](https://arxiv.org/abs/1706.03762) |
| LLM Basics | GPT Series / BERT / LLaMA Architectures | [Andrej Karpathy's Course](https://karpathy.ai/zero-to-hero/) |

#### Stage 2: Advanced Practice (2-3 Months)

| Module | Content | Recommended Resources |
|------|------|----------|
| Model Training | Pre-training / SFT / RLHF / LoRA | [HuggingFace Documentation](https://huggingface.co/docs) |
| Inference Optimization | INT8/FP8 Quantization, KV Cache, Continuous Batching | [vLLM](https://github.com/vllm-project/vllm) |
| Distributed Training | DeepSpeed / Megatron-LM / FSDP | [DeepSpeed Examples](https://github.com/microsoft/DeepSpeed) |
| Multimodal | CLIP / BLIP / LLaVA / SD | [Multimodal Paper List](https://github.com/BradyFU/Awesome-Multimodal-Large-Language-Models) |

#### Stage 3: Specialized Deep Dive (Ongoing)

- **Algorithm Direction**: Model Architecture Innovation, Long Context Modeling, Efficient Attention
- **Engineering Direction**: Inference Engine Development, Hardware Acceleration, Custom Frameworks
- **Infra Direction**: Large-Scale Distributed Training, Heterogeneous Computing, MLOps Platforms

---

## 💼 Interview Experience

### Common Positions and Key Focus Areas

| Position Direction | Core Focus Areas | Percentage |
|----------|-----------|------|
| **LLM Algorithm Engineer** | Model Architecture Understanding / Training Techniques / Paper Comprehension Ability | 40% |
| **Large Model Infra** | Distributed Training / Inference Optimization / System Design | 50% |
| **Multimodal Algorithm** | Vision/Language Fundamentals / Cross-modal Alignment / Training Strategies | 45% |
| **Model Deployment Engineer** | Quantization & Compression / Inference Engines / CUDA Optimization | 55% |

### Selected Interview Questions

<details>
<summary><b>Fundamental Theory (Mandatory)</b></summary>

- [ ] What are the core components of a Transformer? What is the computational complexity of Self-Attention?
- [ ] What improvements did LLaMA make compared to the GPT series?
- [ ] What are the roles of the three stages in RLHF (PPO/SFT/Reward Model)?
- [ ] What is the core idea and parameter calculation of LoRA?
- [ ] What is KV Cache? Why does it accelerate inference?
- [ ] What are the differences and usage scenarios between BF16, FP16, and FP32?

</details>

<details>
<summary><b>Engineering Practice (High Frequency)</b></summary>

- [ ] How to fine-tune a tens of billions parameter model with limited GPU memory?
- [ ] What are the optimization strategies of the three Stages in DeepSpeed ZeRO?
- [ ] Explain the difference between Continuous Batching and Static Batching.
- [ ] How to perform INT8 quantization? Post-Training Quantization vs QAT?
- [ ] In CUDA programming, how to optimize matrix multiplication GEMM?
- [ ] What are the core optimization points of Flash Attention?

</details>

<details>
<summary><b>System Design (Advanced)</b></summary>

- [ ] Design a system architecture that supports inference for trillion-parameter models.
- [ ] How to design an efficient model evaluation platform?
- [ ] What are the solutions when GPU memory is insufficient?
- [ ] How to ensure load balancing during distributed training?

</details>

> 📎 View the complete interview question bank: [interview/README.md](./interview/README.md)

---

## 🛠️ Infra Components

Based on community practical needs, we are incubating the following tool components:

| Component | Description | Status |
|------|------|------|
| **llm-serve** | Lightweight LLM inference service framework | 🚧 Under Development |
| **quant-toolkit** | Quantization training and evaluation toolkit | 🚧 Under Development |
| **train-benchmark** | Distributed training performance benchmarking tool | 📋 Planned |
| **prompt-eval** | Prompt effect evaluation platform | 📋 Planned |

> ⚠️ Components are still under active development, contributions are welcome!

---

## 📂 Quick Navigation

| Category | Content |
|------|------|
| [LLM Fundamentals](./learning-paths/LLM-fundamentals/) | Transformer / Attention / Mainstream Model Architectures |
| [Multimodal Learning](./learning-paths/multimodal/) | Vision-Language / Audio-Language / Multimodal Fusion |
| [Model Training](./learning-paths/training/) | Pre-training / SFT / RLHF / PEFT |
| [Inference & Deployment](./learning-paths/inference/) | Quantization / Optimization / Serving / Hardware Acceleration |
| [Infra Practice](./learning-paths/infra/) | Distributed Training / Inference Engines / MLOps |
| [Interview Experience](./interview/) | Position Interview Experiences / Question Banks / System Design |

---

## 🤝 Participate in Contributions

We welcome all students who are passionate about large model technology to participate in contributions!

### Contribution Methods

- 📝 **Improve Documentation**: Correct errors, add content, refine expressions.
- 🔧 **Contribute Code**: Develop Infra tool components, fix bugs.
- 📚 **Share Experience**: Submit interview experiences, learning insights.
- 🐛 **Report Issues**: If you find documentation errors or outdated content, please submit an Issue.

### Contribution Workflow

```bash
# 1. Fork this repository
# 2. Create your branch
git checkout -b feature/your-feature-name

# 3. Commit your changes
git commit -m "feat: add xxx"

# 4. Push to your branch
git push origin feature/your-feature-name

# 5. Create a Pull Request
```

> 📖 Detailed Contribution Guide: [CONTRIBUTING.md](./CONTRIBUTING.md)

---

## 📜 Open Source License

This project is open-sourced under the [MIT License](./LICENSE), and you are free to:

- ✅ Freely use, modify, and share the content of this project.
- ✅ For commercial use.
- ✅ Merge, distribute, and disseminate.

You must comply with:

- ⚠️ Citing the source.
- ⚠️ Clearly indicating modifications.
- ⚠️ Distributing under the same license.

---

## 🙏 Acknowledgements

This project is inspired by the following open-source projects:

- [Awesome-LLM](https://github.com/Hannibal046/Awesome-LLM) - LLM Resource Compilation
- [LLMReadingList](https://github.com/Zacci/LLMReadingList) - LLM Paper Reading List
- [transformers](https://github.com/huggingface/transformers) - Hugging Face Transformers
- [DeepSpeed](https://github.com/microsoft/DeepSpeed) - Deep Learning Optimization Library
- [vLLM](https://github.com/vllm-project/vllm) - Efficient LLM Inference Engine

---

## 📬 Contact Information

- 🐛 Issue Feedback: [GitHub Issues](https://github.com/Bob-bobo/lm-Interview/issues)
- 💬 Discussion: [GitHub Discussions]()
- 📧 Email Contact: 1297802531@qq.com

---

<div align="center">

**If this project has been helpful to you, please give us a ⭐**

*Built by Bowen.❤️*

</div>
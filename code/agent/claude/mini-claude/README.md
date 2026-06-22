# Mini Claude Code

> A minimal Claude Code clone — learn the agentic loop by doing

基于 Claude Code 官方架构原理，提炼核心设计，构建的精简版 Agent CLI 工具。

## 核心原理

```
用户输入 → 构建上下文 → LLM API 调用 → 解析工具调用 → 权限校验 → 执行工具 → 追加结果 → 循环/终止
```

这就是 **Agentic Loop（智能体循环）** 的全部。

## 快速开始

```bash
# 安装依赖
npm install

# 设置 API Key（Anthropic）
export ANTHROPIC_API_KEY=your-key

# 交互模式
npm run dev

# 管道模式
npm run dev -- -p "list all TypeScript files in src/"
```

## 🔥 多 Provider 支持

Mini Claude Code 支持 **Anthropic** 和所有 **OpenAI 兼容 API**（覆盖国内几乎所有厂商）：

### CLI 参数方式

```bash
# Anthropic Claude（默认）
export ANTHROPIC_API_KEY=sk-ant-xxx
npm run dev

# DeepSeek
export DEEPSEEK_API_KEY=sk-xxx
npm run dev -- --provider deepseek --model deepseek-chat

# 智谱 GLM
export GLM_API_KEY=xxx.xxx
npm run dev -- --provider glm --model glm-4-plus

# Moonshot (Kimi)
export MOONSHOT_API_KEY=sk-xxx
npm run dev -- --provider moonshot --model moonshot-v1-8k

# 通义千问 Qwen
export DASHSCOPE_API_KEY=sk-xxx
npm run dev -- --provider qwen --model qwen-plus

# 百川
export BAICHUAN_API_KEY=sk-xxx
npm run dev -- --provider baichuan --model Baichuan4

# 零一万物
npm run dev -- --provider yi --api-key sk-xxx --model yi-large

# 硅基流动（聚合多模型）
export SILICONFLOW_API_KEY=sk-xxx
npm run dev -- --provider siliconflow --model Qwen/Qwen2.5-72B-Instruct

# OpenAI
export OPENAI_API_KEY=sk-xxx
npm run dev -- --provider openai --model gpt-4o

# 自定义 OpenAI 兼容 API
npm run dev -- --base-url https://your-api.com/v1 --api-key your-key --model your-model
```

### 环境变量方式

```bash
# 通用环境变量（优先级最高）
export LLM_PROVIDER=deepseek    # 或 glm, moonshot, qwen, openai, anthropic
export LLM_BASE_URL=https://api.deepseek.com/v1
export LLM_API_KEY=sk-xxx
export LLM_MODEL=deepseek-chat

npm run dev
```

### 配置文件方式 `~/.mini-claude/settings.json`

```json
{
  "model": "glm-4-plus",
  "provider": {
    "type": "openai",
    "baseUrl": "https://open.bigmodel.cn/api/paas/v4",
    "apiKey": "your-glm-api-key",
    "modelMap": {
      "claude-sonnet-4-6": "glm-4-plus"
    }
  }
}
```

### Provider 预设一览

| 预设名 | 厂商 | Base URL | 环境变量 |
|--------|------|----------|----------|
| `anthropic` | Anthropic | https://api.anthropic.com | `ANTHROPIC_API_KEY` |
| `openai` | OpenAI | https://api.openai.com/v1 | `OPENAI_API_KEY` |
| `deepseek` | DeepSeek | https://api.deepseek.com/v1 | `DEEPSEEK_API_KEY` |
| `glm` | 智谱AI | https://open.bigmodel.cn/api/paas/v4 | `GLM_API_KEY` |
| `moonshot` | 月之暗面 | https://api.moonshot.cn/v1 | `MOONSHOT_API_KEY` |
| `qwen` | 阿里通义 | https://dashscope.aliyuncs.com/compatible-mode/v1 | `DASHSCOPE_API_KEY` |
| `baichuan` | 百川 | https://api.baichuan-ai.com/v1 | `BAICHUAN_API_KEY` |
| `yi` | 零一万物 | https://api.lingyiwanwu.com/v1 | - |
| `siliconflow` | 硅基流动 | https://api.siliconflow.cn/v1 | `SILICONFLOW_API_KEY` |

### 模型映射

如果内部代码中硬编码了 `claude-sonnet-4-6` 等模型名，可通过 `modelMap` 映射到实际模型：

```json
{
  "provider": {
    "type": "openai",
    "baseUrl": "https://open.bigmodel.cn/api/paas/v4",
    "modelMap": {
      "claude-sonnet-4-6": "glm-4-plus",
      "claude-opus-4-6": "glm-4-long"
    }
  }
}
```

## 内置工具

| 工具 | 用途 |
|------|------|
| **Read** | 读取文件（支持分段、目录列表） |
| **Write** | 创建/覆盖文件 |
| **Edit** | 精确字符串替换 |
| **Bash** | 执行 shell 命令 |
| **Glob** | 文件模式匹配搜索 |
| **Grep** | 文件内容正则搜索 |

## 斜杠命令

| 命令 | 说明 |
|------|------|
| `/help` | 显示帮助 |
| `/compact` | 压缩上下文 |
| `/clear` | 清除历史 |
| `/model` | 查看/切换模型 |
| `/provider` | 查看 Provider 信息 |
| `/config` | 查看配置 |
| `/context` | 查看上下文使用量 |
| `/quit` | 退出 |

## 配置

### 用户级配置 `~/.mini-claude/settings.json`

```json
{
  "model": "claude-sonnet-4-6",
  "maxTokens": 16384,
  "maxIterations": 50,
  "provider": {
    "type": "anthropic"
  },
  "permissions": {
    "allow": [
      { "tool": "Read", "pattern": "*" },
      { "tool": "Glob", pattern: "*" },
      { "tool": "Grep", "pattern": "*" }
    ],
    "deny": [
      { "tool": "Bash", "pattern": "rm -rf *" }
    ]
  }
}
```

### 项目级配置 `.mini-claude/settings.json`

项目配置覆盖用户配置。

### CLAUDE.md 指令

在项目根目录创建 `CLAUDE.md`，Mini Claude Code 会在每次会话开始时加载：

```markdown
# Project Instructions

- Use TypeScript strict mode
- Run `npm test` before committing
- API handlers live in src/api/
```

## 项目结构

```
mini-claude/
├── src/
│   ├── index.ts                   # CLI 入口
│   ├── types.ts                   # 核心类型定义（含 ProviderConfig）
│   ├── agent/
│   │   ├── loop.ts                # Agent Loop 核心循环
│   │   └── context.ts             # 上下文管理器
│   ├── api/
│   │   ├── provider.ts            # 🔥 LLM Provider 抽象接口
│   │   ├── factory.ts             # Provider 工厂 + 预设
│   │   ├── anthropic-provider.ts  # Anthropic Provider
│   │   ├── openai-provider.ts     # OpenAI-compatible Provider
│   │   ├── client.ts              # 兼容层
│   │   └── tokens.ts              # Token 估算
│   ├── tools/
│   │   ├── registry.ts            # 工具注册中心
│   │   ├── dispatcher.ts          # 工具调度器
│   │   ├── read.ts / write.ts / edit.ts / bash.ts / glob.ts / grep.ts
│   ├── permissions/
│   │   └── checker.ts             # 权限校验器
│   ├── memory/
│   │   └── loader.ts              # CLAUDE.md 加载器
│   ├── hooks/
│   │   └── runner.ts              # 钩子运行器
│   ├── config/
│   │   └── manager.ts             # 配置管理器
│   └── cli/
│       ├── repl.ts                # 交互式 REPL
│       ├── render.ts              # 输出渲染
│       └── commands.ts            # 斜杠命令
├── package.json
├── tsconfig.json
└── README.md
```

## 架构：Provider 抽象层

```
                    ┌──────────────────┐
                    │   LLMProvider    │  ← 抽象接口
                    │  (provider.ts)   │
                    │──────────────────│
                    │ + call(params)   │
                    │ + resolveModel() │
                    └────────┬─────────┘
                             │
                ┌────────────┼────────────┐
                │                         │
     ┌──────────▼──────────┐  ┌──────────▼──────────┐
     │ AnthropicProvider   │  │  OpenAIProvider     │
     │ (anthropic-         │  │  (openai-           │
     │  provider.ts)       │  │   provider.ts)     │
     │─────────────────────│  │─────────────────────│
     │ @anthropic-ai/sdk   │  │  openai sdk         │
     │                     │  │                     │
     │ Claude 系列:        │  │ DeepSeek / GLM /    │
     │ sonnet, opus, haiku │  │ Qwen / Moonshot /   │
     │                     │  │ Baichuan / Yi /     │
     │                     │  │ SiliconFlow / ...   │
     └─────────────────────┘  └─────────────────────┘
```

Agent Loop 只依赖 `LLMProvider` 接口，不关心底层是哪家 API。

## 与 Claude Code 对比

| 维度 | Claude Code | Mini Claude Code |
|------|-------------|------------------|
| 代码量 | ~100K+ 行 | ~3K 行 |
| 工具数量 | 11+ | 6 |
| 权限模式 | allow/ask/deny + auto | allow/deny |
| 配置层数 | 5 层 | 2 层 |
| Hook 类型 | 5 种 × 20+ 事件 | 1 种 × 3 事件 |
| 子代理 | 完整 | 无 |
| MCP | 完整 | 无 |
| **Provider** | **仅 Anthropic** | **Anthropic + 国内全厂商** |

## License

MIT

# Mini Claude Code — 精简版需求分析

> 基于 Claude Code 官方架构原理，提炼核心设计，构建一个可学习、可扩展的精简版 Agent CLI 工具

---

## 一、Claude Code 核心原理总结

### 1.1 整体架构

Claude Code 本质是一个 **Agentic Loop（智能体循环）** 驱动的 CLI 工具：

```
用户输入 → System Prompt 构建 → LLM API 调用 → 工具执行 → 结果反馈 → 循环/终止
```

核心循环（Agentic Loop）：
1. **构建上下文**：将 system prompt + CLAUDE.md + 历史对话 + 工具定义组装为消息
2. **调用 LLM**：发送到 Anthropic API，流式接收响应
3. **解析工具调用**：若 LLM 返回 `tool_use`，解析工具名和参数
4. **权限校验**：检查是否允许执行（allow/ask/deny 规则）
5. **执行工具**：调用对应工具实现，获取结果
6. **追加结果**：将工具结果追加到对话历史
7. **判断终止**：若 LLM 返回 `end_turn`，输出最终回复；否则回到步骤2

### 1.2 核心子系统

| 子系统 | 职责 | 关键设计 |
|--------|------|----------|
| **Agent Loop** | 驱动对话循环 | while(stop_reason !== "end_turn") 持续调用 LLM + 工具 |
| **Tool System** | 工具注册/调度/执行 | 统一接口 `execute(params) → result`，每种工具独立实现 |
| **Permission System** | 安全控制 | 三级规则：allow（自动通过）/ ask（需确认）/ deny（拒绝） |
| **Context Manager** | 上下文窗口管理 | 超限时自动压缩（compact），保留关键信息 |
| **Memory System** | 跨会话记忆 | CLAUDE.md（用户编写）+ Auto Memory（AI 自学习） |
| **Hook System** | 生命周期拦截 | PreToolUse/PostToolUse/Stop 等事件点运行自定义脚本 |
| **Config System** | 配置管理 | 多层配置优先级：Managed > CLI > Local > Project > User |
| **Subagent System** | 子代理派生 | 独立上下文窗口，受限工具集，返回摘要 |
| **MCP Integration** | 外部工具接入 | Model Context Protocol 连接外部服务 |

### 1.3 工具清单

Claude Code 内置的核心工具：

| 工具 | 用途 | 输入 | 安全级别 |
|------|------|------|----------|
| **Read** | 读取文件 | file_path, offset, limit | 低（只读） |
| **Write** | 写入文件 | file_path, content | 高（覆盖） |
| **Edit** | 精确替换 | file_path, old_string, new_string | 中（修改） |
| **Bash** | 执行命令 | command, timeout | 高（任意执行） |
| **Glob** | 文件搜索 | pattern, path | 低（只读） |
| **Grep** | 内容搜索 | pattern, path, glob | 低（只读） |
| **WebSearch** | 网页搜索 | query | 低（外部） |
| **WebFetch** | 网页获取 | url, prompt | 低（外部） |
| **Agent** | 派生子代理 | prompt, subagent_type | 中（派生） |
| **AskUserQuestion** | 询问用户 | questions | 低（交互） |
| **Monitor** | 监控输出 | command, filter | 中（长运行） |

### 1.4 权限模型

```
permissions:
  allow:  [Bash(npm test), Read(*)]     # 自动通过
  ask:    [Bash(rm *), Write(*)]        # 需用户确认
  deny:   [Bash(curl *), Read(.env)]    # 直接拒绝
```

优先级：**deny > allow > ask**，规则跨配置层合并。

### 1.5 上下文管理

- **启动时加载**：System Prompt → CLAUDE.md → Memory → 历史会话
- **运行时增长**：每轮对话追加 user/assistant/tool 消息
- **超限处理**：自动触发 compact（压缩），保留 system prompt + 最近对话 + 关键摘要
- **CLAUDE.md 加载顺序**：从文件系统根目录向下，最近的目录优先级最高

### 1.6 Hook 生命周期

```
SessionStart → UserPromptSubmit → [PreToolUse → 执行工具 → PostToolUse]* → Stop → SessionEnd
```

Hook 可执行：command（shell）、http（网络）、prompt（LLM判断）、mcp_tool（MCP调用）、agent（子代理）

---

## 二、精简版需求分析

### 2.1 设计目标

| 目标 | 说明 |
|------|------|
| **学习性** | 清晰展示 Agent Loop 核心原理，代码可读性优先 |
| **可运行** | 能够实际连接 LLM API，执行真实工具操作 |
| **精简性** | 仅保留核心子系统，去除生产级复杂度 |
| **可扩展** | 工具/钩子/配置可按需扩展，架构不设限 |
| **TypeScript** | 使用 TypeScript，与 Claude Agent SDK 技术栈对齐 |

### 2.2 功能范围 — 保留 vs 精简

| 子系统 | Claude Code 完整版 | Mini 精简版 | 精简理由 |
|--------|-------------------|-------------|----------|
| **Agent Loop** | 完整循环 + 流式 + 重试 | ✅ 完整循环 + 流式 | 核心原理，必须保留 |
| **Tool System** | 11+ 内置工具 | ✅ 6个核心工具 | 保留最常用的 Read/Write/Edit/Bash/Glob/Grep |
| **Permission System** | allow/ask/deny + 自动模式 | ✅ allow/deny 两级 | 简化为自动通过/拒绝，去除交互确认 |
| **Context Manager** | 自动压缩 + 摘要 | ✅ 简单截断 | 保留窗口限制，用简单策略处理超限 |
| **Memory System** | CLAUDE.md + Auto Memory | ⚠️ 仅 CLAUDE.md | 保留用户指令，去除 AI 自学习 |
| **Hook System** | 5种钩子类型 × 20+事件 | ⚠️ command类型 + PreToolUse/PostToolUse | 仅保留最实用的 shell 钩子 |
| **Config System** | 5层配置 + 热加载 | ⚠️ 2层配置 | 仅 User + Project 两层 |
| **Subagent System** | 多类型 + 工作树隔离 | ❌ 不实现 | 复杂度高，可后续扩展 |
| **MCP Integration** | 完整 MCP 协议 | ❌ 不实现 | 可后续扩展 |
| **WebSearch/WebFetch** | 完整实现 | ❌ 不实现 | 非核心，可后续扩展 |
| **Monitor** | 完整实现 | ❌ 不实现 | 非核心 |
| **Schedule/Cron** | 完整实现 | ❌ 不实现 | 非核心 |
| **Plan Mode** | 完整实现 | ❌ 不实现 | 非核心 |

### 2.3 核心功能需求

#### FR-01: Agent Loop — 智能体循环

**优先级**: P0（核心）

**描述**: 实现 `while(stop_reason !== "end_turn")` 的核心循环

**详细需求**:
- 接收用户输入，组装为消息
- 调用 LLM API（支持 Anthropic API，流式响应）
- 解析流式响应中的 text 和 tool_use 内容块
- 若包含 tool_use，执行对应工具，将结果追加到消息历史
- 继续调用 LLM，直到返回 end_turn
- 支持最大迭代次数限制（防止无限循环）
- 支持优雅中断（Ctrl+C）

**输入**: 用户 prompt（string）
**输出**: LLM 最终文本回复（string）

#### FR-02: Tool System — 工具系统

**优先级**: P0（核心）

**描述**: 统一的工具注册、调度和执行框架

**详细需求**:
- 工具注册机制：每个工具实现统一接口 `execute(params) → result`
- 工具定义生成：将注册的工具转换为 LLM API 的 `tools` 参数格式
- 工具调度：根据 LLM 返回的 tool_name 路由到对应实现
- 错误处理：工具执行失败时返回错误信息给 LLM（而非崩溃）
- 内置 6 个核心工具：

| 工具 | 实现要点 |
|------|----------|
| **Read** | fs.readFile，支持 offset/limit 分段读取，支持图片/PDF |
| **Write** | fs.writeFile，需权限检查 |
| **Edit** | 精确字符串替换：读取文件 → 查找 old_string → 替换为 new_string → 写回 |
| **Bash** | child_process.exec，支持 timeout，支持后台运行 |
| **Glob** | 基于 fast-glob 的文件模式匹配 |
| **Grep** | 基于 ripgrep 或 Node.js 实现的内容搜索 |

#### FR-03: Permission System — 权限系统

**优先级**: P0（核心）

**描述**: 控制工具执行的安全边界

**详细需求**:
- 配置式规则：`allow` 和 `deny` 规则列表
- 规则语法：`ToolName(param_pattern)`，支持 `*` 通配符
  - 例：`Bash(npm test)`、`Bash(rm *)`、`Read(*)`
- 优先级：deny > allow
- 未匹配任何规则时：默认拒绝（安全优先）
- 运行时校验：每次工具调用前检查权限
- 支持两种运行模式：
  - `strict`：未匹配则拒绝
  - `permissive`：未匹配则允许（用于开发调试）

#### FR-04: Context Manager — 上下文管理

**优先级**: P1（重要）

**描述**: 管理对话历史和上下文窗口

**详细需求**:
- 维护消息历史数组：`[{role, content}]`
- 上下文窗口大小限制（可配置，默认 200K tokens）
- 超限策略：简单截断 — 保留 system prompt + 最近 N 轮对话
- Token 估算：基于字符数的简单估算（4 chars ≈ 1 token）
- 支持 `/compact` 手动压缩
- 支持 `/clear` 清除历史

#### FR-05: Memory System — 记忆系统

**优先级**: P1（重要）

**描述**: CLAUDE.md 指令加载

**详细需求**:
- 加载项目根目录的 `CLAUDE.md` 文件
- 加载 `~/.claude/CLAUDE.md` 用户全局指令
- 加载 `.claude/CLAUDE.md` 项目指令
- 合并策略：全局 → 项目（后者覆盖前者）
- 注入位置：system prompt 之后，作为上下文消息
- 支持 `@path` 导入语法（基础版）

#### FR-06: Hook System — 钩子系统

**优先级**: P2（扩展）

**描述**: 生命周期事件拦截

**详细需求**:
- 支持事件点：`PreToolUse`、`PostToolUse`、`Stop`
- 钩子类型：仅 `command`（执行 shell 命令）
- 配置格式：
  ```json
  {
    "hooks": {
      "PreToolUse": [{
        "matcher": "Bash",
        "hooks": [{ "type": "command", "command": "./check.sh" }]
      }]
    }
  }
  ```
- 钩子输入：通过 stdin 传入 JSON（tool_name, tool_input, session_id）
- 钩子输出：stdout 解析为 JSON（decision: allow/deny）
- 超时控制：默认 30 秒

#### FR-07: Config System — 配置系统

**优先级**: P1（重要）

**描述**: 两层配置管理

**详细需求**:
- User 配置：`~/.mini-claude/settings.json`
- Project 配置：`.mini-claude/settings.json`
- 合并策略：Project > User（项目级覆盖用户级）
- 配置内容：
  ```json
  {
    "model": "claude-sonnet-4-6",
    "permissions": { "allow": [...], "deny": [...] },
    "hooks": { ... },
    "maxTokens": 200000,
    "maxIterations": 50
  }
  ```

#### FR-08: CLI Interface — 命令行界面

**优先级**: P0（核心）

**描述**: 交互式命令行界面

**详细需求**:
- 交互模式：`mini-claude` 启动 REPL
- 管道模式：`echo "fix bug" | mini-claude -p` 非交互
- 斜杠命令：
  - `/help` — 帮助信息
  - `/compact` — 压缩上下文
  - `/clear` — 清除历史
  - `/model` — 切换模型
  - `/config` — 查看配置
  - `/quit` — 退出
- 输出格式：Markdown 渲染（使用 chalk + markdown-it）
- 工具调用可视化：显示工具名称、参数、执行状态

---

## 三、系统架构设计

### 3.1 项目结构

```
mini-claude/
├── src/
│   ├── index.ts                 # 入口：CLI 参数解析 + 启动
│   ├── agent/
│   │   ├── loop.ts              # 核心：Agent Loop 实现
│   │   ├── context.ts           # 上下文管理器
│   │   └── message.ts           # 消息类型定义
│   ├── tools/
│   │   ├── registry.ts          # 工具注册中心
│   │   ├── dispatcher.ts        # 工具调度器
│   │   ├── definitions.ts       # 工具 JSON Schema 定义
│   │   ├── read.ts              # Read 工具实现
│   │   ├── write.ts             # Write 工具实现
│   │   ├── edit.ts              # Edit 工具实现
│   │   ├── bash.ts              # Bash 工具实现
│   │   ├── glob.ts              # Glob 工具实现
│   │   └── grep.ts              # Grep 工具实现
│   ├── permissions/
│   │   ├── checker.ts           # 权限校验器
│   │   ├── rules.ts             # 规则解析与匹配
│   │   └── schema.ts            # 权限配置类型定义
│   ├── memory/
│   │   ├── loader.ts            # CLAUDE.md 加载器
│   │   └── parser.ts            # @import 解析器
│   ├── hooks/
│   │   ├── runner.ts            # 钩子运行器
│   │   └── events.ts            # 事件类型定义
│   ├── config/
│   │   ├── manager.ts           # 配置管理器
│   │   ├── merger.ts            # 多层配置合并
│   │   └── schema.ts            # 配置类型定义
│   ├── api/
│   │   ├── client.ts            # Anthropic API 客户端
│   │   ├── stream.ts            # 流式响应处理
│   │   └── tokens.ts            # Token 估算
│   └── cli/
│       ├── repl.ts              # 交互式 REPL
│       ├── render.ts            # 输出渲染
│       └── commands.ts          # 斜杠命令处理
├── config/
│   └── default.json             # 默认配置
├── package.json
├── tsconfig.json
└── README.md
```

### 3.2 核心类图

```
┌─────────────────────────────────────────────────┐
│                   AgentLoop                      │
│─────────────────────────────────────────────────│
│ - context: ContextManager                       │
│ - toolDispatcher: ToolDispatcher                │
│ - permissionChecker: PermissionChecker          │
│ - hookRunner: HookRunner                        │
│ - apiClient: APIClient                          │
│─────────────────────────────────────────────────│
│ + run(prompt: string): Promise<string>          │
│ - callLLM(): Promise<LLMResponse>              │
│ - executeTools(calls: ToolCall[]): Promise<()>  │
│ - shouldStop(response): boolean                 │
└────────────┬────────────────────────────────────┘
             │ uses
     ┌───────┴────────┐
     │                 │
┌────▼─────┐   ┌──────▼──────┐   ┌──────────────┐
│ Context  │   │  Tool       │   │ Permission   │
│ Manager  │   │  Dispatcher │   │ Checker      │
│──────────│   │─────────────│   │──────────────│
│+messages │   │+registry    │   │+rules        │
│+addMsg() │   │+dispatch()  │   │+check()      │
│+compact()│   │+register()  │   │+match()      │
└──────────┘   └──────┬──────┘   └──────────────┘
                      │ uses
              ┌───────┴────────┐
              │                │
        ┌─────▼─────┐  ┌──────▼──────┐
        │ Tool      │  │ Hook        │
        │ Registry  │  │ Runner      │
        │───────────│  │─────────────│
        │+tools:Map │  │+run(event)  │
        │+get(name) │  │+match()     │
        │+schemas() │  └─────────────┘
        └───────────┘
```

### 3.3 Agent Loop 核心流程

```typescript
// 伪代码 — Agent Loop 核心逻辑
async function agentLoop(prompt: string): Promise<string> {
  // 1. 初始化上下文
  context.addMessage({ role: "user", content: prompt });

  // 2. 循环直到 LLM 停止
  while (true) {
    // 2.1 组装请求
    const systemPrompt = buildSystemPrompt(config, memory);
    const tools = toolRegistry.getSchemas();
    const messages = context.getMessages();

    // 2.2 调用 LLM
    const response = await apiClient.create({
      model: config.model,
      system: systemPrompt,
      messages,
      tools,
      stream: true,
    });

    // 2.3 处理流式响应
    const { text, toolCalls, stopReason } = await processStream(response);
    context.addMessage({ role: "assistant", content: [...text, ...toolCalls] });

    // 2.4 如果没有工具调用，返回文本
    if (stopReason === "end_turn" || toolCalls.length === 0) {
      return text;
    }

    // 2.5 执行工具调用
    for (const call of toolCalls) {
      // 权限检查
      const permitted = permissionChecker.check(call.name, call.input);
      if (!permitted) {
        context.addMessage({ role: "tool_result", error: "Permission denied" });
        continue;
      }

      // 钩子：PreToolUse
      await hookRunner.run("PreToolUse", call);

      // 执行工具
      const result = await toolDispatcher.dispatch(call.name, call.input);

      // 钩子：PostToolUse
      await hookRunner.run("PostToolUse", call, result);

      // 追加结果
      context.addMessage({ role: "tool_result", tool_use_id: call.id, content: result });
    }
  }
}
```

---

## 四、技术选型

| 层面 | 选型 | 理由 |
|------|------|------|
| **语言** | TypeScript | 与 Claude Agent SDK 对齐，类型安全 |
| **运行时** | Node.js | 跨平台，工具实现简单（fs, child_process） |
| **LLM SDK** | @anthropic-ai/sdk | 官方 SDK，流式支持 |
| **CLI 框架** | Commander.js | 轻量级，生态成熟 |
| **REPL** | readline / inquirer | 交互式输入 |
| **渲染** | chalk + markdown-it | 终端彩色 + Markdown |
| **文件搜索** | fast-glob | Glob 模式匹配 |
| **内容搜索** | ripgrep (rg) 或 exec grep | 高性能内容搜索 |
| **配置** | JSON (直接读写) | 简单，与 Claude Code 对齐 |
| **打包** | tsup | 快速构建，ESM + CJS |

---

## 五、实现阶段规划

### Phase 1: 核心骨架（P0）

**目标**: 跑通 Agent Loop，能对话 + 执行工具

- [ ] 项目初始化（tsconfig, package.json, eslintrc）
- [ ] API Client — Anthropic API 流式调用
- [ ] Tool Registry — 工具注册 + Schema 生成
- [ ] Tool Dispatcher — 工具调度 + 错误处理
- [ ] Agent Loop — 核心循环实现
- [ ] CLI 入口 — 交互式 REPL

**交付物**: `mini-claude "list files in this directory"` 能运行

### Phase 2: 核心工具（P0）

**目标**: 6 个核心工具实现

- [ ] Read 工具
- [ ] Write 工具
- [ ] Edit 工具（精确字符串替换）
- [ ] Bash 工具（命令执行 + 超时）
- [ ] Glob 工具（文件搜索）
- [ ] Grep 工具（内容搜索）

**交付物**: 能实际读写文件、执行命令、搜索代码

### Phase 3: 安全与配置（P1）

**目标**: 权限控制 + 配置管理 + 记忆加载

- [ ] Permission Checker — allow/deny 规则
- [ ] Config Manager — 两层配置
- [ ] Memory Loader — CLAUDE.md 加载
- [ ] Context Manager — 窗口限制 + 简单截断

**交付物**: 安全可控，配置可调，记忆可用

### Phase 4: 钩子与增强（P2）

**目标**: Hook 系统 + 斜杠命令 + 输出美化

- [ ] Hook Runner — PreToolUse/PostToolUse
- [ ] 斜杠命令 — /help /compact /clear /model
- [ ] 输出渲染 — Markdown + 工具调用可视化
- [ ] 管道模式 — 非交互 `-p` 模式

**交付物**: 完整可用的精简版 Agent CLI

---

## 六、关键接口定义

### 6.1 工具接口

```typescript
interface Tool {
  name: string;                    // 工具名称，如 "Read"
  description: string;             // 工具描述
  inputSchema: JSONSchema;         // 输入参数 JSON Schema
  execute(params: Record<string, any>): Promise<string>;  // 执行，返回文本结果
}
```

### 6.2 权限规则

```typescript
interface PermissionRule {
  tool: string;        // 工具名，如 "Bash"
  pattern: string;     // 参数模式，如 "npm test *" 或 "*"
}

interface Permissions {
  allow: PermissionRule[];   // 自动通过
  deny: PermissionRule[];    // 直接拒绝
}
```

### 6.3 钩子配置

```typescript
interface HookConfig {
  matcher: string;          // 匹配的工具名模式
  hooks: HookHandler[];     // 处理器列表
}

interface HookHandler {
  type: "command";          // 仅支持 command 类型
  command: string;          // Shell 命令
  timeout?: number;         // 超时秒数
}
```

### 6.4 配置结构

```typescript
interface Config {
  model: string;              // 模型 ID
  maxTokens: number;          // 上下文窗口大小
  maxIterations: number;      // 最大循环次数
  permissions: Permissions;   // 权限规则
  hooks: Record<string, HookConfig[]>;  // 钩子配置
  apiKey?: string;            // API Key（建议用环境变量）
}
```

---

## 七、与 Claude Code 的对比

| 维度 | Claude Code | Mini Claude Code |
|------|-------------|------------------|
| 代码量 | ~100K+ 行 | ~3K-5K 行 |
| 工具数量 | 11+ | 6 |
| 权限模式 | allow/ask/deny + auto | allow/deny |
| 配置层数 | 5 层 | 2 层 |
| Hook 类型 | 5 种 | 1 种 (command) |
| Hook 事件 | 20+ | 3 (PreToolUse/PostToolUse/Stop) |
| 子代理 | 完整 | 无 |
| MCP | 完整 | 无 |
| 上下文压缩 | 智能摘要 | 简单截断 |
| 记忆 | CLAUDE.md + Auto | 仅 CLAUDE.md |
| 流式渲染 | 完整 TUI | 基础终端输出 |
| 安装方式 | 全平台原生 | npm install |

---

## 八、风险与约束

| 风险 | 影响 | 缓解措施 |
|------|------|----------|
| API Key 安全 | 泄露风险 | 优先使用环境变量，不硬编码 |
| Bash 工具安全 | 任意命令执行 | 严格的 deny 规则，默认拒绝危险命令 |
| 无限循环 | API 费用失控 | maxIterations 硬限制 |
| Token 超限 | API 报错 | 简单截断 + compact 提示 |
| 文件覆盖 | Write 覆盖重要文件 | Edit 优先，Write 需明确权限 |

---

## 九、总结

Mini Claude Code 的核心价值在于 **用最少的代码清晰展示 Agent Loop 的完整原理**：

1. **Agentic Loop** 是灵魂 — while 循环驱动 LLM + 工具交替执行
2. **Tool System** 是双手 — 统一接口让 LLM 能操作真实世界
3. **Permission System** 是安全网 — 在能力与安全间取平衡
4. **Context/Memory** 是大脑 — 让 Agent 拥有记忆和指令遵循能力
5. **Hook System** 是神经系统 — 在关键节点注入自定义逻辑

精简版去除的是 **生产级的复杂度**（多层配置、完整 MCP、子代理隔离、智能压缩），保留的是 **架构级的清晰度**（核心循环、工具调度、权限校验、配置合并）。

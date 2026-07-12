# Claude Code 深度架构原理 — 面试 & 实战参考

> 基于官方文档源码级研究，覆盖架构原理、核心机制、最佳实践和面试高频考点

---

## 一、整体架构：Agentic Harness

### 1.1 核心定位

Claude Code 不是聊天机器人，而是一个 **Agentic Harness（智能体框架）**：

```
┌──────────────────────────────────────────────────────────┐
│                    Claude Code                           │
│              = Agentic Harness                           │
│                                                          │
│  ┌──────────┐   ┌──────────┐   ┌───────────────────┐   │
│  │  Model   │ + │  Tools   │ + │ Context Management │   │
│  │(推理大脑)│   │(行动双手)│   │   (记忆与控制)     │   │
│  └──────────┘   └──────────┘   └───────────────────┘   │
│                                                          │
│  Model 负责「想」，Tools 负责「做」，Harness 负责「管」  │
└──────────────────────────────────────────────────────────┘
```

**面试考点**：Claude Code 的本质是什么？
> 答：一个围绕 LLM 构建的 Agentic Harness。它提供工具、上下文管理和执行环境，将语言模型转化为能自主编码的智能体。核心循环是 `gather context → take action → verify results`。

### 1.2 Agentic Loop 详解

```
用户输入 (prompt)
    │
    ▼
┌─────────────────────────────────────┐
│  1. 构建上下文                       │
│     system prompt + CLAUDE.md +     │
│     auto memory + 历史消息 + 工具定义 │
└──────────────┬──────────────────────┘
               │
               ▼
┌─────────────────────────────────────┐
│  2. 调用 LLM API（流式）            │
│     → 返回 text + tool_use 内容块   │
└──────────────┬──────────────────────┘
               │
        ┌──────┴──────┐
        │ 有 tool_use? │
        └──────┬──────┘
           Yes │    │ No
               │    └──→ 返回 text，循环结束
               ▼
┌─────────────────────────────────────┐
│  3. 对每个 tool_use：               │
│     a. 权限检查（Permission Mode）  │
│     b. PreToolUse 钩子             │
│     c. 执行工具，获取结果           │
│     d. PostToolUse 钩子            │
│     e. 结果追加到消息历史           │
└──────────────┬──────────────────────┘
               │
               ▼
          回到步骤 2（继续调用 LLM）
```

**关键细节**：
- 每轮 LLM 调用可能返回**多个** tool_use，它们被逐个执行
- 工具结果以 `tool_result` 内容块追加到消息历史
- 循环直到 `stop_reason === "end_turn"` 或无 tool_use
- 用户可随时按 `Esc` 中断，上下文保留

**面试考点**：Agentic Loop 和普通 Chat Loop 的区别？
> 答：普通 Chat 是一问一答，单轮结束。Agentic Loop 是 LLM 和工具**交替执行**的 while 循环：LLM 决定用什么工具 → 工具返回结果 → LLM 根据结果继续推理 → 可能再调工具 → 直到任务完成。这使 LLM 从"只能说话"变成"能行动"。

---

## 二、上下文窗口 — 最核心的约束

### 2.1 上下文窗口加载时序

上下文窗口 200K tokens，启动时自动加载以下内容：

| 加载顺序 | 内容 | Token 消耗 | 可见性 |
|---------|------|-----------|--------|
| 1 | **System Prompt** | ~4,200 | 对用户隐藏 |
| 2 | **Auto Memory (MEMORY.md)** | ~680 | 对用户隐藏 |
| 3 | **Environment Info** | ~280 | 对用户隐藏 |
| 4 | **MCP Tools (deferred)** | ~120 | 仅名称，Schema 延迟加载 |
| 5 | **Skill Descriptions** | ~450 | 仅描述，内容按需加载 |
| 6 | **~/.claude/CLAUDE.md** | ~320 | 用户全局指令 |
| 7 | **Project CLAUDE.md** | ~1,800 | 项目指令 |

**启动消耗总计**：约 7,850 tokens（占 200K 的 ~4%）

### 2.2 运行时增长

| 事件 | Token 消耗 | 说明 |
|------|-----------|------|
| 用户 prompt | ~45 | 用户输入 |
| Read 一个文件 | ~1,100-2,400 | **文件读取是最大消耗源** |
| Bash 输出 | ~500-5,000 | 命令输出 |
| LLM 响应 | ~200-2,000 | 文本 + tool_use |
| Path-scoped Rule | ~380 | 按需加载的规则 |

**面试考点**：上下文窗口中什么最耗 token？
> 答：**文件读取**。一个中等源文件可能消耗 2,000+ tokens。这也是为什么应该给 Claude 具体的文件路径（减少不必要的读取），以及用 subagent 做探索（不占主上下文）。

### 2.3 上下文压缩（Compaction）

当上下文接近 200K 限制时，自动触发压缩：

1. **优先清除**：较旧的 tool output（文件内容、命令输出）
2. **然后摘要**：对话历史压缩为摘要
3. **保留**：system prompt、CLAUDE.md、最近的关键代码片段、用户请求

**压缩后行为**：
- Project-root CLAUDE.md 会**重新注入**（从磁盘重新读取）
- 子目录 CLAUDE.md **不会**重新注入
- Skill descriptions **不会**重新注入（只有实际用过的 skill 被保留）
- 早期对话中的详细指令可能丢失

**手动控制**：
- `/compact` — 手动压缩
- `/compact Focus on the API changes` — 带焦点的压缩
- `/clear` — 完全清除，重新开始

**面试考点**：上下文压缩后什么会丢失？
> 答：早期对话中的详细指令、子目录 CLAUDE.md、未使用的 skill descriptions。重要规则应放在根目录 CLAUDE.md 中（压缩后会重新注入），而非依赖对话历史。

---

## 三、工具系统 — Agent 的双手

### 3.1 工具分类

| 类别 | 工具 | 能力 |
|------|------|------|
| **文件操作** | Read, Write, Edit, NotebookEdit | 读写文件、精确编辑、笔记本编辑 |
| **搜索** | Glob, Grep | 文件模式匹配、内容正则搜索 |
| **执行** | Bash, PowerShell | 运行 shell 命令、脚本、git |
| **Web** | WebSearch, WebFetch | 网页搜索、获取网页内容 |
| **代码智能** | LSP (via plugin) | 类型错误、定义跳转、引用查找 |
| **编排** | Agent, AskUserQuestion | 派生子代理、询问用户 |
| **监控** | Monitor | 监控后台脚本输出 |

### 3.2 工具选择机制

Claude **自主决定**使用哪个工具，基于：
- 用户的 prompt
- 当前已知的上下文
- 前一步工具返回的结果

例：`"fix the failing tests"` → Claude 可能：
1. `Bash(npm test)` — 运行测试看失败
2. `Read(error output)` — 读取错误信息
3. `Grep(pattern)` — 搜索相关源文件
4. `Read(source file)` — 读取源码
5. `Edit(file, old, new)` — 修复代码
6. `Bash(npm test)` — 再次验证

### 3.3 MCP 工具延迟加载

MCP 工具采用**延迟加载**策略：
- 启动时只加载工具**名称**（~120 tokens）
- 完整 JSON Schema **按需加载**（Claude 需要用时才拉取）
- 通过 `ENABLE_TOOL_SEARCH` 控制行为：
  - `auto`：Schema 在 10% 上下文窗口内时预加载
  - `false`：全部预加载

**面试考点**：MCP 工具如何节省上下文？
> 答：默认只加载工具名称列表，完整 Schema 延迟到 Claude 实际需要使用时才加载。这种 Tool Search 机制让大量 MCP 工具的空闲开销极低。

---

## 四、权限系统 — 安全边界

### 4.1 六种权限模式

| 模式 | 自动执行范围 | 适用场景 |
|------|------------|---------|
| `default` | 仅读取 | 入门、敏感操作 |
| `acceptEdits` | 读取 + 文件编辑 + 常见文件命令 | 迭代代码 |
| `plan` | 仅读取（不编辑源码） | 先分析再动手 |
| `auto` | 全部（有后台安全检查） | 长任务、减少打断 |
| `dontAsk` | 仅预批准的工具 | CI/CD、脚本 |
| `bypassPermissions` | 全部（无检查） | 仅限隔离容器 |

### 4.2 Auto Mode 的分类器模型

Auto Mode 使用**独立的分类器模型**（非用户选择的模型）审查每个操作：

**默认阻止**：
- `curl | bash` 等下载执行
- 向外部端点发送敏感数据
- 生产部署和迁移
- 大规模删除云存储
- 授予 IAM/仓库权限
- Force push 或直接 push 到 main
- `terraform destroy` 等破坏性操作

**默认允许**：
- 工作目录内的文件操作
- 安装 lock 文件声明的依赖
- 读取 `.env` 并发送到匹配的 API
- 只读 HTTP 请求
- Push 到当前分支

**回退机制**：
- 连续被阻止 3 次 → 暂停 auto mode，恢复手动确认
- 总计被阻止 20 次 → 同上
- 非交互模式（`-p`）中重复阻止 → 终止会话

### 4.3 Protected Paths

以下路径的写入**永不自动批准**（除 `bypassPermissions` 外）：

```
.git/  .claude/  .vscode/  .idea/  .husky/  .envrc
.gitconfig  .bashrc  .zshrc  .npmrc  .mcp.json  .claude.json
```

即使有 `permissions.allow` 规则匹配，保护路径检查也**先于** allow 规则执行。

### 4.4 权限规则语法

```json
{
  "permissions": {
    "allow": ["Bash(npm test)", "Bash(npm run lint *)", "Read(*)"],
    "ask": ["Bash(rm *)", "Write(*)"],
    "deny": ["Bash(curl * | * sh)", "Read(.env)"]
  }
}
```

优先级：**deny > ask > allow**，规则跨配置层**合并**。

**面试考点**：Auto Mode 如何保证安全？
> 答：使用独立的分类器模型在操作执行前审查。分类器看到用户消息和 tool call，但**不看到 tool result**（防止恶意内容注入）。同时有服务端探针扫描 tool result 中的可疑内容。分类器阻止的阈值触发后会自动回退到手动确认模式。

---

## 五、记忆系统 — 跨会话持久化

### 5.1 双记忆机制

| 维度 | CLAUDE.md | Auto Memory |
|------|-----------|-------------|
| **谁写** | 用户 | Claude 自己 |
| **内容** | 指令和规则 | 学习和模式 |
| **作用域** | 项目/用户/组织 | 每仓库 |
| **加载** | 每次会话完整加载 | 前 200 行或 25KB |
| **用途** | 编码标准、工作流、架构 | 构建命令、调试洞察、偏好 |

### 5.2 CLAUDE.md 加载顺序

从文件系统根目录**向上遍历**到工作目录，**全部加载**（追加而非覆盖）：

```
/CLAUDE.md                    ← 最先加载
/home/user/CLAUDE.md          ← 
/home/user/project/CLAUDE.md  ← 最后加载（最近优先）
```

子目录的 CLAUDE.md **延迟加载**：当 Claude 读取该子目录中的文件时才加载。

### 5.3 Auto Memory 存储

```
~/.claude/projects/<project>/memory/
├── MEMORY.md          ← 索引入口，每次会话加载前 200 行
├── debugging.md       ← 调试模式笔记
├── api-conventions.md ← API 约定
└── ...                ← 其他主题文件
```

- 同一 git 仓库的所有 worktree 共享一个 memory 目录
- 主题文件**不在启动时加载**，Claude 用标准文件工具按需读取

**面试考点**：CLAUDE.md 和 Auto Memory 的区别？
> 答：CLAUDE.md 是用户编写的指令（编码标准、构建命令），每次会话完整加载。Auto Memory 是 Claude 自己积累的学习笔记（调试洞察、偏好），只加载 MEMORY.md 的前 200 行/25KB，主题文件按需读取。两者互补：CLAUDE.md 管"应该怎么做"，Auto Memory 管"学到了什么"。

---

## 六、钩子系统 — 生命周期拦截

### 6.1 事件点

| 频率 | 事件 |
|------|------|
| 每会话 | `SessionStart`, `SessionEnd` |
| 每轮 | `UserPromptSubmit`, `Stop`, `StopFailure` |
| 每工具调用 | `PreToolUse`, `PostToolUse`, `PostToolUseFailure` |
| 其他 | `PermissionRequest`, `PermissionDenied`, `PostToolBatch`, `Notification`, `SubagentStart`, `SubagentStop`, `PreCompact`, `PostCompact`, `ConfigChange`, `FileChanged` 等 |

### 6.2 五种钩子类型

| 类型 | 说明 | 适用场景 |
|------|------|---------|
| `command` | 执行 shell 命令 | 格式化、lint、阻止危险操作 |
| `http` | POST 到 HTTP 端点 | 通知、审计 |
| `mcp_tool` | 调用 MCP 工具 | 通过 MCP 服务处理 |
| `prompt` | LLM 评估 yes/no | 需要推理的决策 |
| `agent` | 派生子代理验证 | 复杂验证逻辑 |

### 6.3 退出码语义

| 退出码 | 含义 | 行为 |
|--------|------|------|
| 0 | 成功 | 解析 stdout JSON |
| 2 | **阻止** | 忽略 stdout，将 stderr 反馈给 Claude，阻止操作 |
| 其他 | 非阻止错误 | 显示 stderr 第一行，继续执行 |

**关键**：退出码 1 是**非阻止**的！要用退出码 2 才能真正阻止操作。

### 6.4 钩子 vs CLAUDE.md

| 维度 | Hook | CLAUDE.md |
|------|------|-----------|
| 执行 | 确定性，每次必触发 | 建议性，Claude 可能不遵循 |
| 上下文消耗 | 零（除非返回输出） | 每次请求都消耗 |
| 适用 | 必须每次执行的操作 | 行为指导和偏好 |

**面试考点**：什么时候用 Hook 而不是 CLAUDE.md？
> 答：当规则**必须每次都执行**时用 Hook（如每次编辑后自动 lint、阻止写入 .env）。CLAUDE.md 是建议性的，Claude 可能不遵循。Hook 是确定性的保证。如果一条规则违反会导致严重后果，就应该用 Hook 而非 CLAUDE.md。

---

## 七、子代理系统 — 上下文隔离

### 7.1 核心原理

```
主会话 (200K context)
    │
    ├──→ 子代理 A (独立 200K context)
    │      └──→ 返回摘要（不返回中间过程）
    │
    ├──→ 子代理 B (独立 200K context)
    │      └──→ 返回摘要
    │
    └──→ 主会话只接收摘要，不膨胀上下文
```

### 7.2 子代理加载内容

- 子代理自己的 system prompt（非完整 Claude Code system prompt）
- `skills:` 字段列出的 skill **完整预加载**
- CLAUDE.md 和 git status（Explore 和 Plan 代理除外）
- 主代理传入的 prompt

**子代理不继承**：主会话的对话历史、已调用的 skill

### 7.3 内置子代理

| 代理 | 用途 | 工具 |
|------|------|------|
| `Explore` | 只读搜索，广度优先 | Read, Grep, Glob |
| `Plan` | 架构设计，制定计划 | Read, Grep, Glob |
| 自定义 | 专用任务 | 用户指定 |

**面试考点**：子代理如何节省主上下文？
> 答：子代理在独立的上下文窗口中运行，可能读取几十个文件或执行大量搜索，但主会话只接收一个摘要。子代理的中间工作不消耗主上下文。这是长会话中最重要的上下文管理策略。

---

## 八、配置系统 — 多层合并

### 8.1 配置层级（优先级从高到低）

| 层级 | 位置 | 可共享 | 说明 |
|------|------|--------|------|
| Managed | 服务端/MDM/注册表 | 是（IT 管理） | 不可被用户覆盖 |
| CLI args | 命令行参数 | 否 | 本次运行覆盖 |
| Local | `.claude/settings.local.json` | 否（gitignored） | 个人项目配置 |
| Project | `.claude/settings.json` | 是（git 提交） | 团队共享 |
| User | `~/.claude/settings.json` | 否 | 个人全局配置 |

**权限规则特殊**：`allow`/`ask`/`deny` 规则跨层级**合并**（而非覆盖）。

### 8.2 关键配置项

```json
{
  "model": "claude-sonnet-4-6",           // 模型选择
  "maxTokens": 16384,                     // 最大输出 token
  "permissions": {                        // 权限规则
    "allow": ["Bash(npm test)"],
    "deny": ["Bash(rm -rf *)"]
  },
  "hooks": { ... },                       // 钩子配置
  "env": { "FOO": "bar" },               // 环境变量
  "autoMemoryEnabled": true,              // Auto Memory 开关
  "autoCompactEnabled": true,             // 自动压缩开关
  "fileCheckpointingEnabled": true        // 文件快照（/rewind）
}
```

---

## 九、会话管理 — 持久化与恢复

### 9.1 会话存储

- 每条消息、工具调用和结果写入 `~/.claude/projects/` 下的 JSONL 文件
- 编辑文件前自动快照（支持 `/rewind` 回退）
- 会话独立：新会话从空白上下文开始

### 9.2 恢复与分支

| 操作 | 命令 | 行为 |
|------|------|------|
| 继续 | `claude --continue` | 同一会话 ID，追加消息 |
| 选择 | `claude --resume` | 从列表选择会话 |
| 分支 | `--fork-session` 或 `/branch` | 复制历史到新会话 ID |
| 命名 | `/rename` | 给会话命名便于查找 |

### 9.3 检查点（Checkpoint）

- 每次用户发送 prompt 创建一个检查点
- `Esc + Esc` 或 `/rewind` 打开回退菜单
- 可恢复：仅对话、仅代码、或两者都恢复
- 检查点**跨会话持久化**
- 仅追踪 Claude 做的修改，不追踪外部进程

---

## 十、扩展系统对比 — 何时用什么

| 扩展 | 何时添加 | 触发器 |
|------|---------|--------|
| **CLAUDE.md** | Claude 同一错误犯两次 | 持久规则 |
| **Skill** | 重复输入相同 prompt | 可复用工作流 |
| **Subagent** | 副任务淹没主对话 | 上下文隔离需求 |
| **MCP** | 需要从外部系统获取数据 | Claude 看不到的数据 |
| **Hook** | 某操作必须每次执行 | 确定性保证需求 |
| **Plugin** | 多仓库复用同一配置 | 跨项目共享 |

### 各扩展的上下文消耗

| 扩展 | 加载时机 | 上下文消耗 |
|------|---------|-----------|
| CLAUDE.md | 会话启动 | 每次请求 |
| Skill | 启动(描述) + 使用时(内容) | 低（直到使用） |
| MCP | 启动(名称) + 使用时(Schema) | 低（直到使用） |
| Subagent | 派生时 | 与主会话隔离 |
| Hook | 触发时 | 零（除非返回输出） |

---

## 十一、实战最佳实践 — 高频使用模式

### 11.1 给 Claude 验证手段

```
❌ "implement a function that validates email addresses"
✅ "write validateEmail. test cases: user@example.com→true, invalid→false.
    run the tests after implementing"
```

验证层级（从轻到重）：
1. **Prompt 内**：同一消息中要求运行测试并迭代
2. **`/goal` 条件**：每轮后自动检查目标是否达成
3. **Stop Hook**：脚本级确定性门控
4. **验证子代理**：独立上下文中的第二意见

### 11.2 探索 → 规划 → 实现

```
1. Plan Mode: "read /src/auth and understand how we handle sessions"
2. Plan Mode: "create a plan for adding Google OAuth"
3. Default Mode: "implement the OAuth flow from your plan"
4. Default Mode: "commit with a descriptive message"
```

### 11.3 上下文管理铁律

1. **不相关任务间用 `/clear`** — 避免厨房水槽会话
2. **同一问题纠正超过 2 次 → `/clear` 重来** — 上下文已被污染
3. **探索用子代理** — `"use subagents to investigate X"`
4. **CLAUDE.md 保持 < 200 行** — 过长则 Claude 忽略一半
5. **用 `/btw` 问快速问题** — 不进入对话历史

### 11.4 CLAUDE.md 编写原则

| ✅ 包含 | ❌ 排除 |
|---------|---------|
| Claude 猜不到的 Bash 命令 | Claude 能从代码推断的 |
| 与默认不同的代码风格规则 | 标准语言约定 |
| 测试指令和首选 runner | 详细 API 文档（链接即可） |
| 仓库约定（分支命名、PR 约定） | 频繁变化的信息 |
| 项目特有的架构决策 | 逐文件的代码库描述 |

### 11.5 并行工作模式

| 方法 | 协调程度 | 适用 |
|------|---------|------|
| Worktrees | 无协调，独立 checkout | 独立任务 |
| Desktop 多会话 | 手动协调 | 可视化管理 |
| Agent Teams | 自动协调（共享任务+消息） | 复杂协作 |
| `claude -p` 循环 | 脚本级协调 | 批量迁移 |

---

## 十二、面试高频 Q&A

### Q1: Claude Code 的 Agentic Loop 和普通 Chat 有什么区别？

普通 Chat 是单轮问答：用户问 → LLM 答 → 结束。

Agentic Loop 是 LLM 和工具的 **while 循环**：LLM 可以决定调用工具（读文件、执行命令、搜索代码），工具返回结果后 LLM 继续推理，可能再调工具，直到任务完成。这使 LLM 从"只能说话"变成"能行动的智能体"。

### Q2: 上下文窗口满了怎么办？

自动触发 Compaction：优先清除旧的 tool output，然后对话历史压缩为摘要。Project-root CLAUDE.md 会重新注入，但子目录 CLAUDE.md 和未使用的 skill 不会。手动可用 `/compact` 或 `/clear`。

### Q3: Hook 和 CLAUDE.md 都能约束 Claude 行为，何时用哪个？

CLAUDE.md 是**建议性**的 — Claude 可能不遵循。Hook 是**确定性**的 — 每次必触发，不可跳过。当规则违反会导致严重后果（如写入 .env、执行 rm -rf），必须用 Hook。当只是偏好或习惯（如代码风格），用 CLAUDE.md 即可。

### Q4: 子代理如何节省主上下文？

子代理在独立的上下文窗口运行。它可能读取几十个文件做研究，但主会话只接收一个摘要。中间过程不占主上下文。这是长会话中最有效的上下文管理策略。

### Q5: Auto Mode 的分类器如何工作？

独立的分类器模型在操作执行前审查。它看到用户消息和 tool call，但**不看到 tool result**（防止恶意内容注入攻击）。被阻止的操作有阈值：连续 3 次或总计 20 次后回退到手动确认。

### Q6: MCP 工具如何避免消耗过多上下文？

默认只加载工具名称列表（~120 tokens），完整 JSON Schema 延迟到 Claude 实际使用时才加载。这种 Tool Search 机制让大量 MCP 工具的空闲开销极低。

### Q7: Protected Paths 是什么？为什么需要？

`.git/`、`.claude/`、`.envrc`、`.bashrc` 等关键路径的写入永不自动批准（除 bypassPermissions 外）。即使有 allow 规则也无效。这是防止 Claude 意外破坏仓库状态或自身配置的安全层。

### Q8: Skill 和 CLAUDE.md 的区别？

CLAUDE.md 每次会话自动完整加载，适合"每次都要知道"的规则。Skill 按需加载（描述在启动时，内容在使用时），适合"有时需要"的参考材料或可调用的工作流。CLAUDE.md 应 < 200 行，超出部分移到 Skill。

### Q9: Claude Code 如何实现文件编辑的安全回退？

每次编辑前自动快照当前文件内容。用户可通过 `Esc+Esc` 或 `/rewind` 打开回退菜单，恢复到任意检查点。检查点跨会话持久化，但仅追踪 Claude 的修改，不追踪外部进程。

### Q10: 权限规则的优先级是什么？

`deny > ask > allow`。deny 规则最高优先级，无论 allow 怎么写都不会覆盖 deny。规则跨配置层级（Managed/User/Project/Local）**合并**而非覆盖。Protected Paths 检查先于所有规则执行。

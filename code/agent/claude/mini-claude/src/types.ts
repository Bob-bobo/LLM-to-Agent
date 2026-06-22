/**
 * Mini Claude Code — 核心类型定义
 *
 * 对齐 Claude Code 的消息格式和工具接口
 * 支持多 Provider：Anthropic / OpenAI-compatible（国内各厂商）
 */

// ─── 消息类型 ───────────────────────────────────────────────

export interface TextContent {
  type: "text";
  text: string;
}

export interface ToolUseContent {
  type: "tool_use";
  id: string;
  name: string;
  input: Record<string, unknown>;
}

export interface ToolResultContent {
  type: "tool_result";
  tool_use_id: string;
  content: string;
  is_error?: boolean;
}

export type ContentBlock = TextContent | ToolUseContent | ToolResultContent;

export interface Message {
  role: "user" | "assistant";
  content: ContentBlock[];
}

// ─── 工具接口 ───────────────────────────────────────────────

export interface ToolDefinition {
  name: string;
  description: string;
  input_schema: Record<string, unknown>; // JSON Schema
}

export interface Tool {
  name: string;
  description: string;
  inputSchema: Record<string, unknown>;
  execute(params: Record<string, unknown>): Promise<string>;
}

// ─── Provider 类型 ──────────────────────────────────────────

/**
 * LLM Provider 类型
 *
 * - anthropic:  Anthropic 官方 API（Claude 系列）
 * - openai:     OpenAI 兼容 API（覆盖国内几乎所有厂商）
 */
export type ProviderType = "anthropic" | "openai";

/**
 * Provider 配置
 *
 * 国内厂商 OpenAI 兼容 API 示例：
 *   智谱GLM:    { type: "openai", baseUrl: "https://open.bigmodel.cn/api/paas/v4" }
 *   DeepSeek:   { type: "openai", baseUrl: "https://api.deepseek.com/v1" }
 *   Moonshot:   { type: "openai", baseUrl: "https://api.moonshot.cn/v1" }
 *   通义Qwen:   { type: "openai", baseUrl: "https://dashscope.aliyuncs.com/compatible-mode/v1" }
 *   百川:       { type: "openai", baseUrl: "https://api.baichuan-ai.com/v1" }
 *   零一万物:   { type: "openai", baseUrl: "https://api.lingyiwanwu.com/v1" }
 *   硅基流动:   { type: "openai", baseUrl: "https://api.siliconflow.cn/v1" }
 */
export interface ProviderConfig {
  /** Provider 类型 */
  type: ProviderType;
  /** API Key（也可通过环境变量设置） */
  apiKey?: string;
  /** API Base URL（OpenAI 兼容厂商必填） */
  baseUrl?: string;
  /** 模型 ID 映射：内部名 → 实际模型 ID
   *
   * 例：{ "claude-sonnet-4-6": "glm-4-plus" } 将 claude-sonnet-4-6 映射到智谱的 glm-4-plus
   */
  modelMap?: Record<string, string>;
}

// ─── 权限类型 ───────────────────────────────────────────────

export interface PermissionRule {
  tool: string;   // 工具名，如 "Bash"
  pattern: string; // 参数模式，如 "npm test *" 或 "*"
}

export interface Permissions {
  allow: PermissionRule[];
  deny: PermissionRule[];
}

export type PermissionDecision = "allow" | "deny";

// ─── 钩子类型 ───────────────────────────────────────────────

export type HookEvent = "PreToolUse" | "PostToolUse" | "Stop";

export interface HookHandler {
  type: "command";
  command: string;
  timeout?: number;
}

export interface HookMatcher {
  matcher: string;       // 工具名匹配模式
  hooks: HookHandler[];
}

// ─── 配置类型 ───────────────────────────────────────────────

export interface Config {
  model: string;
  maxTokens: number;
  maxIterations: number;
  /** Provider 配置（默认 anthropic） */
  provider: ProviderConfig;
  permissions: Permissions;
  hooks: Partial<Record<HookEvent, HookMatcher[]>>;
  /** @deprecated 使用 provider.apiKey 代替 */
  apiKey?: string;
}

// ─── LLM 调用接口 ──────────────────────────────────────────

/** LLM 调用参数（Provider 无关） */
export interface LLMCallParams {
  model: string;
  system: string;
  messages: Message[];
  tools: ToolDefinition[];
  maxTokens: number;
}

/** LLM 响应（Provider 无关） */
export interface LLMResponse {
  text: string;
  toolCalls: ToolUseContent[];
  stopReason: string;
}

// ─── Agent Loop 事件 ────────────────────────────────────────

export interface LoopEvent {
  type: "text" | "tool_call" | "tool_result" | "error" | "stop";
  data: unknown;
}

/**
 * OpenAI-compatible Provider — 支持 OpenAI 兼容 API
 *
 * 覆盖国内几乎所有厂商：
 *   智谱GLM:    baseUrl = "https://open.bigmodel.cn/api/paas/v4"
 *   DeepSeek:   baseUrl = "https://api.deepseek.com/v1"
 *   Moonshot:   baseUrl = "https://api.moonshot.cn/v1"
 *   通义Qwen:   baseUrl = "https://dashscope.aliyuncs.com/compatible-mode/v1"
 *   百川:       baseUrl = "https://api.baichuan-ai.com/v1"
 *   零一万物:   baseUrl = "https://api.lingyiwanwu.com/v1"
 *   硅基流动:   baseUrl = "https://api.siliconflow.cn/v1"
 *   OpenAI:     baseUrl = "https://api.openai.com/v1"（默认）
 *
 * 支持环境变量：
 *   OPENAI_API_KEY          — API Key（通用）
 *   OPENAI_BASE_URL         — Base URL（通用）
 *   DEEPSEEK_API_KEY        — DeepSeek 专用
 *   GLM_API_KEY             — 智谱 专用
 *   MOONSHOT_API_KEY        — Moonshot 专用
 *   DASHSCOPE_API_KEY       — 通义 专用
 */

import OpenAI from "openai";
import type { LLMProvider } from "./provider.js";
import type {
  LLMCallParams,
  LLMResponse,
  ToolUseContent,
  ToolResultContent,
  Message,
  ContentBlock,
  ToolDefinition,
  ProviderConfig,
} from "../types.js";

export class OpenAIProvider implements LLMProvider {
  readonly name: string;
  readonly type = "openai";

  private client: OpenAI;
  private modelMap: Record<string, string>;

  constructor(config: ProviderConfig) {
    const apiKey = config.apiKey || this.detectApiKey();
    if (!apiKey) {
      throw new Error(
        "OpenAI-compatible API Key not set.\n" +
        "Set one of:\n" +
        "  export OPENAI_API_KEY=your-key\n" +
        "  export DEEPSEEK_API_KEY=your-key\n" +
        "  export GLM_API_KEY=your-key\n" +
        "  export MOONSHOT_API_KEY=your-key\n" +
        "  export DASHSCOPE_API_KEY=your-key\n" +
        "Or set in config: provider.apiKey"
      );
    }

    const baseUrl = config.baseUrl || process.env.OPENAI_BASE_URL || "https://api.openai.com/v1";

    this.client = new OpenAI({
      apiKey,
      baseURL: baseUrl,
    });

    this.modelMap = config.modelMap || {};
    this.name = this.detectProviderName(baseUrl);
  }

  resolveModel(model: string): string {
    return this.modelMap[model] || model;
  }

  async call(params: LLMCallParams): Promise<LLMResponse> {
    const model = this.resolveModel(params.model);

    // 转换消息格式
    const messages = this.convertMessages(params.system, params.messages);

    // 转换工具格式
    const tools = this.convertTools(params.tools);

    const response = await this.client.chat.completions.create({
      model,
      max_tokens: params.maxTokens,
      messages,
      tools: tools.length > 0 ? tools : undefined,
    });

    const choice = response.choices[0];
    if (!choice) {
      return { text: "", toolCalls: [], stopReason: "end_turn" };
    }

    const message = choice.message;
    const text = message.content || "";
    const toolCalls: ToolUseContent[] = [];

    // 解析 tool_calls
    if (message.tool_calls) {
      for (const tc of message.tool_calls) {
        toolCalls.push({
          type: "tool_use",
          id: tc.id,
          name: tc.function.name,
          input: this.parseJson(tc.function.arguments),
        });
      }
    }

    // OpenAI stop_reason 映射
    const stopReason = choice.finish_reason === "tool_calls" ? "tool_use" : "end_turn";

    return { text, toolCalls, stopReason };
  }

  // ─── 格式转换 ─────────────────────────────────────────────

  /**
   * 将内部消息 + system prompt 转为 OpenAI 格式
   */
  private convertMessages(
    system: string,
    messages: Message[]
  ): OpenAI.ChatCompletionMessageParam[] {
    const result: OpenAI.ChatCompletionMessageParam[] = [];

    // System prompt
    if (system) {
      result.push({ role: "system", content: system });
    }

    for (const msg of messages) {
      // 分离 tool_use 和 tool_result，它们需要特殊处理
      const textParts: string[] = [];
      const toolUseParts: ToolUseContent[] = [];
      const toolResultParts: ToolResultContent[] = [];

      for (const block of msg.content) {
        if (block.type === "text") {
          textParts.push(block.text);
        } else if (block.type === "tool_use") {
          toolUseParts.push(block);
        } else if (block.type === "tool_result") {
          toolResultParts.push(block);
        }
      }

      // 处理 assistant 消息（可能包含 text + tool_calls）
      if (msg.role === "assistant") {
        if (toolUseParts.length > 0) {
          result.push({
            role: "assistant",
            content: textParts.join("") || null,
            tool_calls: toolUseParts.map((tc) => ({
              id: tc.id,
              type: "function" as const,
              function: {
                name: tc.name,
                arguments: JSON.stringify(tc.input),
              },
            })),
          });
        } else if (textParts.length > 0) {
          result.push({ role: "assistant", content: textParts.join("") });
        }
      }

      // 处理 user 消息
      if (msg.role === "user") {
        // tool_result 需要作为独立的 tool 消息
        for (const tr of toolResultParts) {
          result.push({
            role: "tool",
            tool_call_id: tr.tool_use_id,
            content: tr.is_error ? `Error: ${tr.content}` : tr.content,
          });
        }

        // 普通 user 文本
        if (textParts.length > 0) {
          result.push({ role: "user", content: textParts.join("") });
        }
      }
    }

    return result;
  }

  /**
   * 将内部 ToolDefinition[] 转为 OpenAI 格式
   */
  private convertTools(tools: ToolDefinition[]): OpenAI.ChatCompletionTool[] {
    return tools.map((tool) => ({
      type: "function" as const,
      function: {
        name: tool.name,
        description: tool.description,
        parameters: tool.input_schema,
      },
    }));
  }

  // ─── 辅助方法 ─────────────────────────────────────────────

  /**
   * 自动检测 API Key（按厂商优先级）
   */
  private detectApiKey(): string | undefined {
    return (
      process.env.OPENAI_API_KEY ||
      process.env.DEEPSEEK_API_KEY ||
      process.env.GLM_API_KEY ||
      process.env.MOONSHOT_API_KEY ||
      process.env.DASHSCOPE_API_KEY ||
      process.env.BAICHUAN_API_KEY ||
      process.env.SILICONFLOW_API_KEY ||
      undefined
    );
  }

  /**
   * 根据 baseUrl 推断 Provider 名称
   */
  private detectProviderName(baseUrl: string): string {
    if (baseUrl.includes("deepseek")) return "DeepSeek";
    if (baseUrl.includes("bigmodel") || baseUrl.includes("zhipu")) return "智谱GLM";
    if (baseUrl.includes("moonshot")) return "Moonshot";
    if (baseUrl.includes("dashscope") || baseUrl.includes("qwen")) return "通义Qwen";
    if (baseUrl.includes("baichuan")) return "百川";
    if (baseUrl.includes("lingyiwanwu") || baseUrl.includes("yi")) return "零一万物";
    if (baseUrl.includes("siliconflow")) return "硅基流动";
    if (baseUrl.includes("openai")) return "OpenAI";
    return `OpenAI-compatible(${baseUrl})`;
  }

  /**
   * 安全解析 JSON
   */
  private parseJson(str: string): Record<string, unknown> {
    try {
      return JSON.parse(str);
    } catch {
      return {};
    }
  }
}

/**
 * Anthropic Provider — 调用 Anthropic 官方 API（Claude 系列）
 *
 * 支持环境变量：
 *   ANTHROPIC_API_KEY       — API Key
 *   ANTHROPIC_BASE_URL      — 自定义 Base URL（可选，用于代理）
 */

import Anthropic from "@anthropic-ai/sdk";
import type { LLMProvider } from "./provider.js";
import type { LLMCallParams, LLMResponse, ContentBlock, ToolUseContent, ProviderConfig } from "../types.js";

export class AnthropicProvider implements LLMProvider {
  readonly name = "Anthropic";
  readonly type = "anthropic";

  private client: Anthropic;
  private modelMap: Record<string, string>;

  constructor(config: ProviderConfig) {
    const apiKey = config.apiKey || process.env.ANTHROPIC_API_KEY;
    if (!apiKey) {
      throw new Error(
        "Anthropic API Key not set.\n" +
        "Set environment variable: export ANTHROPIC_API_KEY=your-key\n" +
        "Or set in config: provider.apiKey"
      );
    }

    const options: { apiKey: string; baseURL?: string } = { apiKey };
    if (config.baseUrl || process.env.ANTHROPIC_BASE_URL) {
      options.baseURL = config.baseUrl || process.env.ANTHROPIC_BASE_URL;
    }

    this.client = new Anthropic(options);
    this.modelMap = config.modelMap || {};
  }

  resolveModel(model: string): string {
    return this.modelMap[model] || model;
  }

  async call(params: LLMCallParams): Promise<LLMResponse> {
    const model = this.resolveModel(params.model);

    const response = await this.client.messages.create({
      model,
      max_tokens: params.maxTokens,
      system: params.system,
      messages: this.serializeMessages(params.messages),
      tools: params.tools as Anthropic.Tool[],
    });

    const text: string[] = [];
    const toolCalls: ToolUseContent[] = [];

    for (const block of response.content) {
      if (block.type === "text") {
        text.push(block.text);
      } else if (block.type === "tool_use") {
        toolCalls.push({
          type: "tool_use",
          id: block.id,
          name: block.name,
          input: block.input as Record<string, unknown>,
        });
      }
    }

    return {
      text: text.join(""),
      toolCalls,
      stopReason: response.stop_reason || "end_turn",
    };
  }

  async *streamCall(params: LLMCallParams): AsyncGenerator<ContentBlock, void, void> {
    const model = this.resolveModel(params.model);
    const stream = this.client.messages.stream({
      model,
      max_tokens: params.maxTokens,
      system: params.system,
      messages: this.serializeMessages(params.messages),
      tools: params.tools as Anthropic.Tool[],
    });

    let currentToolUse: Partial<ToolUseContent> | null = null;

    for await (const event of stream) {
      if (event.type === "content_block_start") {
        if (event.content_block.type === "tool_use") {
          currentToolUse = {
            type: "tool_use",
            id: event.content_block.id,
            name: event.content_block.name,
            input: {},
          };
        }
      } else if (event.type === "content_block_delta") {
        if (event.delta.type === "text_delta") {
          yield { type: "text", text: event.delta.text };
        }
      } else if (event.type === "content_block_stop") {
        if (currentToolUse && currentToolUse.id) {
          yield {
            type: "tool_use",
            id: currentToolUse.id,
            name: currentToolUse.name || "",
            input: currentToolUse.input || {},
          } as ToolUseContent;
          currentToolUse = null;
        }
      }
    }
  }

  /**
   * 将内部 Message[] 转为 Anthropic SDK 格式
   */
  private serializeMessages(messages: import("../types.js").Message[]): Anthropic.MessageParam[] {
    const result: Anthropic.MessageParam[] = [];

    for (const msg of messages) {
      const content: Anthropic.ContentBlockParam[] = [];

      for (const block of msg.content) {
        if (block.type === "text") {
          content.push({ type: "text", text: block.text });
        } else if (block.type === "tool_use") {
          content.push({
            type: "tool_use",
            id: block.id,
            name: block.name,
            input: block.input as Record<string, unknown>,
          });
        } else if (block.type === "tool_result") {
          content.push({
            type: "tool_result",
            tool_use_id: block.tool_use_id,
            content: block.content,
            is_error: block.is_error,
          });
        }
      }

      result.push({ role: msg.role, content });
    }

    return result;
  }
}

/**
 * LLM Provider 抽象接口
 *
 * 所有 Provider（Anthropic、OpenAI-compatible）实现此接口
 * Agent Loop 只依赖此接口，不关心底层是哪家 API
 */

import type { LLMCallParams, LLMResponse, ContentBlock } from "../types.js";

export interface LLMProvider {
  /** Provider 名称（用于日志和显示） */
  readonly name: string;

  /** Provider 类型 */
  readonly type: string;

  /**
   * 非流式调用 — Agent Loop 主用
   */
  call(params: LLMCallParams): Promise<LLMResponse>;

  /**
   * 流式调用 — 逐块返回（可选，用于实时渲染）
   */
  streamCall?(params: LLMCallParams): AsyncGenerator<ContentBlock, void, void>;

  /**
   * 解析模型名 — 将内部模型名映射为实际模型 ID
   * 例：modelMap["claude-sonnet-4-6"] = "glm-4-plus"
   */
  resolveModel(model: string): string;
}

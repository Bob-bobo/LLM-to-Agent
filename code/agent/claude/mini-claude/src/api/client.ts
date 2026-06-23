/**
 * API Client — 兼容层
 *
 * 向后兼容：旧代码 `new APIClient(apiKey)` 仍然可用
 * 内部委托给 LLMProvider 实现
 *
 * 新代码应直接使用 createProvider() 创建 LLMProvider
 */

import type { LLMCallParams, LLMResponse, ProviderConfig } from "../types.js";
import type { LLMProvider } from "./provider.js";
import { createProvider } from "./factory.js";

export type { LLMCallParams, LLMResponse };

export class APIClient {
  private provider: LLMProvider;

  /**
   * 向后兼容构造：仍支持 apiKey 参数
   */
  constructor(apiKey?: string);

  /**
   * 新构造：传入 ProviderConfig
   */
  constructor(config: ProviderConfig);

  /**
   * Provider 注入：直接传入 LLMProvider 实例
   */
  constructor(provider: LLMProvider);

  constructor(arg?: string | ProviderConfig | LLMProvider) {
    if (!arg) {
      // 无参数 → 默认 Anthropic
      this.provider = createProvider({ type: "anthropic" });
    } else if (typeof arg === "string") {
      // apiKey 字符串
      this.provider = createProvider({ type: "anthropic", apiKey: arg });
    } else if ("call" in arg && "resolveModel" in arg) {
      // LLMProvider 实例
      this.provider = arg as LLMProvider;
    } else {
      // ProviderConfig 对象
      this.provider = createProvider(arg as ProviderConfig);
    }
  }

  /** 获取底层 Provider */
  getProvider(): LLMProvider {
    return this.provider;
  }

  /** 非流式调用 */
  async call(params: LLMCallParams): Promise<LLMResponse> {
    return this.provider.call(params);
  }

  /** 流式调用（如果 Provider 支持） */
  async *streamCall(params: LLMCallParams): AsyncGenerator<import("../types.js").ContentBlock, void, void> {
    if (this.provider.streamCall) {
      yield* this.provider.streamCall(params);
    } else {
      // 回退到非流式
      const response = await this.provider.call(params);
      if (response.text) {
        yield { type: "text", text: response.text };
      }
      for (const tc of response.toolCalls) {
        yield tc;
      }
    }
  }
}

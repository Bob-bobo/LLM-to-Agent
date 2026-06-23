/**
 * Provider 工厂 — 根据 ProviderConfig 创建对应的 LLMProvider
 *
 * 使用方式：
 *   const provider = createProvider(config.provider);
 *   const response = await provider.call(params);
 */

import type { LLMProvider } from "./provider.js";
import type { ProviderConfig } from "../types.js";
import { AnthropicProvider } from "./anthropic-provider.js";
import { OpenAIProvider } from "./openai-provider.js";

/**
 * 国内厂商预设配置 — 方便快速使用
 */
export const PROVIDER_PRESETS: Record<string, ProviderConfig> = {
  // ── 国际 ──────────────────────────────────────────────
  anthropic: {
    type: "anthropic",
  },
  openai: {
    type: "openai",
    baseUrl: "https://api.openai.com/v1",
  },

  // ── 国内厂商 ──────────────────────────────────────────
  deepseek: {
    type: "openai",
    baseUrl: "https://api.deepseek.com/v1",
  },
  glm: {
    type: "openai",
    baseUrl: "https://open.bigmodel.cn/api/paas/v4",
  },
  moonshot: {
    type: "openai",
    baseUrl: "https://api.moonshot.cn/v1",
  },
  qwen: {
    type: "openai",
    baseUrl: "https://dashscope.aliyuncs.com/compatible-mode/v1",
  },
  baichuan: {
    type: "openai",
    baseUrl: "https://api.baichuan-ai.com/v1",
  },
  yi: {
    type: "openai",
    baseUrl: "https://api.lingyiwanwu.com/v1",
  },
  siliconflow: {
    type: "openai",
    baseUrl: "https://api.siliconflow.cn/v1",
  },
};

/**
 * 创建 LLM Provider
 *
 * @param config - Provider 配置
 * @param preset - 预设名称（可选，覆盖 config 中的 baseUrl 等）
 */
export function createProvider(config: ProviderConfig, preset?: string): LLMProvider {
  // 合并预设
  let mergedConfig = config;
  if (preset && PROVIDER_PRESETS[preset]) {
    const presetConfig = PROVIDER_PRESETS[preset];
    mergedConfig = {
      ...presetConfig,
      ...config,
      // apiKey 优先用用户显式设置的
      apiKey: config.apiKey || undefined,
      // modelMap 合并
      modelMap: { ...presetConfig.modelMap, ...config.modelMap },
    };
  }

  // 也从环境变量检测 preset
  const envPreset = process.env.LLM_PROVIDER;
  if (envPreset && PROVIDER_PRESETS[envPreset] && !preset) {
    const presetConfig = PROVIDER_PRESETS[envPreset];
    mergedConfig = {
      ...presetConfig,
      ...mergedConfig,
      apiKey: mergedConfig.apiKey || undefined,
      modelMap: { ...presetConfig.modelMap, ...mergedConfig.modelMap },
    };
  }

  switch (mergedConfig.type) {
    case "anthropic":
      return new AnthropicProvider(mergedConfig);

    case "openai":
      return new OpenAIProvider(mergedConfig);

    default:
      throw new Error(
        `Unknown provider type: "${mergedConfig.type}". ` +
        `Supported: "anthropic", "openai"`
      );
  }
}

/**
 * 列出所有可用预设
 */
export function listPresets(): string[] {
  return Object.keys(PROVIDER_PRESETS);
}

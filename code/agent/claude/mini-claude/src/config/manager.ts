/**
 * 配置管理器 — 两层配置（User + Project）
 *
 * User:   ~/.mini-claude/settings.json
 * Project: .mini-claude/settings.json
 * 合并策略：Project > User
 */

import fs from "node:fs/promises";
import path from "node:path";
import os from "node:os";
import type { Config, Permissions, HookMatcher, HookEvent, ProviderConfig } from "../types.js";

const CONFIG_DIR = ".mini-claude";
const SETTINGS_FILE = "settings.json";

// 默认配置
const DEFAULT_CONFIG: Config = {
  model: "claude-sonnet-4-6",
  maxTokens: 16384,
  maxIterations: 50,
  provider: {
    type: "anthropic",
  },
  permissions: {
    allow: [
      { tool: "Read", pattern: "*" },
      { tool: "Glob", pattern: "*" },
      { tool: "Grep", pattern: "*" },
    ],
    deny: [
      { tool: "Bash", pattern: "rm -rf *" },
      { tool: "Bash", pattern: "curl * | * sh" },
    ],
  },
  hooks: {},
};

export class ConfigManager {
  private config: Config;
  private projectDir: string;

  constructor(projectDir?: string) {
    this.projectDir = projectDir || process.cwd();
    this.config = { ...DEFAULT_CONFIG };
  }

  /**
   * 加载配置（User → Project，后者覆盖前者）
   */
  async load(): Promise<Config> {
    // 1. 加载 User 配置
    const userConfigPath = path.join(os.homedir(), CONFIG_DIR, SETTINGS_FILE);
    const userConfig = await this.loadConfigFile(userConfigPath);

    // 2. 加载 Project 配置
    const projectConfigPath = path.join(this.projectDir, CONFIG_DIR, SETTINGS_FILE);
    const projectConfig = await this.loadConfigFile(projectConfigPath);

    // 3. 合并：DEFAULT → User → Project
    this.config = this.merge(
      this.merge(DEFAULT_CONFIG, userConfig),
      projectConfig
    );

    // 4. 环境变量覆盖
    this.applyEnvOverrides();

    return this.config;
  }

  /**
   * 获取当前配置
   */
  get(): Config {
    return { ...this.config };
  }

  /**
   * 更新配置
   */
  update(partial: Partial<Config>): void {
    this.config = this.merge(this.config, partial);
  }

  /**
   * 加载单个配置文件
   */
  private async loadConfigFile(filePath: string): Promise<Partial<Config>> {
    try {
      const content = await fs.readFile(filePath, "utf-8");
      return JSON.parse(content) as Partial<Config>;
    } catch {
      return {}; // 文件不存在或解析失败，返回空
    }
  }

  /**
   * 环境变量覆盖
   */
  private applyEnvOverrides(): void {
    // LLM_PROVIDER 环境变量
    const envProvider = process.env.LLM_PROVIDER;
    if (envProvider === "anthropic") {
      this.config.provider.type = "anthropic";
    } else if (envProvider === "openai") {
      this.config.provider.type = "openai";
    }

    // LLM_BASE_URL 环境变量
    const envBaseUrl = process.env.LLM_BASE_URL;
    if (envBaseUrl) {
      this.config.provider.baseUrl = envBaseUrl;
    }

    // LLM_API_KEY 环境变量（通用）
    const envApiKey = process.env.LLM_API_KEY;
    if (envApiKey) {
      this.config.provider.apiKey = envApiKey;
    }

    // LLM_MODEL 环境变量
    const envModel = process.env.LLM_MODEL;
    if (envModel) {
      this.config.model = envModel;
    }
  }

  /**
   * 深度合并两个配置对象
   */
  private merge(base: Config, override: Partial<Config>): Config {
    const result = { ...base };

    if (override.model !== undefined) result.model = override.model;
    if (override.maxTokens !== undefined) result.maxTokens = override.maxTokens;
    if (override.maxIterations !== undefined) result.maxIterations = override.maxIterations;

    // Provider 合并
    if (override.provider) {
      result.provider = this.mergeProvider(base.provider, override.provider);
    }

    // 兼容旧 apiKey 字段
    if (override.apiKey !== undefined) {
      result.provider.apiKey = override.apiKey;
    }

    // 权限合并：数组拼接
    if (override.permissions) {
      result.permissions = {
        allow: [
          ...(base.permissions?.allow || []),
          ...(override.permissions.allow || []),
        ],
        deny: [
          ...(base.permissions?.deny || []),
          ...(override.permissions.deny || []),
        ],
      };
    }

    // 钩子合并
    if (override.hooks) {
      result.hooks = { ...base.hooks };
      for (const [event, matchers] of Object.entries(override.hooks)) {
        if (matchers) {
          result.hooks[event as HookEvent] = [
            ...(base.hooks[event as HookEvent] || []),
            ...matchers,
          ];
        }
      }
    }

    return result;
  }

  /**
   * 合并 Provider 配置
   */
  private mergeProvider(base: ProviderConfig, override: Partial<ProviderConfig>): ProviderConfig {
    return {
      type: override.type || base.type,
      apiKey: override.apiKey || base.apiKey,
      baseUrl: override.baseUrl || base.baseUrl,
      modelMap: { ...base.modelMap, ...override.modelMap },
    };
  }
}

#!/usr/bin/env node

/**
 * Mini Claude Code — 入口
 *
 * 用法：
 *   mini-claude                                    # 交互模式（默认 Anthropic）
 *   mini-claude -p "fix bug"                      # 管道/非交互模式
 *   mini-claude --provider deepseek               # 使用 DeepSeek
 *   mini-claude --provider glm                    # 使用智谱 GLM
 *   mini-claude --provider openai --model gpt-4o  # 使用 OpenAI
 *   mini-claude --base-url https://xxx/v1         # 自定义 API 地址
 */

import { Command } from "commander";
import chalk from "chalk";
import type { Config, LoopEvent, ProviderConfig } from "./types.js";

// ─── 核心模块 ─────────────────────────────────────────────
import { ToolRegistry } from "./tools/registry.js";
import { readTool } from "./tools/read.js";
import { writeTool } from "./tools/write.js";
import { editTool } from "./tools/edit.js";
import { bashTool } from "./tools/bash.js";
import { globTool } from "./tools/glob.js";
import { grepTool } from "./tools/grep.js";
import { PermissionChecker } from "./permissions/checker.js";
import { ContextManager } from "./agent/context.js";
import { AgentLoop } from "./agent/loop.js";
import { ConfigManager } from "./config/manager.js";
import { MemoryLoader } from "./memory/loader.js";
import { HookRunner } from "./hooks/runner.js";
import { Renderer } from "./cli/render.js";
import { REPL } from "./cli/repl.js";
import { createSlashCommands } from "./cli/commands.js";
import { createProvider, listPresets, PROVIDER_PRESETS } from "./api/factory.js";
import type { LLMProvider } from "./api/provider.js";

// ─── 组装函数 ─────────────────────────────────────────────

/**
 * 组装所有模块，返回 AgentLoop 实例
 */
async function assemble(options: {
  model?: string;
  maxTokens?: number;
  maxIterations?: number;
  permissionMode?: "strict" | "permissive";
  projectDir?: string;
  provider?: string;
  baseUrl?: string;
  apiKey?: string;
}) {
  // 1. 配置
  const configManager = new ConfigManager(options.projectDir);
  const config = await configManager.load();

  // CLI 参数覆盖
  if (options.model) config.model = options.model;
  if (options.maxTokens) config.maxTokens = options.maxTokens;
  if (options.maxIterations) config.maxIterations = options.maxIterations;

  // 2. Provider 配置
  let providerConfig: ProviderConfig = { ...config.provider };

  // --provider 预设覆盖
  if (options.provider) {
    const preset = PROVIDER_PRESETS[options.provider];
    if (preset) {
      providerConfig = {
        ...preset,
        apiKey: options.apiKey || providerConfig.apiKey,
        modelMap: { ...preset.modelMap, ...providerConfig.modelMap },
      };
    } else {
      // 未识别的预设，当作 openai-compatible 处理
      console.log(chalk.yellow(`Unknown provider preset "${options.provider}", treating as custom openai-compatible.`));
      providerConfig.type = "openai";
    }
  }

  // --base-url 覆盖
  if (options.baseUrl) {
    providerConfig.baseUrl = options.baseUrl;
    // 如果有自定义 baseUrl 但 type 还是 anthropic，自动切换为 openai
    if (providerConfig.type === "anthropic" && !options.provider) {
      providerConfig.type = "openai";
    }
  }

  // --api-key 覆盖
  if (options.apiKey) {
    providerConfig.apiKey = options.apiKey;
  }

  // 3. 创建 Provider
  const provider: LLMProvider = createProvider(providerConfig);

  // 4. 工具注册
  const registry = new ToolRegistry();
  registry.registerAll([
    readTool,
    writeTool,
    editTool,
    bashTool,
    globTool,
    grepTool,
  ]);

  // 5. 权限校验
  const permissionChecker = new PermissionChecker(
    config.permissions,
    options.permissionMode || "strict"
  );

  // 6. 上下文管理
  const context = new ContextManager(config.maxTokens);

  // 7. 记忆加载
  const memoryLoader = new MemoryLoader(options.projectDir);

  // 8. 钩子运行器
  const sessionId = `session_${Date.now()}_${Math.random().toString(36).slice(2, 8)}`;
  const hookRunner = new HookRunner(config.hooks, sessionId);

  // 9. Agent Loop
  const agentLoop = new AgentLoop({
    config,
    provider,
    toolRegistry: registry,
    permissionChecker,
    context,
    hookRunner,
    memoryLoader,
  });

  return { agentLoop, config, context, configManager, provider };
}

// ─── CLI ──────────────────────────────────────────────────

const program = new Command();

program
  .name("mini-claude")
  .description("A minimal Claude Code clone — learn the agentic loop by doing")
  .version("0.1.0");

// 交互模式（默认）
program
  .option("-m, --model <model>", "LLM model to use")
  .option("--max-tokens <number>", "Max tokens per request", parseInt)
  .option("--max-iterations <number>", "Max agent loop iterations", parseInt)
  .option("--permissive", "Permissive permission mode (allow unmatched)", false)
  .option("-p, --prompt <prompt>", "Run in non-interactive mode with given prompt")
  .option("--project-dir <dir>", "Project directory (default: cwd)")
  .option(
    "--provider <name>",
    `LLM provider preset: ${listPresets().join(", ")}`
  )
  .option("--base-url <url>", "Custom API base URL (for OpenAI-compatible providers)")
  .option("--api-key <key>", "API key (or set env var)")
  .action(async (opts) => {
    try {
      const { agentLoop, config, context, provider } = await assemble({
        model: opts.model,
        maxTokens: opts.maxTokens,
        maxIterations: opts.maxIterations,
        permissionMode: opts.permissive ? "permissive" : "strict",
        projectDir: opts.projectDir,
        provider: opts.provider,
        baseUrl: opts.baseUrl,
        apiKey: opts.apiKey,
      });

      // 显示 Provider 信息
      console.log(chalk.dim(`Provider: ${provider.name} | Model: ${provider.resolveModel(config.model)}`));

      // 设置事件回调
      const setupEvents = (loop: AgentLoop) => {
        (loop as any).onEvent = (event: LoopEvent) => {
          const renderer = new Renderer();
          if (event.type === "tool_call") {
            const { name, input } = event.data as { name: string; input: Record<string, unknown> };
            renderer.toolCall(name, input);
          } else if (event.type === "tool_result") {
            renderer.toolResult(
              (event.data as any).name,
              event.data as { success: boolean; output: string; duration: number; error?: string }
            );
          } else if (event.type === "error") {
            renderer.error(String(event.data));
          }
        };
      };

      // 非交互模式
      if (opts.prompt) {
        setupEvents(agentLoop);
        const result = await agentLoop.run(opts.prompt);
        console.log(result);
        return;
      }

      // 交互模式
      const commands = createSlashCommands(context, config);
      setupEvents(agentLoop);

      const repl = new REPL(agentLoop, commands);
      await repl.start();
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : String(err);
      console.error(chalk.red(`Error: ${msg}`));
      process.exit(1);
    }
  });

program.parse();

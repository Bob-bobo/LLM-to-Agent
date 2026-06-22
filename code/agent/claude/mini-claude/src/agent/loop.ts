/**
 * Agent Loop — 核心智能体循环
 *
 * 这是 Mini Claude Code 的灵魂：
 *
 *   while (stopReason !== "end_turn") {
 *     response = callLLM(messages, tools)
 *     if (response.hasToolCalls) {
 *       for (toolCall of response.toolCalls) {
 *         checkPermission(toolCall)
 *         runHooks("PreToolUse", toolCall)
 *         result = executeTool(toolCall)
 *         runHooks("PostToolUse", toolCall, result)
 *         appendResult(result)
 *       }
 *     } else {
 *       return response.text
 *     }
 *   }
 */

import chalk from "chalk";
import type { Config, ContentBlock, ToolUseContent, LoopEvent, LLMCallParams } from "../types.js";
import type { LLMProvider } from "../api/provider.js";
import { ToolRegistry } from "../tools/registry.js";
import { ToolDispatcher, type DispatchResult } from "../tools/dispatcher.js";
import { PermissionChecker } from "../permissions/checker.js";
import { ContextManager } from "./context.js";
import { HookRunner } from "../hooks/runner.js";
import { MemoryLoader } from "../memory/loader.js";

// System Prompt 模板
const SYSTEM_PROMPT = `You are a helpful AI coding assistant with access to tools for reading, writing, editing files and running commands.

You operate in an agentic loop: you can use tools to explore the codebase, make changes, and verify results. When you have completed the user's request, respond with a final text message (without using any tools).

Key principles:
- Read files before editing them to understand the current content
- Use Edit for precise changes, Write for new files
- Run commands with Bash when needed (tests, builds, etc.)
- Be concise and focused in your responses`;

export interface AgentLoopOptions {
  config: Config;
  provider: LLMProvider;
  toolRegistry: ToolRegistry;
  permissionChecker: PermissionChecker;
  context: ContextManager;
  hookRunner: HookRunner;
  memoryLoader: MemoryLoader;
  onEvent?: (event: LoopEvent) => void; // 事件回调（用于 UI 渲染）
}

export class AgentLoop {
  private config: Config;
  private provider: LLMProvider;
  private toolRegistry: ToolRegistry;
  private dispatcher: ToolDispatcher;
  private permissionChecker: PermissionChecker;
  private context: ContextManager;
  private hookRunner: HookRunner;
  private memoryLoader: MemoryLoader;
  private onEvent?: (event: LoopEvent) => void;
  private aborted: boolean = false;

  constructor(options: AgentLoopOptions) {
    this.config = options.config;
    this.provider = options.provider;
    this.toolRegistry = options.toolRegistry;
    this.dispatcher = new ToolDispatcher(this.toolRegistry);
    this.permissionChecker = options.permissionChecker;
    this.context = options.context;
    this.hookRunner = options.hookRunner;
    this.memoryLoader = options.memoryLoader;
    this.onEvent = options.onEvent;
  }

  /**
   * 运行 Agent Loop — 核心入口
   */
  async run(prompt: string): Promise<string> {
    this.aborted = false;

    // 1. 添加用户消息到上下文
    this.context.addUserMessage(prompt);

    // 2. 构建 system prompt（包含 CLAUDE.md 指令）
    const systemPrompt = await this.buildSystemPrompt();

    // 3. 获取工具定义
    const tools = this.toolRegistry.getSchemas();

    // 4. 主循环
    let iteration = 0;
    const maxIterations = this.config.maxIterations;

    while (iteration < maxIterations && !this.aborted) {
      iteration++;

      // 4.1 检查上下文窗口
      if (this.context.isNearLimit()) {
        this.emit("text", chalk.yellow("[Context approaching limit, auto-compacting...]"));
        this.context.compact();
      }

      // 4.2 调用 LLM（通过 Provider 抽象层）
      this.emit("text", ""); // 空事件，UI 可用于显示 "thinking..."

      let response;
      try {
        const params: LLMCallParams = {
          model: this.config.model,
          system: systemPrompt,
          messages: this.context.getMessages(),
          tools,
          maxTokens: this.config.maxTokens,
        };
        response = await this.provider.call(params);
      } catch (err: unknown) {
        const msg = err instanceof Error ? err.message : String(err);
        this.emit("error", `API Error: ${msg}`);
        return `Error calling LLM: ${msg}`;
      }

      // 4.3 处理 LLM 响应
      const { text, toolCalls, stopReason } = response;

      // 构建助手消息内容块
      const assistantContent: ContentBlock[] = [];
      if (text) {
        assistantContent.push({ type: "text", text });
        this.emit("text", text);
      }
      for (const tc of toolCalls) {
        assistantContent.push(tc);
      }

      this.context.addAssistantMessage(assistantContent);

      // 4.4 如果没有工具调用或 LLM 停止，返回文本
      if (stopReason === "end_turn" || toolCalls.length === 0) {
        // 运行 Stop 钩子
        await this.hookRunner.run("Stop", {});
        return text || "(no response)";
      }

      // 4.5 执行工具调用
      for (const toolCall of toolCalls) {
        if (this.aborted) break;

        await this.executeToolCall(toolCall);
      }
    }

    if (this.aborted) {
      return "(aborted)";
    }

    return `(reached max iterations: ${maxIterations})`;
  }

  /**
   * 执行单个工具调用
   */
  private async executeToolCall(toolCall: ToolUseContent): Promise<void> {
    const { id, name, input } = toolCall;

    // 显示工具调用信息
    this.emit("tool_call", {
      id,
      name,
      input,
    });

    // 1. 权限检查
    const decision = this.permissionChecker.check(name, input);
    if (decision === "deny") {
      const msg = `Permission denied for ${name}`;
      this.emit("tool_result", { name, error: msg });
      this.context.addToolResult(id, msg, true);
      return;
    }

    // 2. PreToolUse 钩子
    const hookResult = await this.hookRunner.run("PreToolUse", {
      tool_name: name,
      tool_input: input,
    });
    if (hookResult.decision === "deny" || hookResult.decision === "block") {
      const msg = hookResult.reason || `Hook blocked ${name}`;
      this.emit("tool_result", { name, error: msg });
      this.context.addToolResult(id, msg, true);
      return;
    }

    // 3. 执行工具
    const result: DispatchResult = await this.dispatcher.dispatch(name, input);

    // 4. 显示结果
    this.emit("tool_result", {
      name,
      success: result.success,
      output: result.output,
      duration: result.duration,
    });

    // 5. PostToolUse 钩子
    await this.hookRunner.run("PostToolUse", {
      tool_name: name,
      tool_input: input,
      tool_result: result.output,
    });

    // 6. 追加结果到上下文
    this.context.addToolResult(id, result.output, !result.success);
  }

  /**
   * 构建 system prompt（含 CLAUDE.md 指令）
   */
  private async buildSystemPrompt(): Promise<string> {
    let prompt = SYSTEM_PROMPT;

    // 加载 CLAUDE.md 指令
    const memory = await this.memoryLoader.load();
    if (memory) {
      prompt += `\n\n# Project Instructions\n\n${memory}`;
    }

    return prompt;
  }

  /**
   * 中断循环
   */
  abort(): void {
    this.aborted = true;
  }

  /**
   * 发射事件
   */
  private emit(type: LoopEvent["type"], data: unknown): void {
    this.onEvent?.({ type, data });
  }
}

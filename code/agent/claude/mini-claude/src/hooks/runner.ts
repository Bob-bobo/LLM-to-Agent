/**
 * 钩子运行器 — 生命周期事件拦截
 *
 * 支持 PreToolUse / PostToolUse / Stop 三个事件点
 * 仅支持 command 类型（执行 shell 命令）
 *
 * 钩子输入通过 stdin 传入 JSON，输出从 stdout 解析
 */

import { exec, type ExecException } from "node:child_process";
import os from "node:os";
import type { HookEvent, HookMatcher, HookHandler } from "../types.js";

const DEFAULT_TIMEOUT = 30; // seconds
const SHELL = os.platform() === "win32" ? "cmd.exe" : "/bin/sh";

export interface HookInput {
  session_id: string;
  hook_event_name: HookEvent;
  tool_name?: string;
  tool_input?: Record<string, unknown>;
  tool_result?: string;
}

export interface HookOutput {
  decision?: "allow" | "deny" | "block";
  reason?: string;
  additionalContext?: string;
}

export class HookRunner {
  private hooks: Partial<Record<HookEvent, HookMatcher[]>>;
  private sessionId: string;

  constructor(
    hooks: Partial<Record<HookEvent, HookMatcher[]>>,
    sessionId: string
  ) {
    this.hooks = hooks;
    this.sessionId = sessionId;
  }

  /**
   * 运行指定事件的所有钩子
   */
  async run(event: HookEvent, input: Partial<HookInput>): Promise<HookOutput> {
    const matchers = this.hooks[event];
    if (!matchers || matchers.length === 0) {
      return {}; // 无钩子配置，直接通过
    }

    const fullInput: HookInput = {
      session_id: this.sessionId,
      hook_event_name: event,
      ...input,
    };

    for (const matcher of matchers) {
      // 检查 matcher 是否匹配当前工具
      if (input.tool_name && !this.matchToolName(matcher.matcher, input.tool_name)) {
        continue;
      }

      // 运行匹配的钩子处理器
      for (const handler of matcher.hooks) {
        const output = await this.runHandler(handler, fullInput);
        if (output.decision === "deny" || output.decision === "block") {
          return output; // 拒绝/阻塞，立即返回
        }
      }
    }

    return {}; // 所有钩子通过
  }

  /**
   * 运行单个钩子处理器
   */
  private runHandler(
    handler: HookHandler,
    input: HookInput
  ): Promise<HookOutput> {
    if (handler.type !== "command") {
      return Promise.resolve({}); // 仅支持 command 类型
    }

    const timeout = (handler.timeout || DEFAULT_TIMEOUT) * 1000;

    return new Promise((resolve) => {
      const child = exec(handler.command, {
        timeout,
        shell: SHELL,
        cwd: process.cwd(),
      }, (error: ExecException | null, stdout: string, stderr: string) => {
        if (error) {
          // 非零退出码
          if (error.killed) {
            resolve({ decision: "deny", reason: `Hook timed out after ${timeout}ms` });
          } else {
            // exit code 2 = blocking error
            if (error.code === 2) {
              resolve({ decision: "deny", reason: stderr || "Hook blocked" });
            } else {
              // exit code 1 = non-blocking error，继续执行
              resolve({});
            }
          }
          return;
        }

        // 解析 stdout JSON
        try {
          const output = JSON.parse(stdout.trim()) as HookOutput;
          resolve(output);
        } catch {
          // 非法 JSON，视为通过
          resolve({});
        }
      });

      // 通过 stdin 传入 JSON
      child.stdin?.write(JSON.stringify(input));
      child.stdin?.end();
    });
  }

  /**
   * 匹配工具名模式
   * 支持：精确匹配、|分隔列表、*通配符
   */
  private matchToolName(pattern: string, toolName: string): boolean {
    if (pattern === "*" || pattern === "") return true;

    // | 分隔列表
    if (pattern.includes("|")) {
      return pattern.split("|").some((p) => p.trim() === toolName);
    }

    return pattern === toolName;
  }
}

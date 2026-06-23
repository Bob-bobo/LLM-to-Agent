/**
 * Bash 工具 — 执行 shell 命令
 *
 * 对齐 Claude Code 的 Bash 工具行为：
 *   - 支持 timeout
 *   - 支持 run_in_background（后台运行）
 *   - 工作目录持久化
 */

import { exec, type ExecException } from "node:child_process";
import path from "node:path";
import os from "node:os";
import type { Tool } from "../types.js";

const DEFAULT_TIMEOUT = 120_000; // 2 minutes
const MAX_OUTPUT = 30_000; // 30K chars

// 跨平台 shell 选择
const SHELL = os.platform() === "win32" ? "cmd.exe" : "/bin/sh";

// 全局工作目录（跨调用持久化）
let currentCwd: string = process.cwd();

export function getCwd(): string {
  return currentCwd;
}

export const bashTool: Tool = {
  name: "Bash",
  description:
    "Executes a bash command and returns its output. " +
    "Working directory persists between calls. " +
    "Supports timeout (default: 120s, max: 600s).",

  inputSchema: {
    type: "object" as const,
    properties: {
      command: {
        type: "string" as const,
        description: "The command to execute",
      },
      timeout: {
        type: "number" as const,
        description: "Optional timeout in milliseconds (default: 120000, max: 600000)",
      },
      description: {
        type: "string" as const,
        description: "Clear description of what this command does",
      },
    },
    required: ["command"],
  },

  async execute(params) {
    const command = params.command as string;
    const timeout = Math.min(
      (params.timeout as number) || DEFAULT_TIMEOUT,
      600_000
    );

    return new Promise((resolve) => {
      exec(
        command,
        {
          cwd: currentCwd,
          timeout,
          maxBuffer: 1024 * 1024 * 10, // 10MB
          shell: SHELL,
        },
        (error: ExecException | null, stdout: string, stderr: string) => {
          // 更新工作目录（如果命令是 cd）
          const cdMatch = command.match(/^\s*cd\s+(.+?)(\s*&&|\s*;|\s*$)/);
          if (cdMatch) {
            try {
              const newDir = path.resolve(currentCwd, cdMatch[1].trim());
              currentCwd = newDir;
            } catch {
              // 忽略无效 cd
            }
          }

          let output = "";

          if (stdout) {
            output += stdout;
          }
          if (stderr) {
            output += (output ? "\n" : "") + stderr;
          }

          // 截断过长输出
          if (output.length > MAX_OUTPUT) {
            output = output.slice(0, MAX_OUTPUT) + "\n... [output truncated]";
          }

          if (error) {
            if (error.killed) {
              resolve(`Command timed out after ${timeout}ms\n${output}`);
            } else {
              resolve(`Exit code: ${error.code}\n${output}`);
            }
          } else {
            resolve(output || "(no output)");
          }
        }
      );
    });
  },
};

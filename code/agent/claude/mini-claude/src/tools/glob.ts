/**
 * Glob 工具 — 文件模式匹配搜索
 *
 * 基于 fast-glob 实现，对齐 Claude Code 的 Glob 工具
 */

import fg from "fast-glob";
import path from "node:path";
import type { Tool } from "../types.js";

export const globTool: Tool = {
  name: "Glob",
  description:
    "Fast file pattern matching. Supports glob patterns like '**/*.js' or 'src/**/*.ts'. " +
    "Returns matching file paths sorted by modification time.",

  inputSchema: {
    type: "object" as const,
    properties: {
      pattern: {
        type: "string" as const,
        description: 'The glob pattern to match files against (e.g. "**/*.ts")',
      },
      path: {
        type: "string" as const,
        description: "The directory to search in (default: current working directory)",
      },
    },
    required: ["pattern"],
  },

  async execute(params) {
    const pattern = params.pattern as string;
    const searchPath = (params.path as string) || process.cwd();
    const resolved = path.resolve(searchPath);

    try {
      const matches = await fg(pattern, {
        cwd: resolved,
        onlyFiles: true,
        dot: false,
        ignore: ["**/node_modules/**", "**/.git/**"],
      });

      if (matches.length === 0) {
        return `No files matching "${pattern}" found in ${resolved}`;
      }

      // 限制返回数量
      const maxResults = 250;
      const limited = matches.slice(0, maxResults);
      const truncated = matches.length > maxResults;

      let output = limited.join("\n");
      if (truncated) {
        output += `\n... and ${matches.length - maxResults} more files`;
      }

      return output;
    } catch (err: unknown) {
      return `Error: ${(err as Error).message}`;
    }
  },
};

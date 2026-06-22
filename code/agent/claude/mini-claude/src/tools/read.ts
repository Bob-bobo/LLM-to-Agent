/**
 * Read 工具 — 读取文件内容
 *
 * 支持：文本文件、分段读取（offset/limit）、目录列表
 * 对齐 Claude Code 的 Read 工具行为
 */

import fs from "node:fs/promises";
import path from "node:path";
import type { Tool } from "../types.js";

const MAX_LINES = 2000;

export const readTool: Tool = {
  name: "Read",
  description:
    "Reads a file from the local filesystem. " +
    "Returns content with line numbers (cat -n format). " +
    "Use offset/limit for large files. " +
    "Reading a directory returns its entries.",

  inputSchema: {
    type: "object" as const,
    properties: {
      file_path: {
        type: "string" as const,
        description: "The absolute path to the file to read",
      },
      offset: {
        type: "number" as const,
        description: "Line number to start reading from (1-based, default: 1)",
      },
      limit: {
        type: "number" as const,
        description: `Number of lines to read (default: ${MAX_LINES})`,
      },
    },
    required: ["file_path"],
  },

  async execute(params) {
    const filePath = params.file_path as string;
    const offset = (params.offset as number) || 1;
    const limit = (params.limit as number) || MAX_LINES;

    const resolved = path.resolve(filePath);

    // 检查是否是目录
    let stat;
    try {
      stat = await fs.stat(resolved);
    } catch {
      return `Error: File not found: ${resolved}`;
    }

    if (stat.isDirectory()) {
      const entries = await fs.readdir(resolved, { withFileTypes: true });
      const lines = entries.map((e) => {
        const suffix = e.isDirectory() ? "/" : e.isSymbolicLink() ? "@" : "";
        return `  ${e.name}${suffix}`;
      });
      return `Directory listing of ${resolved}:\n${lines.join("\n")}`;
    }

    // 读取文件内容
    const content = await fs.readFile(resolved, "utf-8");
    const allLines = content.split("\n");

    // 分段读取
    const startLine = Math.max(1, offset);
    const endLine = Math.min(allLines.length, startLine + limit - 1);
    const selectedLines = allLines.slice(startLine - 1, endLine);

    // cat -n 格式：行号 + tab + 内容
    const numbered = selectedLines
      .map((line, i) => `${startLine + i}\t${line}`)
      .join("\n");

    const header = `File: ${resolved} (lines ${startLine}-${endLine} of ${allLines.length})`;
    return `${header}\n${numbered}`;
  },
};

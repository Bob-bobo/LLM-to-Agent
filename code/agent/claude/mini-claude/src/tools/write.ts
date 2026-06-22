/**
 * Write 工具 — 创建或覆盖文件
 *
 * 对齐 Claude Code 的 Write 工具行为
 * 安全提示：覆盖已有文件前应确认
 */

import fs from "node:fs/promises";
import path from "node:path";
import type { Tool } from "../types.js";

export const writeTool: Tool = {
  name: "Write",
  description:
    "Writes a file to the local filesystem, overwriting if one exists. " +
    "For partial changes, use the Edit tool instead. " +
    "The file_path must be absolute.",

  inputSchema: {
    type: "object" as const,
    properties: {
      file_path: {
        type: "string" as const,
        description: "The absolute path to the file to write",
      },
      content: {
        type: "string" as const,
        description: "The content to write to the file",
      },
    },
    required: ["file_path", "content"],
  },

  async execute(params) {
    const filePath = params.file_path as string;
    const content = params.content as string;

    const resolved = path.resolve(filePath);

    // 确保目录存在
    const dir = path.dirname(resolved);
    await fs.mkdir(dir, { recursive: true });

    // 检查文件是否已存在
    let exists = false;
    try {
      await fs.access(resolved);
      exists = true;
    } catch {
      // 文件不存在
    }

    await fs.writeFile(resolved, content, "utf-8");

    const lineCount = content.split("\n").length;
    const action = exists ? "Overwrote" : "Created";
    return `${action} file ${resolved} (${lineCount} lines)`;
  },
};

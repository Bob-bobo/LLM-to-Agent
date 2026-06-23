/**
 * Edit 工具 — 精确字符串替换
 *
 * 核心原理：读取文件 → 查找 old_string → 替换为 new_string → 写回
 * 对齐 Claude Code 的 Edit 工具行为：
 *   - old_string 必须在文件中唯一匹配
 *   - replace_all: true 时替换所有匹配
 */

import fs from "node:fs/promises";
import path from "node:path";
import type { Tool } from "../types.js";

export const editTool: Tool = {
  name: "Edit",
  description:
    "Performs exact string replacement in a file. " +
    "old_string must match the file exactly (including indentation) and be unique. " +
    "For replacing all occurrences, set replace_all to true.",

  inputSchema: {
    type: "object" as const,
    properties: {
      file_path: {
        type: "string" as const,
        description: "The absolute path to the file to modify",
      },
      old_string: {
        type: "string" as const,
        description: "The text to replace (must match exactly)",
      },
      new_string: {
        type: "string" as const,
        description: "The text to replace it with",
      },
      replace_all: {
        type: "boolean" as const,
        description: "Replace all occurrences instead of just the first (default: false)",
      },
    },
    required: ["file_path", "old_string", "new_string"],
  },

  async execute(params) {
    const filePath = params.file_path as string;
    const oldString = params.old_string as string;
    const newString = params.new_string as string;
    const replaceAll = (params.replace_all as boolean) || false;

    const resolved = path.resolve(filePath);

    // 读取文件
    let content: string;
    try {
      content = await fs.readFile(resolved, "utf-8");
    } catch {
      return `Error: File not found: ${resolved}`;
    }

    // 检查 old_string 是否存在
    if (!content.includes(oldString)) {
      return `Error: old_string not found in file. Make sure the string matches exactly, including whitespace and indentation.`;
    }

    // 检查唯一性（非 replace_all 模式）
    if (!replaceAll) {
      const firstIdx = content.indexOf(oldString);
      const secondIdx = content.indexOf(oldString, firstIdx + 1);
      if (secondIdx !== -1) {
        return (
          `Error: old_string is not unique in the file (found multiple matches). ` +
          `Either provide more context to make it unique, or set replace_all to true.`
        );
      }
    }

    // 执行替换
    const newContent = replaceAll
      ? content.split(oldString).join(newString)
      : content.replace(oldString, newString);

    await fs.writeFile(resolved, newContent, "utf-8");

    // 统计替换次数
    const count = replaceAll
      ? content.split(oldString).length - 1
      : 1;

    return `Edited ${resolved}: replaced ${count} occurrence(s) of old_string with new_string`;
  },
};

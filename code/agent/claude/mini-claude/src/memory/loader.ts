/**
 * 记忆加载器 — CLAUDE.md 指令加载
 *
 * 加载顺序（从广到窄，后者覆盖前者）：
 *   1. ~/.mini-claude/CLAUDE.md  (用户全局)
 *   2. ./CLAUDE.md 或 ./.mini-claude/CLAUDE.md  (项目级)
 *
 * 注入位置：system prompt 之后，作为上下文消息
 */

import fs from "node:fs/promises";
import path from "node:path";
import os from "node:os";

const CONFIG_DIR = ".mini-claude";

export class MemoryLoader {
  private projectDir: string;

  constructor(projectDir?: string) {
    this.projectDir = projectDir || process.cwd();
  }

  /**
   * 加载所有 CLAUDE.md 内容，合并为一段文本
   */
  async load(): Promise<string> {
    const parts: string[] = [];

    // 1. 用户全局指令
    const userMemory = await this.readFile(
      path.join(os.homedir(), CONFIG_DIR, "CLAUDE.md")
    );
    if (userMemory) parts.push(userMemory);

    // 2. 项目根目录指令
    const projectMemory1 = await this.readFile(
      path.join(this.projectDir, "CLAUDE.md")
    );
    if (projectMemory1) parts.push(projectMemory1);

    // 3. 项目 .mini-claude 目录指令
    const projectMemory2 = await this.readFile(
      path.join(this.projectDir, CONFIG_DIR, "CLAUDE.md")
    );
    if (projectMemory2) parts.push(projectMemory2);

    if (parts.length === 0) {
      return "";
    }

    return parts.join("\n\n---\n\n");
  }

  /**
   * 解析 @import 语法（基础版）
   * 支持：@path/to/file 相对路径导入
   */
  async resolveImports(content: string, baseDir: string): Promise<string> {
    const importRegex = /@([\w./\-]+)/g;
    let match: RegExpExecArray | null;
    const replacements: Array<[string, string]> = [];

    while ((match = importRegex.exec(content)) !== null) {
      const importPath = path.resolve(baseDir, match[1]);
      const imported = await this.readFile(importPath);
      if (imported !== null) {
        replacements.push([match[0], imported]);
      }
    }

    let result = content;
    for (const [original, replacement] of replacements) {
      result = result.replace(original, replacement);
    }

    return result;
  }

  /**
   * 读取单个文件，失败返回 null
   */
  private async readFile(filePath: string): Promise<string | null> {
    try {
      const content = await fs.readFile(filePath, "utf-8");
      return content.trim() || null;
    } catch {
      return null;
    }
  }
}

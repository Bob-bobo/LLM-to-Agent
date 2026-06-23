/**
 * Grep 工具 — 文件内容搜索
 *
 * 使用 Node.js 实现（不依赖 ripgrep），对齐 Claude Code 的 Grep 工具
 * 支持正则表达式、glob 过滤、上下文行
 */

import fs from "node:fs/promises";
import path from "node:path";
import type { Tool } from "../types.js";

const MAX_RESULTS = 250;

export const grepTool: Tool = {
  name: "Grep",
  description:
    "Content search using regex. Supports glob filtering and context lines. " +
    "Returns matching lines with file paths and line numbers.",

  inputSchema: {
    type: "object" as const,
    properties: {
      pattern: {
        type: "string" as const,
        description: "The regular expression pattern to search for",
      },
      path: {
        type: "string" as const,
        description: "File or directory to search in (default: current directory)",
      },
      glob: {
        type: "string" as const,
        description: 'Glob pattern to filter files (e.g. "*.ts", "*.{ts,tsx}")',
      },
      output_mode: {
        type: "string" as const,
        enum: ["content", "files_with_matches", "count"],
        description: 'Output mode: "content" (with lines), "files_with_matches" (paths only), "count" (default: "files_with_matches")',
      },
      context: {
        type: "number" as const,
        description: "Number of context lines before and after each match",
      },
    },
    required: ["pattern"],
  },

  async execute(params) {
    const pattern = params.pattern as string;
    const searchPath = (params.path as string) || process.cwd();
    const globFilter = params.glob as string | undefined;
    const outputMode = (params.output_mode as string) || "files_with_matches";
    const contextLines = (params.context as number) || 0;

    const resolved = path.resolve(searchPath);

    // 编译正则
    let regex: RegExp;
    try {
      regex = new RegExp(pattern, "i");
    } catch {
      return `Error: Invalid regex pattern: ${pattern}`;
    }

    // 收集文件
    const files = await collectFiles(resolved, globFilter);
    if (files.length === 0) {
      return `No files found to search in ${resolved}`;
    }

    // 搜索
    const results: SearchResult[] = [];
    for (const file of files) {
      if (results.length >= MAX_RESULTS) break;
      await searchFile(file, regex, results, MAX_RESULTS, contextLines);
    }

    // 格式化输出
    return formatResults(results, outputMode, resolved);
  },
};

interface SearchResult {
  file: string;
  line: number;
  text: string;
  contextBefore: string[];
  contextAfter: string[];
}

async function collectFiles(dir: string, glob?: string): Promise<string[]> {
  const { glob: fg } = await import("fast-glob");
  const pattern = glob || "**/*";
  return fg(pattern, {
    cwd: dir,
    onlyFiles: true,
    dot: false,
    ignore: ["**/node_modules/**", "**/.git/**", "**/dist/**"],
    absolute: true,
  });
}

async function searchFile(
  filePath: string,
  regex: RegExp,
  results: SearchResult[],
  maxResults: number,
  contextLines: number
): Promise<void> {
  let content: string;
  try {
    content = await fs.readFile(filePath, "utf-8");
  } catch {
    return; // 跳过不可读文件
  }

  const lines = content.split("\n");
  for (let i = 0; i < lines.length; i++) {
    if (results.length >= maxResults) return;
    if (regex.test(lines[i])) {
      // 重置 lastIndex（因为有 'g' 标志的可能）
      regex.lastIndex = 0;

      results.push({
        file: filePath,
        line: i + 1,
        text: lines[i],
        contextBefore: contextLines > 0 ? lines.slice(Math.max(0, i - contextLines), i) : [],
        contextAfter: contextLines > 0 ? lines.slice(i + 1, i + 1 + contextLines) : [],
      });
    }
    regex.lastIndex = 0;
  }
}

function formatResults(
  results: SearchResult[],
  mode: string,
  basePath: string
): string {
  if (results.length === 0) {
    return "No matches found.";
  }

  if (mode === "files_with_matches") {
    const unique = [...new Set(results.map((r) => path.relative(basePath, r.file)))];
    return unique.join("\n");
  }

  if (mode === "count") {
    const counts = new Map<string, number>();
    for (const r of results) {
      const rel = path.relative(basePath, r.file);
      counts.set(rel, (counts.get(rel) || 0) + 1);
    }
    return Array.from(counts.entries())
      .map(([file, count]) => `${file}: ${count}`)
      .join("\n");
  }

  // content mode
  return results
    .map((r) => {
      const rel = path.relative(basePath, r.file);
      const before = r.contextBefore.map((l, i) => `  ${r.line - r.contextBefore.length + i}\t${l}`).join("\n");
      const match = `${r.line}\t${r.text}`;
      const after = r.contextAfter.map((l, i) => `  ${r.line + i + 1}\t${l}`).join("\n");
      const parts = [before, match, after].filter(Boolean);
      return `${rel}:\n${parts.join("\n")}`;
    })
    .join("\n\n");
}

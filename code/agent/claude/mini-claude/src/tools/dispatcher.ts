/**
 * 工具调度器 — 根据 LLM 返回的 tool_use 路由到对应实现
 *
 * 处理：查找工具 → 执行 → 错误捕获 → 返回结果
 */

import type { ToolRegistry } from "./registry.js";

export interface DispatchResult {
  success: boolean;
  output: string;
  duration: number; // ms
}

export class ToolDispatcher {
  private registry: ToolRegistry;

  constructor(registry: ToolRegistry) {
    this.registry = registry;
  }

  /**
   * 调度执行一个工具调用
   */
  async dispatch(
    toolName: string,
    toolInput: Record<string, unknown>
  ): Promise<DispatchResult> {
    const tool = this.registry.get(toolName);

    if (!tool) {
      return {
        success: false,
        output: `Unknown tool: "${toolName}". Available tools: ${this.registry.names().join(", ")}`,
        duration: 0,
      };
    }

    const start = Date.now();
    try {
      const output = await tool.execute(toolInput);
      return {
        success: true,
        output,
        duration: Date.now() - start,
      };
    } catch (err: unknown) {
      const message = err instanceof Error ? err.message : String(err);
      return {
        success: false,
        output: `Tool "${toolName}" failed: ${message}`,
        duration: Date.now() - start,
      };
    }
  }
}

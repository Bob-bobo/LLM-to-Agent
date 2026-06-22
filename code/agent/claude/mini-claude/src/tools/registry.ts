/**
 * 工具注册中心 — 统一注册、查询和 Schema 生成
 *
 * 每个 Tool 实现统一接口，Registry 管理所有工具的注册和查找
 */

import type { Tool, ToolDefinition } from "../types.js";

export class ToolRegistry {
  private tools: Map<string, Tool> = new Map();

  /**
   * 注册一个工具
   */
  register(tool: Tool): void {
    this.tools.set(tool.name, tool);
  }

  /**
   * 批量注册
   */
  registerAll(tools: Tool[]): void {
    for (const tool of tools) {
      this.register(tool);
    }
  }

  /**
   * 获取工具
   */
  get(name: string): Tool | undefined {
    return this.tools.get(name);
  }

  /**
   * 获取所有已注册工具名
   */
  names(): string[] {
    return Array.from(this.tools.keys());
  }

  /**
   * 生成 LLM API 所需的 tools 参数
   */
  getSchemas(): ToolDefinition[] {
    return Array.from(this.tools.values()).map((tool) => ({
      name: tool.name,
      description: tool.description,
      input_schema: tool.inputSchema,
    }));
  }

  /**
   * 检查工具是否存在
   */
  has(name: string): boolean {
    return this.tools.has(name);
  }
}

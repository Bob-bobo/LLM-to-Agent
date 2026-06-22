/**
 * 权限校验器 — allow/deny 规则匹配
 *
 * 规则语法：ToolName(param_pattern)
 *   - Bash(npm test)    → 精确匹配
 *   - Bash(npm *)       → 通配符匹配
 *   - Read(*)           → 允许所有 Read
 *   - *                 → 匹配所有工具
 *
 * 优先级：deny > allow > 默认策略
 */

import type { PermissionRule, Permissions, PermissionDecision } from "../types.js";

export class PermissionChecker {
  private permissions: Permissions;
  private defaultMode: "strict" | "permissive";

  constructor(
    permissions: Permissions,
    defaultMode: "strict" | "permissive" = "strict"
  ) {
    this.permissions = permissions;
    this.defaultMode = defaultMode;
  }

  /**
   * 检查工具调用是否被允许
   */
  check(toolName: string, toolInput: Record<string, unknown>): PermissionDecision {
    // 1. 检查 deny 规则（最高优先级）
    for (const rule of this.permissions.deny) {
      if (this.matchRule(rule, toolName, toolInput)) {
        return "deny";
      }
    }

    // 2. 检查 allow 规则
    for (const rule of this.permissions.allow) {
      if (this.matchRule(rule, toolName, toolInput)) {
        return "allow";
      }
    }

    // 3. 未匹配任何规则 → 使用默认策略
    return this.defaultMode === "permissive" ? "allow" : "deny";
  }

  /**
   * 匹配单个规则
   */
  private matchRule(
    rule: PermissionRule,
    toolName: string,
    toolInput: Record<string, unknown>
  ): boolean {
    // 工具名匹配
    if (rule.tool !== "*" && rule.tool !== toolName) {
      return false;
    }

    // 参数模式匹配
    const pattern = rule.pattern;
    if (pattern === "*" || pattern === "") {
      return true; // 匹配所有参数
    }

    // 获取工具的主要参数（用于匹配的字符串）
    const primaryArg = this.getPrimaryArg(toolName, toolInput);
    if (!primaryArg) {
      return false;
    }

    // 通配符匹配
    return this.wildcardMatch(pattern, primaryArg);
  }

  /**
   * 获取工具的主要参数字符串
   */
  private getPrimaryArg(
    toolName: string,
    toolInput: Record<string, unknown>
  ): string | null {
    switch (toolName) {
      case "Bash":
        return (toolInput.command as string) || null;
      case "Read":
      case "Write":
      case "Edit":
        return (toolInput.file_path as string) || null;
      case "Glob":
        return (toolInput.pattern as string) || null;
      case "Grep":
        return (toolInput.pattern as string) || null;
      default:
        return JSON.stringify(toolInput);
    }
  }

  /**
   * 通配符模式匹配
   * 支持 * 匹配任意字符序列
   */
  private wildcardMatch(pattern: string, text: string): boolean {
    // 将通配符模式转为正则
    const regexStr = pattern
      .replace(/[.+^${}()|[\]\\]/g, "\\$&") // 转义特殊字符
      .replace(/\*/g, ".*");                  // * → .*
    const regex = new RegExp(`^${regexStr}$`);
    return regex.test(text);
  }

  /**
   * 更新权限规则
   */
  update(permissions: Permissions): void {
    this.permissions = permissions;
  }
}

/**
 * 上下文管理器 — 对话历史 + 窗口控制
 *
 * 核心职责：
 *   - 维护消息历史
 *   - Token 估算 + 窗口限制
 *   - 超限时简单截断（保留 system prompt + 最近 N 轮）
 *   - 支持 compact / clear
 */

import type { Message, ContentBlock } from "../types.js";
import { estimateMessagesTokens } from "../api/tokens.js";

export class ContextManager {
  private messages: Message[] = [];
  private maxTokens: number;

  constructor(maxTokens: number = 200000) {
    this.maxTokens = maxTokens;
  }

  /**
   * 添加用户消息
   */
  addUserMessage(text: string): void {
    this.messages.push({
      role: "user",
      content: [{ type: "text", text }],
    });
  }

  /**
   * 添加助手消息（可能包含 text + tool_use）
   */
  addAssistantMessage(content: ContentBlock[]): void {
    this.messages.push({
      role: "assistant",
      content,
    });
  }

  /**
   * 添加工具结果消息
   * 注意：Anthropic API 要求 tool_result 放在 user 消息中
   */
  addToolResult(toolUseId: string, content: string, isError: boolean = false): void {
    // 查找或创建最后的 user 消息来放置 tool_result
    // 实际上 Anthropic API 允许 tool_result 作为独立 content block
    // 我们将其作为 user 消息中的一个 content block
    this.messages.push({
      role: "user",
      content: [{
        type: "tool_result",
        tool_use_id: toolUseId,
        content,
        is_error: isError,
      }],
    });
  }

  /**
   * 获取所有消息
   */
  getMessages(): Message[] {
    return this.messages;
  }

  /**
   * 获取当前 token 估算
   */
  getTokenCount(): number {
    return estimateMessagesTokens(this.messages);
  }

  /**
   * 检查是否接近上下文窗口限制
   */
  isNearLimit(threshold: number = 0.9): boolean {
    return this.getTokenCount() >= this.maxTokens * threshold;
  }

  /**
   * 简单截断压缩 — 保留最近 N 轮对话
   */
  compact(keepRounds: number = 10): void {
    if (this.messages.length <= keepRounds * 2) {
      return; // 不需要压缩
    }

    // 保留最近的 keepRounds 轮（每轮 = user + assistant）
    const kept = this.messages.slice(-keepRounds * 2);

    // 添加压缩摘要
    const removedCount = this.messages.length - kept.length;
    const summary: Message = {
      role: "user",
      content: [{
        type: "text",
        text: `[Context compacted: ${removedCount} earlier messages removed. Continuing with recent context.]`,
      }],
    };

    this.messages = [summary, ...kept];
  }

  /**
   * 清除所有历史
   */
  clear(): void {
    this.messages = [];
  }

  /**
   * 更新最大 token 数
   */
  setMaxTokens(max: number): void {
    this.maxTokens = max;
  }

  /**
   * 获取消息数量
   */
  get length(): number {
    return this.messages.length;
  }
}

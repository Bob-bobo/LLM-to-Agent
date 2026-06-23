/**
 * Token 估算 — 基于字符数的简单估算
 *
 * 规则：英文约 4 chars = 1 token，中文约 1.5 chars = 1 token
 * 精简版不做精确计算，仅用于上下文窗口粗略控制
 */

/**
 * 估算文本的 token 数
 */
export function estimateTokens(text: string): number {
  // 分离中文和非中文字符
  let cjkCount = 0;
  let otherCount = 0;

  for (const ch of text) {
    const code = ch.codePointAt(0)!;
    if (
      (code >= 0x4e00 && code <= 0x9fff) ||  // CJK Unified
      (code >= 0x3400 && code <= 0x4dbf) ||  // CJK Extension A
      (code >= 0x3000 && code <= 0x303f)     // CJK Symbols
    ) {
      cjkCount++;
    } else {
      otherCount++;
    }
  }

  // 中文 ~1.5 char/token，英文 ~4 char/token
  return Math.ceil(cjkCount / 1.5 + otherCount / 4);
}

/**
 * 估算消息数组的总 token 数
 */
export function estimateMessagesTokens(
  messages: { role: string; content: string | Array<{ type: string; text?: string; name?: string; input?: unknown; content?: string }> }[]
): number {
  let total = 0;

  for (const msg of messages) {
    // 每条消息有 ~4 token 的开销 (role, formatting)
    total += 4;

    if (typeof msg.content === "string") {
      total += estimateTokens(msg.content);
    } else if (Array.isArray(msg.content)) {
      for (const block of msg.content) {
        if (block.type === "text" && block.text) {
          total += estimateTokens(block.text);
        } else if (block.type === "tool_use") {
          total += estimateTokens(block.name || "");
          total += estimateTokens(JSON.stringify(block.input || {}));
        } else if (block.type === "tool_result" && block.content) {
          total += estimateTokens(block.content);
        }
      }
    }
  }

  return total;
}

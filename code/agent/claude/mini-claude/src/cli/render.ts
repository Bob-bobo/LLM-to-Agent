/**
 * 输出渲染器 — 终端彩色 + 工具调用可视化
 */

import chalk from "chalk";

export class Renderer {
  /**
   * 渲染文本块（LLM 输出）
   */
  text(content: string): void {
    if (!content) return;
    // 简单 Markdown 渲染：代码块高亮
    const lines = content.split("\n");
    let inCodeBlock = false;

    for (const line of lines) {
      if (line.startsWith("```")) {
        inCodeBlock = !inCodeBlock;
        console.log(chalk.dim(line));
        continue;
      }
      if (inCodeBlock) {
        console.log(chalk.cyan(line));
        continue;
      }
      console.log(line);
    }
  }

  /**
   * 渲染工具调用开始
   */
  toolCall(name: string, input: Record<string, unknown>): void {
    // 精简显示关键参数
    const summary = this.summarizeInput(name, input);
    console.log(
      chalk.blue(`  ⚙ ${name}`) + chalk.dim(`(${summary})`)
    );
  }

  /**
   * 渲染工具执行结果
   */
  toolResult(name: string, result: { success: boolean; output: string; duration: number; error?: string }): void {
    const icon = result.success ? chalk.green("✓") : chalk.red("✗");
    const time = chalk.dim(`${result.duration}ms`);

    if (result.error) {
      console.log(`  ${icon} ${chalk.red(name)}: ${chalk.red(result.error)} ${time}`);
    } else {
      // 截断长输出
      const output = result.output.length > 200
        ? result.output.slice(0, 200) + chalk.dim("...")
        : result.output;
      const displayOutput = output.split("\n")[0]; // 只显示第一行
      console.log(`  ${icon} ${chalk.gray(name)}: ${chalk.gray(displayOutput)} ${time}`);
    }
  }

  /**
   * 渲染错误
   */
  error(message: string): void {
    console.log(chalk.red(`  ✗ Error: ${message}`));
  }

  /**
   * 渲染系统消息
   */
  system(message: string): void {
    console.log(chalk.yellow(`  ℹ ${message}`));
  }

  /**
   * 渲染分隔线
   */
  separator(): void {
    console.log(chalk.dim("─".repeat(60)));
  }

  /**
   * 精简工具输入参数用于显示
   */
  private summarizeInput(name: string, input: Record<string, unknown>): string {
    switch (name) {
      case "Read":
        return this.truncate(String(input.file_path || ""));
      case "Write":
        return this.truncate(String(input.file_path || ""));
      case "Edit":
        return `${this.truncate(String(input.file_path || ""))}: "${this.truncate(String(input.old_string || ""), 30)}" → "${this.truncate(String(input.new_string || ""), 30)}"`;
      case "Bash":
        return this.truncate(String(input.command || ""));
      case "Glob":
        return this.truncate(String(input.pattern || ""));
      case "Grep":
        return `/${this.truncate(String(input.pattern || ""))}/`;
      default:
        return this.truncate(JSON.stringify(input), 50);
    }
  }

  private truncate(s: string, max: number = 60): string {
    return s.length > max ? s.slice(0, max) + "..." : s;
  }
}

/**
 * 交互式 REPL — 读取-求值-打印循环
 *
 * 支持斜杠命令、多行输入、优雅中断
 */

import readline from "node:readline";
import chalk from "chalk";
import type { AgentLoop } from "../agent/loop.js";
import type { SlashCommand } from "./commands.js";
import { Renderer } from "./render.js";

export class REPL {
  private agentLoop: AgentLoop;
  private commands: Map<string, SlashCommand>;
  private renderer: Renderer;
  private rl: readline.Interface | null = null;
  private running: boolean = false;

  constructor(
    agentLoop: AgentLoop,
    commands: Map<string, SlashCommand>
  ) {
    this.agentLoop = agentLoop;
    this.commands = commands;
    this.renderer = new Renderer();
  }

  /**
   * 启动 REPL
   */
  async start(): Promise<void> {
    this.running = true;

    this.rl = readline.createInterface({
      input: process.stdin,
      output: process.stdout,
      prompt: chalk.cyan("❯ "),
    });

    // 欢迎信息
    console.log(chalk.bold("\n🤖 Mini Claude Code") + chalk.dim(" — a minimal agentic coding tool\n"));
    console.log(chalk.dim("Type your request, or /help for commands.\n"));

    // Ctrl+C 处理
    let ctrlCCount = 0;
    this.rl.on("SIGINT", () => {
      ctrlCCount++;
      if (ctrlCCount >= 2) {
        console.log(chalk.yellow("\nGoodbye!"));
        this.stop();
        return;
      }
      console.log(chalk.yellow("\nPress Ctrl+C again to exit, or type /quit."));
      this.agentLoop.abort();
      this.rl?.prompt();
    });

    // 行输入处理
    this.rl.on("line", async (line) => {
      ctrlCCount = 0;
      const input = line.trim();

      if (!input) {
        this.rl?.prompt();
        return;
      }

      // 斜杠命令
      if (input.startsWith("/")) {
        await this.handleCommand(input);
        if (this.running) this.rl?.prompt();
        return;
      }

      // Agent Loop 执行
      await this.handlePrompt(input);
      if (this.running) this.rl?.prompt();
    });

    this.rl.prompt();
  }

  /**
   * 处理用户 prompt
   */
  private async handlePrompt(prompt: string): Promise<void> {
    console.log(); // 空行分隔

    try {
      const result = await this.agentLoop.run(prompt);
      this.renderer.separator();
      this.renderer.text(result);
      console.log();
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : String(err);
      this.renderer.error(msg);
    }
  }

  /**
   * 处理斜杠命令
   */
  private async handleCommand(input: string): Promise<void> {
    const parts = input.slice(1).split(/\s+/);
    const cmdName = parts[0];
    const args = parts.slice(1);

    const command = this.commands.get(cmdName);
    if (!command) {
      console.log(chalk.red(`Unknown command: /${cmdName}. Type /help for available commands.`));
      return;
    }

    const result = await command.execute(args);
    if (result === "__QUIT__") {
      this.stop();
      return;
    }
    if (result) {
      console.log(result);
    }
  }

  /**
   * 停止 REPL
   */
  stop(): void {
    this.running = false;
    this.rl?.close();
    process.exit(0);
  }
}

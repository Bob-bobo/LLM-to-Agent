/**
 * 斜杠命令处理
 */

import chalk from "chalk";
import type { ContextManager } from "../agent/context.js";
import type { Config } from "../types.js";

export interface SlashCommand {
  name: string;
  description: string;
  execute(args: string[]): Promise<string | void>;
}

export function createSlashCommands(
  context: ContextManager,
  config: Config,
  onConfigChange?: (config: Config) => void
): Map<string, SlashCommand> {
  const commands = new Map<string, SlashCommand>();

  commands.set("help", {
    name: "help",
    description: "Show available commands",
    async execute() {
      const lines = [
        chalk.bold("Available commands:"),
        "  /help       — Show this help",
        "  /compact    — Compact conversation history",
        "  /clear      — Clear all conversation history",
        "  /model      — Show or change model",
        "  /provider   — Show provider info",
        "  /config     — Show current configuration",
        "  /context    — Show context window usage",
        "  /quit       — Exit mini-claude",
      ];
      return lines.join("\n");
    },
  });

  commands.set("compact", {
    name: "compact",
    description: "Compact conversation history",
    async execute() {
      const before = context.length;
      context.compact();
      const after = context.length;
      return chalk.green(`Compacted: ${before} → ${after} messages`);
    },
  });

  commands.set("clear", {
    name: "clear",
    description: "Clear all conversation history",
    async execute() {
      context.clear();
      return chalk.green("Conversation history cleared.");
    },
  });

  commands.set("model", {
    name: "model",
    description: "Show or change model",
    async execute(args) {
      if (args.length > 0) {
        config.model = args[0];
        onConfigChange?.(config);
        return chalk.green(`Model changed to: ${config.model}`);
      }
      return `Current model: ${chalk.cyan(config.model)}`;
    },
  });

  commands.set("provider", {
    name: "provider",
    description: "Show provider info",
    async execute() {
      const p = config.provider;
      const lines = [
        chalk.bold("Provider configuration:"),
        `  Type:     ${chalk.cyan(p.type)}`,
        `  Base URL: ${chalk.dim(p.baseUrl || (p.type === "anthropic" ? "https://api.anthropic.com" : "(not set)"))}`,
        `  API Key:  ${chalk.dim(p.apiKey ? "••••" + p.apiKey.slice(-4) : "(from env)")}`,
      ];
      if (p.modelMap && Object.keys(p.modelMap).length > 0) {
        lines.push(`  Model map:`);
        for (const [from, to] of Object.entries(p.modelMap)) {
          lines.push(`    ${from} → ${chalk.cyan(to)}`);
        }
      }
      return lines.join("\n");
    },
  });

  commands.set("config", {
    name: "config",
    description: "Show current configuration",
    async execute() {
      const p = config.provider;
      return [
        chalk.bold("Current configuration:"),
        `  Provider:      ${chalk.cyan(p.type)}${p.baseUrl ? ` (${p.baseUrl})` : ""}`,
        `  Model:         ${chalk.cyan(config.model)}`,
        `  Max tokens:    ${config.maxTokens}`,
        `  Max iterations: ${config.maxIterations}`,
        `  Permissions:   ${config.permissions.allow.length} allow, ${config.permissions.deny.length} deny`,
        `  Hooks:         ${Object.keys(config.hooks).length} event(s)`,
      ].join("\n");
    },
  });

  commands.set("context", {
    name: "context",
    description: "Show context window usage",
    async execute() {
      const tokens = context.getTokenCount();
      const pct = ((tokens / 200000) * 100).toFixed(1);
      return [
        chalk.bold("Context window:"),
        `  Messages: ${context.length}`,
        `  Tokens:   ~${tokens} (${pct}% of 200K)`,
      ].join("\n");
    },
  });

  commands.set("quit", {
    name: "quit",
    description: "Exit mini-claude",
    async execute() {
      return "__QUIT__";
    },
  });

  return commands;
}

# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

HermesAgent is a native Android AI Agent application inspired by Claude Code's architecture. It implements a complete Agentic Loop with LLM integration, tool calling, and multi-step reasoning.

- **Language**: Kotlin 2.0
- **UI**: Jetpack Compose (Material 3)
- **Platform**: Android (minSdk 26, targetSdk 34)
- **Architecture**: Multi-module Android with clean architecture

## Build Commands

```powershell
# Build debug APK
./gradlew assembleDebug

# Build release APK
./gradlew assembleRelease

# Install debug to connected device
./gradlew installDebug

# Run unit tests
./gradlew test

# Run lint
./gradlew lint

# Clean build
./gradlew clean
```

## Code Structure

```
llm_app/HermesAgent/
├── app/                      # Main application - MainActivity, DI setup, navigation
├── feature-chat/             # Chat UI feature - ChatScreen, ChatViewModel, message components
├── feature-settings/         # Settings UI feature - LLM provider configuration
├── core-common/              # Shared utilities, theme, extensions
├── core-data/                # Data layer - Room DB, repositories, encrypted key storage
├── core-llm/                 # LLM integration - Provider interface, adapters, streaming
└── core-agent/               # Agent engine - Agentic Loop, planner, tool system
```

## Architecture

### Agentic Loop (core-agent)
1. `AgentEngine` orchestrates the multi-step ReAct loop
2. LLM is called with available tool definitions
3. If tool calls are returned → execute tools → add results → continue loop
4. If text only → return as final answer
5. Stops at max configurable steps

### Key Patterns
- **Tool System**: Each tool implements `name`, `description`, `inputSchema`, `execute()`. Registered in `ToolRegistry`, executed by `ToolExecutor`.
- **LLM Providers**: `LlmProvider` interface with adapter implementations for Anthropic, OpenAI, Gemini, and Chinese providers. Factory creates instances from config.
- **Dependency Injection**: Hilt is used throughout. Modules in `di/` packages.
- **Data Layer**: Repository pattern with Room for persistence, DataStore for preferences, encrypted keystore for API keys.

## Tech Stack

- **DI**: Hilt
- **DB**: Room
- **Network**: OkHttp
- **Async**: Coroutines + Flow
- **Navigation**: Navigation Compose
- **Security**: AndroidX Security (encrypted keys)
- **JS Execution**: Rhino (for CodeInterpreter tool)

## Module Dependencies

```
app → feature-chat, feature-settings, core-common, core-data
feature-* → core-common, core-llm, core-agent, core-data
core-agent → core-common, core-llm
core-llm → core-common
core-data → core-common
```

## Reference Documentation

- `CLAUDE_CODE_ARCHITECTURE.md` - Claude Code architecture reference
- `REQUIREMENTS_ANALYSIS.md` - Mini Claude Code requirements analysis

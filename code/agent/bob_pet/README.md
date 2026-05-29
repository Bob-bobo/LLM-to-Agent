# BobPet - Windows 桌面宠物伴侣

基于 [BobPet_Document.md](./BobPet_Document.md) 产品需求实现的 Windows 11 桌面猫娘挂件，支持本地 Ollama 与 OpenAI 兼容云端 API、工作状态感知、悬浮聊天与系统托盘。

## 功能概览

| 功能 | 快捷键 / 操作 |
|------|----------------|
| 桌面猫娘宠物（可拖动，位置记忆） | 拖动宠物 |
| 打开/关闭聊天气泡 | `Ctrl+Shift+M` |
| 思考模式 | `Ctrl+Shift+T` |
| 显示/隐藏宠物 | `Ctrl+Shift+H` |
| 打开聊天 | 双击宠物 |
| 设置 | 托盘菜单 → 设置 |
| 工作状态动画 | 自动监测 IDE（Cursor、VS Code 等） |

## 系统要求

- Windows 11（22H2+，Windows 10 亦可尝试）
- 内存 4GB+（本地模型建议 8GB+）
- 可选：[Ollama](https://ollama.com/download) 用于本地对话

## 快速开始（开发）

```powershell
cd bob_pet
npm install
npm start
```

首次启动会运行配置向导；配置保存在 `%APPDATA%\BobPet\`。

### 本地模型（Ollama）

```powershell
# 安装 Ollama 后
ollama pull llama3.2
ollama serve
```

在设置中选择「本地模型」，地址 `http://127.0.0.1:11434`，模型名 `llama3.2`。

### 云端 API

在设置中填写 OpenAI 兼容的 Base URL、API Key 与模型名即可。

## 打包安装（Windows）

### 方式一：便携版（推荐，无需 NSIS）

```powershell
npm install
npm run build:dir
powershell -ExecutionPolicy Bypass -File .\scripts\install-local.ps1
```

安装后会在桌面创建 **BobPet** 快捷方式，程序位于 `%LOCALAPPDATA%\Programs\BobPet\`。

也可直接运行 `dist\win-unpacked\BobPet.exe`，无需安装。

### 方式二：NSIS 安装程序

```powershell
npm run build
```

成功时生成 `dist\BobPet Setup 1.0.0.exe`。若下载 NSIS 组件失败，请使用方式一。

## 项目结构

```
bob_pet/
├── src/main/          # Electron 主进程（托盘、监控、模型网关）
├── src/renderer/      # 宠物、聊天、设置、向导界面
├── src/preload/       # 安全 IPC 桥接
├── personas/          # 人格适配器（neko.yaml）
└── BobPet_Document.md # 产品需求文档
```

## 配置文件

```
%APPDATA%\BobPet\
  ├── config.json
  ├── personas\
  └── logs\
```

## 许可证

MIT

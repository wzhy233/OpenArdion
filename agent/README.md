# Agent

这里存放 OpenCode CLI/TUI 及其运行依赖。来源和版本见 [UPSTREAM.json](UPSTREAM.json)，许可见 [LICENSE](LICENSE)。

## 构建

在 OpenArdion 根目录执行：

Windows：

```powershell
.\gradlew.bat buildAgent
```

Linux：

```sh
sh gradlew buildAgent
```

脚本使用 Bun 1.4.4，按锁文件安装依赖并生成两个平台的引擎：

```text
build/agent/windows-x64/ardion-engine.exe
build/agent/linux-x64/ardion-engine
```

Windows 脚本在 `build/tools/` 中准备 Bun，Linux 使用 `PATH` 中的 Bun。

## 集成

Gradle 将引擎和相关资源打包进模组。控制台由 `/ai open-cli` 打开，游戏内对话使用 `/ai <消息>`，模型设置通过 `/connect` 和 `/models` 管理。

配置、历史记录和缓存存放在 `~/.ardion/agent/opencode/`。CLI 与游戏内聊天分别保存会话，共用模型配置。

运行方式见 [Agent 文档](../docs/agent/opencode-runtime.md)，游戏接口见 [Minecraft 集成](../docs/agent/minecraft-integration.md)。

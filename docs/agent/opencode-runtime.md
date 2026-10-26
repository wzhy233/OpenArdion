# Agent 运行方式

Agent 源码位于 [agent/](../../agent/README.md)，基于 OpenCode。上游版本记录在 [UPSTREAM.json](../../agent/UPSTREAM.json)。

## 构建产物

`buildAgent` 使用 Bun 1.4.4 编译两个平台的引擎：

```text
build/agent/windows-x64/ardion-engine.exe
build/agent/linux-x64/ardion-engine
```

Gradle 将引擎、启动器、版本信息、校验文件和许可打包到模组的 `assets/ardion/native/`。`AgentRuntime` 负责提取和检查文件，`AgentConsole` 负责打开控制台。

## 使用

在游戏中执行 `/ai open-cli` 打开控制台，用 `/connect` 连接模型服务，用 `/models` 选择模型。

游戏聊天使用 `/ai <消息>`。`/ai stop` 和 `/ai cancel` 停止当前任务。回复会随生成过程显示在聊天栏中。

CLI 会话按游戏目录保存；游戏内 AI 对话的会话位于 `~/.ardion/agent/chat/`。二者共用模型配置。

## 数据目录

Agent 的配置、数据库和缓存位于 `~/.ardion/agent/opencode/`，数据库相对路径为 `data/opencode/opencode.db`。

控制台退出或游戏关闭时，启动器会结束对应引擎及其子进程。Java 集成入口包括：

- [AgentConsole.java](../../src/client/java/io/github/wzhy233/mcp/AgentConsole.java)
- [AgentRuntime.java](../../src/client/java/io/github/wzhy233/mcp/AgentRuntime.java)
- [AgentChat.java](../../src/client/java/io/github/wzhy233/mcp/AgentChat.java)

游戏工具见 [Minecraft 集成](minecraft-integration.md)，自动化组件见 [Voyager](voyager-tools.md)。

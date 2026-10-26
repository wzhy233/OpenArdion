<p align="center">
  <img src="src/main/resources/assets/ardion/icon.png" alt="OpenArdion" width="128" />
</p>

<h1 align="center">OpenArdion</h1>

<p align="center">Minecraft 1.21.11 Fabric 客户端模组</p>

<p align="center">
  <a href="https://github.com/wzhy233/OpenArdion/">GitHub</a> ·
  <a href="docs/getting-started.md">使用指南</a> ·
  <a href="CONTRIBUTING.md">参与开发</a> ·
  <a href="LICENSE">MIT</a>
</p>

## 功能

- 模块管理、自定义 HUD 和渲染效果。
- 物品栏、移动、战斗和世界交互模块。
- Lua 脚本加载与模块管理。
- 网易云音乐搜索、歌单、播放和歌词显示。
- 游戏内 AI 对话和 Agent 控制台。
- OpenHaedusIRC 聊天、在线列表和皮肤状态共享。

## 安装

运行环境：Minecraft 1.21.11、Fabric Loader 0.19.2 或以上、对应版本的 Fabric API，以及 Java 21 或以上。

将对应系统的模组 JAR 放入游戏实例的 `mods/` 目录。进入游戏后，按右 Shift 打开模块界面，也可以在游戏选项页点击 Ardion。

`/ardion help` 查看命令，`/ardion binds` 查看按键绑定。

AI 对话使用 `/ai <消息>`。首次使用时，执行 `/ai open-cli` 打开控制台，通过 `/connect` 连接模型服务，再用 `/models` 选择模型。`/ai stop` 停止当前任务。

详细步骤见 [使用指南](docs/getting-started.md)。聊天服务配置见 [OpenHaedusIRC](docs/OpenHaedusIRC.md)。

## 构建

| 系统 | 构建环境 |
| --- | --- |
| Windows x64 | JDK 21、Git |
| Linux x86_64 / glibc | JDK 21、Git、Bun 1.4.4、Python 3.11 或以上 |

在项目根目录执行：

Windows：

```powershell
.\gradlew.bat build
```

Linux：

```sh
sh gradlew build
```

Windows 脚本会在 `build/tools/` 中准备 Bun。首次构建需要联网下载依赖。

一次构建生成两个平台的模组：

```text
build/libs/OpenArdion-windows-x64.jar
build/libs/OpenArdion-linux-x64.jar
```

## 开发

Windows 使用 `.\gradlew.bat runClient`，Linux 使用 `sh gradlew runClient` 启动开发客户端。开发实例位于 `run/`。

| 目录 | 内容 |
| --- | --- |
| `src/main/` | 模组入口和公共资源 |
| `src/client/` | 模块、界面、音乐和 AI 集成 |
| `agent/` | Agent 源码 |
| `voyager/` | 游戏自动化组件 |
| `natives/prebuilt/` | 平台启动器 |
| `docs/` | 使用与开发文档 |

开发说明见 [CONTRIBUTING.md](CONTRIBUTING.md)。

## 许可

项目采用 [MIT License](LICENSE)。第三方组件保留各自的许可和署名。

感谢 Fabric、Skija、OpenCode 和 Voyager。

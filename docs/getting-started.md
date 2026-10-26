# 使用指南

[项目首页](../README.md)

## 安装模组

1. 创建 Minecraft 1.21.11 的 Fabric 实例，使用 Fabric Loader 0.19.2 或以上。
2. 在实例的 `mods/` 中放入对应版本的 Fabric API。
3. Windows 使用 `OpenArdion-windows-x64.jar`，Linux 使用 `OpenArdion-linux-x64.jar`，将文件放入同一目录。
4. 启动游戏，按右 Shift 打开模块界面，或在选项页点击 Ardion。

游戏运行使用 Java 21 或以上。替换模组版本时，移走旧 JAR，保留一份即可。

## 常用命令

| 命令 | 用途 |
| --- | --- |
| `/ardion help` | 查看命令 |
| `/ardion binds` | 查看模块按键 |
| `/ardion config save <名称>` | 保存配置 |
| `/ardion config load <名称>` | 加载配置 |
| `/ardion config list` | 列出配置 |
| `/ai <消息>` | AI 对话 |
| `/ai open-cli` | 打开 Agent 控制台 |
| `/ai stop` | 停止当前 AI 任务 |

尖括号中的内容替换成实际参数。

## AI 设置

执行 `/ai open-cli`，在控制台中通过 `/connect` 连接模型服务，通过 `/models` 选择模型。控制台和游戏聊天共用模型配置，各自保存会话。

配置和历史记录存放在用户目录下的 `.ardion/agent/opencode/`。运行方式见 [Agent 文档](agent/opencode-runtime.md)。

音乐、HUD、Lua 和其他模块在模块界面中管理。IRC 服务配置见 [OpenHaedusIRC](OpenHaedusIRC.md)。

## 环境准备

构建使用 JDK 21 和 Git。设置 `JAVA_HOME` 指向 JDK 根目录，将其 `bin` 目录加入 `PATH`。IDE 的 Gradle JVM 也选择 JDK 21。

### Windows

在项目根目录打开 PowerShell：

```powershell
java -version
javac -version
.\gradlew.bat build
```

脚本会下载 Bun 1.4.4 到 `build/tools/`。

### Linux

使用 x86_64 / glibc 环境，安装 Bun 1.4.4 和 Python 3.11 或以上。Bun 可从 [1.4.4 发布页](https://github.com/oven-sh/bun/releases/tag/bun-v1.4.4) 下载 `bun-linux-x64-baseline.zip`，解压后将 `bun` 所在目录加入 `PATH`。

```sh
java -version
javac -version
bun --version
python3 --version
sh gradlew build
```

两种系统执行一次 `build`，都会生成 Windows 和 Linux 两个 JAR，输出在 `build/libs/`。

## 构建任务

下表以 Windows 为例，Linux 使用 `sh gradlew` 替换 `.\gradlew.bat`。

| 命令 | 用途 |
| --- | --- |
| `.\gradlew.bat build` | 生成双平台模组 |
| `.\gradlew.bat runClient` | 启动开发客户端 |
| `.\gradlew.bat buildNatives` | 检查平台启动器 |
| `.\gradlew.bat buildAgent` | 构建两个平台的 Agent |
| `.\gradlew.bat buildVoyager` | 构建 Agent 并打包 Voyager |
| `.\gradlew.bat clean` | 清理构建产物 |

平台启动器位于 `natives/prebuilt/`，构建时自动打包。`*-sources.jar` 是源码归档，安装模组使用带平台名称的 JAR。

## 常见问题

### Java 版本错误

检查 `java -version`、`javac -version`、`JAVA_HOME` 和 IDE 的 Gradle JVM。切换 JDK 后，Windows 执行 `.\gradlew.bat --stop`，Linux 执行 `sh gradlew --stop`，再重新构建。

### 下载失败

根据报错检查 Gradle、Maven、GitHub 或包源的访问情况，以及各构建工具的代理设置。`--offline` 适用于依赖已经缓存的情况。

### 平台组件缺失或校验失败

从同一版本的源码恢复 `natives/prebuilt/` 中的文件，再执行 `buildNatives`。

### 模组加载失败

检查游戏、Fabric Loader、Fabric API 和 Java 版本，以及 JAR 对应的平台。确认 `mods/` 中只有一个 OpenArdion 版本。

### AI 连接失败

通过 `/ai open-cli` 查看报错，检查 `/connect` 和 `/models` 中的设置，以及模型服务的连接状态。

### 提交错误信息

附上系统与软件版本、复现步骤和相关日志。构建命令可加 `--stacktrace`，游戏日志位于实例的 `logs/latest.log`。

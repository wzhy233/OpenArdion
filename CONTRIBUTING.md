# 参与开发

环境配置见 [使用指南](docs/getting-started.md)。

## 报告问题

在 Issues 中附上系统、Java、Minecraft 和 Fabric 版本，说明复现步骤、预期结果和实际表现。构建问题贴出报错，界面问题附上截图或录屏。

## 修改代码

为本次修改创建分支，沿用所在目录的代码结构。客户端代码在 `src/client/`，Agent 在 `agent/`，游戏自动化组件在 `voyager/`。目录中有 `AGENTS.md` 时，先阅读其中的开发约定。

提交前执行构建，并在开发客户端中检查改动：

```powershell
.\gradlew.bat build
.\gradlew.bat runClient
```

Linux 使用 `sh gradlew` 替换 `.\gradlew.bat`。Agent 内部改动按 `agent/AGENTS.md` 执行对应包的检查。

Pull Request 中说明解决的问题、改动后的行为和检查结果。保留已有第三方许可与署名，提交文件以源码和文档为主。

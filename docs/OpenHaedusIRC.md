# OpenHaedusIRC

OpenArdion 通过 OpenHaedusIRC 收发聊天消息、在线列表和皮肤状态。

## 启动服务端

在 OpenHaedusIRC 项目目录中使用 JDK 21 和 Maven：

```sh
mvn package
java -jar target/OpenHaedusIRC.jar
```

默认地址是 `127.0.0.1:16667`。启动命令接受监听地址和端口两个参数，例如：

```sh
java -jar target/OpenHaedusIRC.jar 127.0.0.1 16668
```

## 连接客户端

在游戏启动器中添加 JVM 参数，地址和端口填服务端实际使用的值：

```text
-Dopenhaedusirc.host=127.0.0.1 -Dopenhaedusirc.port=16667
```

进入游戏后开启 IRC 模块。登录名使用启动游戏时的用户名。服务端命令有 `users`、`version` 和 `help`。

## 代码位置

客户端连接和消息收发在 [IRCClient.java](../src/client/java/io/github/wzhy233/ardion/irc/IRCClient.java)，状态字段在 [IRCPresence.java](../src/client/java/io/github/wzhy233/ardion/irc/IRCPresence.java)。

服务端入口为 `OpenHaedusIRC.java`，消息格式见服务端仓库的 `PROTOCOL.md`。

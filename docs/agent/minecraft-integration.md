# Minecraft 集成

Agent 通过本机 MCP 调用游戏功能。游戏操作在 Minecraft 线程上执行，相关代码位于 `src/client/java/io/github/wzhy233/mcp/`。

## 游戏工具

| 工具 | 用途 |
| --- | --- |
| `find_blocks` | 按注册 ID 查找已加载的方块 |
| `navigate` | 在已加载地形中寻路 |
| `mine_block` | 破坏方块，可选择随身工具 |
| `interact_block` | 方块交互、放置与打开容器 |
| `interact_entity` | 攻击或交互实体 |
| `use_item` | 使用手持物品 |
| `inventory_action` | 物品栏和容器操作 |
| `recipes` | 查询已解锁配方 |
| `craft_recipe` | 在当前合成界面填入材料并取出产物 |
| `wait_ticks` | 等待游戏 tick |
| `action_status` | 查询动作进度和结果 |
| `cancel_action` | 停止当前动作 |

长动作返回动作 ID，通过 `action_status` 查询结果。结果包含操作前后的状态和物品栏信息。

寻路范围为起点周围 64 格，最多搜索 4096 个节点，支持一格台阶和最多三格落差。路线中断、世界切换或动作超时会结束当前操作。

物品栏数据包含容器槽位、玩家槽位索引、光标物品、`syncId` 和 `revision`。操作容器时使用当前界面的 `syncId`。

## 模组工具

| 工具 | 用途 |
| --- | --- |
| `ardion_modules` | 查询模块和按键，切换模块，读写参数 |
| `ardion_settings` | 全局、HUD 和主题设置 |
| `ardion_config` | 保存和加载配置、HUD 布局 |
| `ardion_command` | 执行客户端命令并获取反馈 |
| `ardion_music` | 音乐搜索、歌单、播放、音量和歌词 |
| `client_inspect` | 查询类型和成员信息 |
| `client_threads` | 查看线程与锁栈 |
| `client_memory` | 查看内存统计 |
| `client_events` | 查看事件监听器耗时和错误 |

## JavaScript 程序

通过 Agent 引擎运行 `.mjs` 程序：

```text
ardion-engine.exe minecraft program.mjs
```

Linux 使用 `ardion-engine`。程序通过 `ARDION_MINECRAFT_API` 加载 SDK：

```js
const mc = await import(process.env.ARDION_MINECRAFT_API);
console.log(await mc.observe());
```

`mc.call()` 调用工具，`mc.run()` 等待长动作完成，`mc.observe()` 读取状态和物品栏，`mc.cancel()` 停止动作。

可复用程序和 `SKILL.md` 放在 `ARDION_SKILLS_DIR/<名称>/`，供后续会话使用。

独立机器人和组合动作见 [Voyager](voyager-tools.md)。

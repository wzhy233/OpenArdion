# Voyager

`voyager/` 包含 Mineflayer、寻路组件和来自 [MineDojo/Voyager](https://github.com/MineDojo/Voyager) 的动作代码。依赖版本记录在 `voyager/package.json` 和 `voyager/bun.lock`，构建时打包进模组。

## 选择控制对象

调用 `voyager` 工具时指定 `target`：

| 值 | 控制对象 |
| --- | --- |
| `client` | 当前游戏玩家 |
| `bot` | 独立的 Mineflayer 机器人 |

根据任务选择对象后，再调用相应动作。

## 连接机器人

在当前单人世界生成 Alex：

```json
{"target":"bot","action":"connect","args":[{"currentWorld":true}]}
```

客户端会打开或复用局域网连接，机器人进入世界后返回状态。离线机器人名称为 Alex，Microsoft 登录使用账号对应的玩家名。

连接其他服务器时，给 `connect` 传入连接参数，或使用 `~/.ardion/agent/voyager.json` 中的配置。当前玩家操作使用 `target: "client"`。

## 动作

动作由 `action` 指定，位置参数放在 `args` 数组中。

| 动作 | 用途 |
| --- | --- |
| `observe` | 查看状态 |
| `findBlocks`、`goto` | 查找方块、寻路 |
| `mineBlock`、`placeItem` | 挖掘、放置 |
| `craftItem`、`smeltItem` | 合成、烧炼 |
| `depositItemIntoChest`、`getItemFromChest`、`checkItemInsideChest` | 容器存取和查询 |
| `equip`、`consume` | 装备、使用物品 |
| `killMob`、`shoot` | 战斗 |
| `exploreUntil` | 探索 |
| `waitTicks` | 等待 tick |
| `execute` | 运行 JavaScript 程序 |
| `cancel`、`disconnect` | 停止动作、断开连接 |

`execute` 加载导出 `default async function(api)` 的 JavaScript 模块，可用于循环、回调和组合动作。工具返回动作结果、事件和观察数据。

`/ai stop` 停止当前任务及其对应的游戏操作。可复用程序放入 `~/.ardion/skills/`。

当前玩家工具说明见 [Minecraft 集成](minecraft-integration.md)。

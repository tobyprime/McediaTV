# Channel 网络包与控制流程

## 目标

本文档描述当前 `channel` 同步链路在客户端 mod 与服务端插件中的实际实现，重点覆盖：

- 当前使用的网络包
- 客户端 `attach / detach / subscribe / unsubscribe` 关系
- 服务端 `snapshot / remove / heartbeat` 处理流程
- 播放控制如何从服务端状态变更传播到客户端
- active audience 与多数派统计的当前口径

这是一份实现说明，与 `docs/player-channel-redesign.md` 的设计性文档互补。

---

## 1. 参与组件

### 客户端

- `MtvClientChannelPayloads`
  - 注册 C2S / S2C payload，并在连接建立与断开时清理本地 channel 状态
- `ClientChannelPlaybackManager`
  - 转发 snapshot / remove 到本地播放管理器
  - 负责发送 subscribe / unsubscribe
  - 管理 `channelId -> ClientChannelSession`
  - 管理最新 `snapshot` 缓存
  - 决定何时发送 subscribe / unsubscribe
- `ClientChannelSession`
  - 真正执行本地加载、seek、pause、speed 同步
  - 周期性发送 heartbeat

这里仍然保留 `session` 这个词，但它已经不再表示网络连接会话：
- `ClientChannelSession` 表示客户端本地某个 `channelId` 的播放运行时
- `AudienceSession` 表示服务端针对某个 `playerUuid + channelId` 维护的观测运行时
- `EntityPlayerHandle`
  - 根据实体配置决定当前应 attach 到哪个 `channelId`
  - 把 screen / speaker 外设挂到对应 `ClientChannelSession`

### 服务端

- `MtvChannelNetworkService`
  - 处理 subscribe / unsubscribe / heartbeat
  - 向客户端发送 snapshot / remove
- `AudienceSessionManager`
  - 维护 `player -> subscriptions`、`channel -> audience sessions`
  - 维护 active audience 过滤与多数派统计
- `MtvChannelService`
  - 维护 channel 权威状态
  - 负责持久化与触发 snapshot 广播
- `MtvPlaybackController`
  - 承接各种播放控制命令，并调用 `MtvChannelService`

---

## 2. 网络包一览

## 2.1 C2S

### 1. `channel_subscribe`

- 通道：`mcedia_mtv:channel_subscribe`
- 发送时机：某个 `channelId` 在客户端本地的使用计数从 **0 -> 1** 时
- 载荷：
  - `String channelId`
- 作用：
  - 表示“当前客户端开始需要这个 channel”
  - 服务端将该玩家标记为订阅该 channel
  - 服务端立刻回发当前 snapshot

注意：
- subscribe 不是按“某个实体 attach 一次就发一次”
- 而是按“整个客户端是否开始需要这个 channel”发送
- 同一个客户端内多个实体可复用同一个 `channelId`

### 2. `channel_unsubscribe`

- 通道：`mcedia_mtv:channel_unsubscribe`
- 发送时机：某个 `channelId` 在客户端本地的使用计数从 **1 -> 0** 时
- 载荷：
  - `String channelId`
- 作用：
  - 表示“当前客户端不再需要这个 channel”
  - 服务端取消该玩家对该 channel 的订阅

### 3. `channel_heartbeat`

- 通道：`mcedia_mtv:channel_heartbeat`
- 发送时机：客户端每 5 秒一次，只要该本地播放 session 仍有 attachment 且当前 snapshot 有媒体
- 载荷字段：
  - `String channelId`
  - `long revision`
  - `boolean loaded`
  - `boolean completed`
  - `long durationUs`

语义说明：
- 当本地媒体尚未 ready 时：
  - `loaded = false`
  - `completed = false`
  - `durationUs = 0`
- 当本地媒体已 ready 时：
  - `loaded = true`
  - `durationUs = 本地观测到的媒体时长`
- 当媒体完成播放时：
  - `completed = true`

当前实现不再使用独立的 `channel_hello` 或客户端自生成 `sessionId`。服务端直接以当前 `Player` 连接身份识别这组 C2S 包。

---

## 2.2 S2C

### 1. `channel_snapshot`

- 通道：`mcedia_mtv:channel_snapshot`
- 发送时机：
  - 服务端状态变化后
  - 新订阅该 channel 的客户端加入后
- 载荷字段：
  - `String channelId`
  - `long revision`
  - `String mediaUrl`
  - `float speed`
  - `long startAt`
  - `long baseTime`
  - `long baseOffset`
  - `String state`
  - `boolean paused`
  - `long resolvedDurationUs`
  - `boolean completed`

语义说明：
- snapshot 是客户端本地播放器的目标状态
- 客户端不会逐 tick 接收播放位置，而是基于 `baseTime / baseOffset / speed` 自行推算当前位置

### 2. `channel_remove`

- 通道：`mcedia_mtv:channel_remove`
- 发送时机：服务端认为某个 channel 应从客户端侧失效时
- 载荷：`String channelId`
- 作用：
  - 客户端清理该 channel 的 snapshot 缓存
  - 若本地已没有 attachment，则直接销毁对应 session

---

## 3. 客户端连接与本地状态流程

## 3.1 连接建立

1. 客户端注册所有 payload 与连接事件监听器
2. `JOIN` 时执行：
   - `ClientChannelPlaybackManager.clear()`
3. 之后客户端不需要单独握手
4. 当某个实体第一次 attach 某个 `channelId` 时，才发送 `channel_subscribe`

## 3.2 连接断开

1. 客户端 `DISCONNECT` 时执行 `clearAll()`
2. 本地销毁全部 `ClientChannelSession`
3. 清理全部 snapshot 缓存
4. 服务端在玩家退出事件中调用 `unregisterClient()`，移除该玩家的订阅与 audience 记录

---

## 4. 客户端 attach / detach 与订阅流程

## 4.1 本地 attach 来源

`EntityPlayerHandle.tick()` 每 tick 会读取实体配置并解析目标 `channelId`。

规则：
- 若显式绑定广播 channel，则使用该 `channelId`
- 否则默认使用 `self:<displayUuid>` 私有 channel

当 `desiredChannelId` 与当前 `channelId` 不同时：
1. 旧 channel `detach`
2. 新 channel `attach`
3. 重挂接 screen / speaker 外设

## 4.2 attach / detach 与 subscribe / unsubscribe 的关系

同一个客户端里可能有多个实体同时使用同一个 `channelId`。

因此客户端向服务端发包的粒度不是“实体动作”，而是“这个客户端是否仍然需要该 channel”。

当前规则：
- `attachments: 0 -> 1`
  - 发送 `channel_subscribe`
- `attachments: 1 -> 0`
  - 发送 `channel_unsubscribe`
  - 同时清理该 channel 的本地 snapshot 缓存
- `attachments: 1 -> 2` 或 `2 -> 1`
  - 不发包

这样可以避免：
- 重复订阅
- 一个实体 detach 后把仍在使用该 channel 的另一个实体误退订
- 重新切回一个已退订 channel 时先吃到本地陈旧 snapshot

## 4.3 snapshot 缓存

客户端在 `ClientChannelPlaybackManager` 中维护：
- `sessions: Map<String, ClientChannelSession>`
- `snapshots: Map<String, ClientChannelPlaybackSnapshot>`

行为：
- 收到 snapshot 时，先写入缓存
- 若本地 session 已存在，则立即更新 session
- attach 新 session 时，若缓存中已有该 channel 的 snapshot，则立即灌入
- 当某个 channel 退订到 `attachments == 0` 时，会删除该 channel 的本地 snapshot 缓存

这解决了：
- “服务端回 snapshot 快于本地 attach”的首包丢失问题
- “重新切回旧 channel 时优先使用本地陈旧 snapshot”的问题

---

## 5. 客户端播放执行流程

## 5.1 snapshot 应用

`ClientChannelSession.tick()` 的核心逻辑：

1. 若 `attachments == 0`
   - 停止播放并等待销毁
2. 若当前 snapshot 没有媒体
   - 停止当前媒体
3. 若 `mediaUrl` 变化
   - 进入 `loadingMedia = true`
   - 重新加载媒体
4. 若 `mediaUrl` 不变
   - 依据 snapshot 进行：
     - seek
     - pause / unpause
     - speed 同步

## 5.2 位置同步

客户端按以下规则推导目标位置：

```text
baseOffset + (now - baseTime) * speed
```

当 `paused = true` 时，不额外推进时间。

如果本地位置与目标位置差距超过阈值：
- `POSITION_SYNC_THRESHOLD_US = 10_000_000`
- 则执行 `seekAsync`

## 5.3 LOADING heartbeat

当前实现中，只要：
- 本地播放 session 仍有 attachment
- 当前 snapshot 有媒体
- 到了 5 秒心跳周期

就会尝试发送 heartbeat。

心跳状态判定如下：

### 本地媒体未 ready

- `loaded = false`
- `completed = false`
- `durationUs = 0`

### 本地媒体已 ready

- `loaded = true`
- `durationUs = 本地媒体时长`

### 本地媒体已结束

- `loaded = true`
- `completed = true`

这使服务端能够区分：
- 客户端尚在加载
- 客户端已真正完成加载
- 客户端已播放结束

---

## 6. 服务端订阅与 audience 会话模型

## 6.1 服务端维护的核心映射

`AudienceSessionManager` 当前只维护：

1. `sessionsByChannel`
   - `channelId -> Map<playerUuid, AudienceSession>`

其中 `AudienceSession` 保存：
- `playerUuid`
- `channelId`
- `lastHeartbeatAtMs`
- `lastRevision`
- `loaded`
- `completed`
- `durationMs`

## 6.2 active audience 定义

当前 active audience 统计必须满足：

1. `now - lastHeartbeatAtMs <= activeTimeoutMs`
2. `sessionsByChannel[channelId]` 中仍存在该玩家的 session

也就是说，只有**当前仍保留订阅 session、且 heartbeat 未过期**的 audience 才计入统计。

插件启动时会把：
- `AUDIENCE_TIMEOUT_MS = 30_000`

写入 `AudienceSessionManager` 作为 active 判定超时阈值。

## 6.3 周期性过期清理

插件以 20 tick 周期执行：
- `pruneExpired(nowMs)`

作用：
- 清理超时 audience 记录
- 清理空 channel 集合

---

## 7. 服务端 heartbeat 处理与多数派规则

## 7.1 heartbeat 处理流程

服务端收到 `channel_heartbeat` 后：

1. 先检查该玩家当前是否仍订阅 `heartbeat.channelId()`
   - 若未订阅，则忽略该 heartbeat
2. 将 `loaded / completed / durationUs` 写入 `AudienceSessionManager.touch(...)`
3. 若本次 heartbeat 没有带来状态变化，则直接结束
4. 若状态发生变化，则触发后续状态推进：
   - `maybeStartLoadedChannel(...)`（当多数派已 loaded）
   - `publishSnapshot(...)`（当 duration 发生变化）
   - `maybePauseCompletedChannel(...)`（当当前 heartbeat 标记 completed）

## 7.2 多数派 loaded 口径

当前 `isMajorityLoaded(channelId, revision, nowMs)` 的统计口径：

- 先筛选 active sessions
- 再筛选 `lastRevision == revision`
- 记为 `matched`
- 其中满足以下条件的记为 `loaded`：
  - `loaded == true`
  - `durationMs > 0`

成立条件：

```text
matched > 0 && loaded * 2 > matched
```

## 7.3 多数派 completed 口径

当前 `isCompleted(channelId, revision, nowMs)` 的统计口径：

- 先筛选 active sessions
- 再筛选 `lastRevision == revision`
- 记为 `matched`
- 其中 `completed == true` 的记为 `completed`

成立条件：

```text
matched > 0 && completed * 2 > matched
```

loaded / completed 现在已经统一使用同一套 active + revision 匹配口径。

## 7.4 duration 汇总口径

当前 `resolveDurationMs(channelId, revision, nowMs)`：

- 只统计 active sessions
- 只统计当前 revision
- 取这些 audience 上报 `durationMs` 的最大值

这个汇总值会写入 snapshot 的 `resolvedDurationUs`。

---

## 8. snapshot / remove 广播流程

## 8.1 snapshot 广播

服务端在以下场景会调用 `publishSnapshot(channelId)` 或 `publishSnapshotTo(player, channelId)`：

- 订阅成功后补发 snapshot
- channel 状态变更后
- heartbeat 补齐了 duration，导致 snapshot 内容需要刷新
- 播放列表推进或暂停状态变化后

广播过滤规则：
- 只发给当前仍订阅该 `channelId` 的玩家

## 8.2 remove 广播

当服务端认为某个 channel 无效时，会发送 `channel_remove`。

客户端收到后：
1. 删除对应 snapshot 缓存
2. 若本地 session 还存在，则将其 snapshot 置空
3. 如果本地已没有 attachment，则直接销毁该 session

服务端发送 `remove` 时，同样只发给当前仍订阅该 channel 的玩家。

---

## 9. 播放控制流

## 9.1 控制入口

当前播放控制入口在服务端：
- `MtvPlaybackController`

它将各种命令转发给 `MtvChannelService`，例如：
- `updateMediaUrl`
- `updateSpeed`
- `updateStartAt`
- `seekRelative`
- `togglePause`
- `playPlaylistIndex`
- `playNextManual`
- `playPreviousManual`

## 9.2 控制执行流程

一次典型的服务端播放控制路径如下：

1. GUI / 命令 / 交互入口调用 `MtvPlaybackController`
2. `MtvPlaybackController` 调用 `MtvChannelService`
3. `MtvChannelService.mutatePlayback(...)`：
   - 解析实体绑定到哪个 runtime channel
   - 载入该 channel 的 `ChannelRuntimeState`
   - 修改播放状态
   - `touch()` 增加 revision
   - 持久化 channel state
   - 将变更应用回当前实体快照
   - 调用 `onChannelChanged(channelId)`
4. `MtvChannelService` 的 change listener 已绑定到 `MtvChannelNetworkService.publishSnapshot`
5. 服务端将最新 snapshot 广播给订阅该 channel 的客户端
6. 客户端 `ClientChannelSession` 根据 snapshot 更新本地播放器

这个流程意味着：
- **服务端是权威状态写入者**
- **客户端播放器只是 snapshot 的执行者**
- **客户端 heartbeat 只负责观测与反馈，不直接写权威状态**

## 9.3 新媒体加载到正式播放的流程

当前流程如下：

1. 服务端把某个 channel 切到新 `mediaUrl`
2. `ChannelRuntimeState` 进入 `LOADING`
3. 服务端广播 snapshot
4. 客户端收到 snapshot，开始异步加载媒体
5. 客户端在媒体未 ready 前发送 `loaded=false` heartbeat
6. 客户端媒体 ready 后开始发送 `loaded=true` 且带有 `durationUs` 的 heartbeat
7. 当服务端判断“当前 revision 的 active audience 已多数 loaded”时：
   - `maybeStartLoadedChannel(channelId)`
   - 将服务端权威状态从 `LOADING` 切到 `PLAYING`
   - 再次广播 snapshot
8. 客户端按新的 `PLAYING` snapshot 正式进入统一播放状态

## 9.4 播放完成后的流程

1. 某些客户端开始上报 `completed=true`
2. 服务端根据 active audience 的多数派结果判断 `isCompleted`
3. 成立后：
   - 回写 resolved duration
   - 调用 `ChannelPlaylistAdvancer.advanceOrPause(...)`
4. 服务端推进播放列表或暂停
5. 广播新的 snapshot
6. 客户端按新 snapshot 切换到下一首或停住

---

## 10. 当前关键保证

当前实现通过以下机制保证链路更稳定：

1. **协议更简单**
   - 不再需要 `channel_hello` 与客户端自生成 `sessionId`
   - 心跳只保留 `loaded / completed / durationUs` 这些服务端真正消费的字段
2. **订阅与本地使用状态一致**
   - 通过 `attachments` 的 0/1 边界发送 subscribe / unsubscribe
3. **majority 统计不再混入旧订阅或超时 audience**
   - active audience 口径统一为：session 仍存在 + heartbeat 未过期
4. **加载阶段可观测**
   - 本地媒体未 ready 时也会上报 `loaded=false`
5. **duration 只在变化时触发额外 snapshot 广播**
   - 普通 heartbeat 不再无条件刷新 snapshot

---

## 11. 当前仍需注意的边界

1. `channel_remove` 当前仍是服务端主动失效语义，不等同于 unsubscribe
2. 该链路追求的是“状态一致 + 5 秒级收敛”，不是逐帧严格同步
3. 客户端是否 ready 仍通过本地是否拿到有效时长来判断；若未来遇到特殊媒体格式，可能需要更明确的 ready 信号
4. `MtvPlaybackController` 目前仍作为播放入口 facade 保留；如果后续 GUI/命令层继续收敛，可以再评估是否进一步折叠

# Channel 网络包与控制流程

## 目标

本文档描述当前 `channel` 同步链路在客户端 mod 与服务端插件中的**实际实现**，重点覆盖：

- 当前使用的网络包
- 客户端 session / 订阅 / 退订流程
- 服务端 snapshot / remove / heartbeat 处理流程
- 播放控制如何从服务端状态变更传播到客户端
- audience 多数派统计的当前口径

这是一份**实现说明**，与 `docs/player-channel-redesign.md` 的设计性文档互补。

---

## 1. 参与组件

### 客户端

- `MtvClientChannelPayloads`
  - 注册 C2S / S2C payload，并在连接建立与断开时初始化或清理会话
- `ClientChannelPlaybackManager`
  - 管理 `channelId -> ClientChannelSession`
  - 转发 `snapshot / sync / remove` 到本地 session
  - 决定何时发送 subscribe / unsubscribe
- `ClientChannelSession`
  - 真正执行本地加载、seek、pause、speed 同步
  - 周期性发送 heartbeat
- `EntityPlayerHandle`
  - 根据实体配置决定当前应 attach 到哪个 `channelId`
  - 把 screen / speaker 外设挂到对应 `ClientChannelSession`

### 服务端

- `MtvChannelNetworkService`
  - 处理 subscribe / unsubscribe / heartbeat
  - 向客户端发送 `snapshot / sync / remove`
- `AudienceSessionManager`
  - 维护 `channel -> audience sessions`
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
  - 服务端立刻回发当前 snapshot，并附带一次首包 `sync`

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
- 发送时机：客户端每 5 秒一次，只要该 session 仍有 attachment 且当前 snapshot 有媒体
- 载荷字段：
  - `String channelId`
  - `long revision`
  - `boolean loaded`
  - `boolean completed`
  - `long durationUs`
  - `boolean error`

语义说明：
- `loaded` 表示本地媒体是否已 ready
- `completed` 表示本地播放位置是否已到媒体结尾
- `durationUs` 为本地观测到的媒体时长
- `error` 表示客户端当前媒体加载或播放出现错误，服务端可据此发送强校准 `sync`

---

## 2.2 S2C

### 1. `channel_snapshot`

- 通道：`mcedia_mtv:channel_snapshot`
- 发送时机：
  - 服务端状态变化后
  - 服务端周期性广播，当前为约每 5 秒一次
  - 新订阅该 channel 的客户端加入后
- 载荷字段：
  - `String channelId`
  - `long revision`
  - `String mediaUrl`
  - `float speed`
  - `long anchorMediaTimeUs`
  - `long elapsedTimeMs`
  - `String state`
  - `boolean paused`
  - `long resolvedDurationUs`
  - `boolean completed`

语义说明：
- `anchorMediaTimeUs` 表示最近一次关键时间点的视频时间
- `elapsedTimeMs` 表示从该关键时间点到本次发包时，服务端已经流逝的时长
- 客户端收到包后，再按“本地收包后的单调时钟流逝时间 * speed”继续推进
- 普通 `snapshot` 是软刷新，主要用于定期纠偏与状态传播，不默认视为强制重同步

### 2. `channel_sync`

- 通道：`mcedia_mtv:channel_sync`
- 发送时机：
  - 新订阅该 channel 的客户端加入后，发送首个强校准包
  - 服务端判断需要显式强校准时发送，例如客户端 heartbeat 上报错误
- 载荷字段：与 `channel_snapshot` 相同
- 作用：
  - 客户端将其视为强校准信号
  - 仅在关键状态错误或明显异常时触发更积极的 seek/对齐

### 3. `channel_remove`

- 通道：`mcedia_mtv:channel_remove`
- 发送时机：服务端认为某个 channel 应从客户端侧失效时
- 载荷：`String channelId`
- 作用：
  - 客户端将该 channel 的 session snapshot 置空
  - 若本地已没有 attachment，则直接销毁对应 session

---

## 3. 客户端连接与 session 流程

## 3.1 连接建立

1. 客户端注册所有 payload 与连接事件监听器
2. `JOIN` 时执行：
   - `ClientChannelPlaybackManager.clear()`
3. 服务端不维护额外的会话握手
4. 后续客户端直接按 channel 维度发送 `subscribe / unsubscribe / heartbeat`

## 3.2 连接断开

1. 客户端 `DISCONNECT` 时执行 `clear()`
2. 本地销毁全部 `ClientChannelSession`
3. 服务端在玩家退出事件中调用 `unregisterClient()`，移除该玩家的订阅与 audience 记录

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

## 4.2 为什么 subscribe / unsubscribe 不按实体直接发

同一个客户端里可能有多个实体同时使用同一个 `channelId`。

因此客户端向服务端发包的粒度不是“实体动作”，而是“这个客户端是否仍然需要该 channel”。

当前规则：
- `attachments: 0 -> 1`
  - 发送 `channel_subscribe`
- `attachments: 1 -> 0`
  - 发送 `channel_unsubscribe`
- `attachments: 1 -> 2` 或 `2 -> 1`
  - 不发包

这样可以避免：
- 重复订阅
- 一个实体 detach 后把仍在使用该 channel 的另一个实体误退订

## 4.3 本地 session 管理

客户端在 `ClientChannelPlaybackManager` 中只维护：
- `sessions: Map<String, ClientChannelSession>`

行为：
- 收到 snapshot 或 sync 时，若本地 session 已存在，则立即更新 session
- 当前没有额外的 snapshot 缓存
- 首包由订阅后的服务端立即回包保证

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
     - pause/unpause
     - speed 同步

## 5.2 位置同步

客户端按以下规则推导目标位置：

```text
anchorMediaTimeUs + elapsedTimeMs * 1000 + localElapsedAfterReceiveMs * 1000 * speed
```

其中：
- `elapsedTimeMs` 是服务端从关键时间点到发包瞬间已经流逝的时长
- `localElapsedAfterReceiveMs` 是客户端从收到该包到当前 tick 的单调时钟流逝时间
- 当 `paused = true` 时，不额外推进时间

同步策略：
- 普通 `snapshot` 使用软阈值纠偏
- `channel_sync` 或 revision/mediaUrl/paused/speed 明显变化会标记为强校准
- 当前阈值：
  - `PERIODIC_SYNC_THRESHOLD_US = 3_000_000`
  - `FORCED_SYNC_THRESHOLD_US = 1_000_000`
- 超过阈值后执行 `seekAsync`

## 5.3 heartbeat

当前实现中，只要：
- session 仍有 attachment
- 当前 snapshot 有媒体
- 到了 5 秒心跳周期

就会尝试发送 heartbeat。

心跳字段判定如下：

### 本地媒体未 ready

- `loaded = false`
- `completed = false`
- `durationUs = 0`
- `error = false`

### 本地媒体已 ready

- `loaded = true`
- `durationUs = 本地媒体时长`
- 若本地播放位置已达到结尾，则 `completed = true`

这使服务端能够区分：
- 客户端尚在加载
- 客户端已真正完成加载
- 客户端已播放结束

---

## 6. 服务端订阅与 audience 会话模型

## 6.1 服务端维护的核心映射

`AudienceSessionManager` 维护：

- `sessionsByChannel`
  - `channelId -> Map<playerUuid, AudienceSession>`

其中 `AudienceSession` 保存：
- `playerUuid`
- `channelId`
- `lastHeartbeatAtMs`
- `lastRevision`
- `loaded`
- `completed`
- `error`
- `durationMs`

## 6.2 active audience 定义

当前 active audience 统计必须同时满足：

1. `now - lastHeartbeatAtMs <= activeTimeoutMs`
2. 该玩家仍订阅当前 `channelId`

也就是说，只有**当前仍订阅且 heartbeat 未过期**的 audience 才计入统计。

插件启动时会把：
- `AUDIENCE_TIMEOUT_MS = 30_000`

写入 `AudienceSessionManager` 作为 active 判定超时阈值。

## 6.3 周期性过期清理

插件以 20 tick 周期执行：
- `pruneExpired(nowMs)`

作用：
- 清理超时 audience session
- 清理空 channel 集合

---

## 7. 服务端 heartbeat 处理与多数派规则

## 7.1 heartbeat 处理流程

服务端收到 `channel_heartbeat` 后：

1. 校验该玩家是否仍订阅当前 `channelId`
2. 更新 `AudienceSessionManager.touch(...)`
3. 触发后续状态推进：
   - `maybeStartLoadedChannel(channelId)`
   - `publishSnapshot(channelId)`（当 `durationUs` 变化时）
   - `publishSync(channelId)`（当客户端上报 `error = true` 时）
   - `maybePauseCompletedChannel(channelId)`（当多数客户端 `completed = true` 时）

## 7.2 多数派 loaded 口径

当前 `summarize(channelId, revision, nowMs)` 的 loaded 统计口径：

- 先筛选 active sessions
- 跳过 `error = true` 的 session
- 再筛选 `lastRevision == revision`
- 记为 `matched`
- 其中 `loaded = true` 的记为 `loaded`

成立条件：

```text
matched > 0 && loaded * 2 > matched
```

## 7.3 多数派 completed 口径

当前 `summarize(channelId, revision, nowMs)` 的 completed 统计口径：

- 先筛选 active sessions
- 跳过 `error = true` 的 session
- 再筛选 `lastRevision == revision`
- 记为 `matched`
- 其中 `completed = true` 的记为 `completed`

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

## 8.1 snapshot / sync 广播

服务端在以下场景会调用 `publishSnapshot(channelId)` 或 `publishSnapshotTo(player, channelId)`：

- 订阅成功后补发 snapshot，并额外发送一次首包 `sync`
- channel 状态变更后
- heartbeat 补齐了 duration，导致 snapshot 内容需要刷新
- 播放列表推进或暂停状态变化后
- 周期性广播，当前为约每 5 秒一次

额外的 `sync` 只在以下场景发送：
- 新订阅客户端的首包强校准
- heartbeat 上报 `error = true`，服务端要求客户端做更积极的重新对齐

广播过滤规则：
- 只发给仍订阅该 `channelId` 的玩家

## 8.2 remove 广播

当服务端认为某个 channel 无效时，会发送 `channel_remove`。

客户端收到后：
1. 若本地 session 还存在，则将其 snapshot 置空
2. 如果本地已没有 attachment，则直接销毁该 session

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
5. 客户端在媒体未 ready 前发送 `loaded = false` heartbeat
6. 客户端媒体 ready 后开始发送带有 `loaded = true` 与 `durationUs` 的 heartbeat
7. 当服务端判断“当前 revision 的 active audience 已多数 loaded”时：
   - `maybeStartLoadedChannel(channelId)`
   - 将服务端权威状态从 `LOADING` 切到 `PLAYING`
   - 再次广播 snapshot
8. 客户端按新的 `PLAYING` snapshot 正式进入统一播放状态

## 9.4 播放完成后的流程

1. 某些客户端开始上报 `completed = true`
2. 服务端根据 active audience 的多数派结果判断 `completed`
3. 成立后：
   - 回写 resolved duration
   - 调用 `ChannelPlaylistAdvancer.advanceOrPause(...)`
4. 服务端推进播放列表或暂停
5. 广播新的 snapshot
6. 客户端按新 snapshot 切换到下一首或停住

---

## 10. 当前关键保证

当前实现通过以下机制保证链路更稳定：

1. **订阅与本地使用状态一致**
   - 通过 `attachments` 的 0/1 边界发送 subscribe / unsubscribe
2. **首包可立即对齐**
   - 订阅成功后服务端立即回发 snapshot，并附带一次首包 `sync`
3. **普通刷新与强校准分离**
   - 周期 snapshot 只做软刷新，`channel_sync` 只在首包和 error 场景下触发强校准
4. **majority 统计不再混入旧订阅或超时 audience**
   - active audience 口径统一为：当前订阅 + 未过期，并跳过 `error = true` 的 session
5. **加载阶段可观测**
   - 本地媒体未 ready 时也会上报 `loaded = false`

---

## 11. 当前仍需注意的边界

1. `channel_remove` 当前仍是服务端主动失效语义，不等同于 unsubscribe
2. `channel_sync` 当前只在首包和客户端 error 场景下发送，不会替代普通的周期 snapshot
3. 服务端多数逻辑仍以权威 timeline 和 duration 汇总为主，heartbeat 负责 loaded / completed / error 观测
4. 该链路追求的是“状态一致 + 5 秒级收敛”，不是逐帧严格同步

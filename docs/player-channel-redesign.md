# Player / Channel 实现设计

## 目标

本文档用于定义 McediaTV 在客户端 mod 与服务端插件分离架构下的播放模型、状态同步方式、生命周期和持久化边界。

本次设计优先解决以下问题：

- 明确 `channel`、`audience`、`player entity` 的职责边界
- 确立服务端权威的时间轴模型
- 将同步粒度控制在 5s 级，而不是逐 tick 强同步
- 支持广播 channel 与播放器私有 channel 两种形态
- 为后续 filesystem、sqlite、postgres、mongodb 等多种持久化实现预留统一抽象

## 总体架构

系统分为客户端 mod 与服务端插件两部分，以下统一称为 C/S。

### 服务端插件职责

- 维护 `channel` 的权威状态
- 管理 `audience` 的加入、心跳、超时与退出
- 管理 `player entity -> channel` 的绑定关系
- 维护播放列表推进逻辑
- 向客户端广播状态更新
- 将 channel 状态持久化到具体存储实现

### 客户端 mod 职责

- 维护多个播放器外设实例
- 将 screen / speaker 等外设挂接到指定 channel
- 接收服务端状态并在本地执行媒体加载、播放、暂停、跳转与调速
- 周期性发送心跳，并携带客户端观测到的播放状态
- 在媒体加载完成后向服务端上报媒体元信息

### 公共协议层职责

- 定义 C/S 之间的 packet
- 定义共享 DTO、状态枚举、错误语义
- 定义时间字段和媒体字段的统一格式

## 核心概念

### Channel

`channel` 是管理媒体播放进度、播放状态、播放列表与 audience 会话的核心对象。

`channel` 不负责：

- 实际视频解码
- 实际音频播放
- 持有客户端播放器实体实例

`channel` 负责：

- 维护权威播放状态
- 维护媒体时间轴锚点
- 维护当前媒体与播放列表游标
- 维护 audience 生命周期
- 在收到心跳时判断是否应切换到下一个媒体
- 将自身状态持久化

### Player Entity

`player entity` 是实际进行视频屏幕渲染、音频播放的实体。

在服务端上，它只是一组绑定数据与配置数据。
在客户端上，它可以维护多个外设实例，但这些实例不拥有权威播放状态。

`player entity` 可以绑定到 `channel`，但 `channel` 不反向持有实体本身，只需要知道绑定关系或绑定索引。

### Audience

`audience` 表示一个正在消费某个 `channel` 状态的客户端会话。

它负责：

- 接收服务端的 channel 状态
- 汇报客户端当前观测到的播放状态
- 通过 5 秒一次的心跳维持会话活性

如果某个 `channel` 在 30 秒内没有收到某个 audience 的心跳，则该 audience 自动断联。
如果某个 `channel` 丢失全部 audience，则该 `channel` 停止服务，但不删除持久化数据。

## Channel 类型

### 1. Broadcast Channel

广播 channel 允许多个播放器实体绑定到同一个 channel。

特性：

- 后续可加入 GUI 搜索与发现
- 允许多个播放器实体共享同一份播放状态
- 面向公共播放场景

### 2. Private Player Channel

播放器私有 channel 仅允许一个播放器实体绑定。

特性：

- 不可搜索
- 不可主动绑定
- 如果某个播放器实体没有绑定到任何公共 channel，则默认落在自己的私有 channel 上
- 虽然是私有 channel，但同样需要持久化

私有 channel 不是临时 fallback 对象，而是 channel 体系中的一等成员，只是能力受限。

## 服务端权威时间轴模型

### 核心原则

播放进度以服务端为权威，但服务端不会逐 tick 主动推进当前时间。

服务端只保存权威播放核心状态与时间轴锚点：

- `playState.mediaUrl`
- `playState.state`
- `playState.speed`
- `playState.mediaTime`
- `playState.playTime`

客户端收到这些数据后，使用本地时间自行推算当前播放位置。

### 时间字段定义

建议统一使用以下单位：

- 服务端系统时间：`epoch millis`
- 媒体时间：`millis`

### Channel Runtime State

一个运行中的 `channel` 至少需要维护以下字段：

- `channelId`
- `channelType`
- `playState`
- `duration`
- `playlistCursor`
- `playlist`
- `revision`

其中 `playState` 不是单一枚举，而是一组播放核心字段，至少包含：

- `mediaUrl`
- `state`
- `speed`
- `mediaTime`
- `playTime`

其中：

- `state`：服务端权威播放状态，枚举值为 `PLAYING`、`PAUSED`、`STOPPED`
- `speed`：当前播放倍率，例如 `1.0`、`1.5`
- `mediaTime`：`playTime` 这个时刻对应的媒体时间
- `playTime`：服务端系统时间，表示关键播放数据最近一次变化的时间点
- `duration`：当前媒体时长，由客户端加载完成后上报

### 当前播放位置推导

客户端或服务端在需要计算当前播放位置时，统一使用以下规则：

```text
if playState.state == PLAYING:
  currentMediaTime = (currentSystemTime - playState.playTime) * playState.speed + playState.mediaTime
else:
  currentMediaTime = playState.mediaTime
```

这里的 `currentSystemTime`：

- 客户端侧表示客户端当前系统时间
- 服务端侧表示服务端当前系统时间

### 时间轴更新规则

所有会影响时间轴的操作，本质上都遵循同一规则：

1. 先根据当前锚点结算出最新 `currentMediaTime`
2. 再更新目标状态
3. 重建新的时间轴锚点

#### 开始播放 / 恢复播放

- `playState.state = PLAYING`
- `playState.playTime = serverNow`
- `playState.mediaTime = 当前起播位置`

#### 暂停

- 先结算 `currentMediaTime`
- `playState.mediaTime = currentMediaTime`
- `playState.state = PAUSED`
- `playState.playTime = serverNow`

#### 跳转

- `playState.mediaTime = targetMediaTime`
- `playState.playTime = serverNow`
- `playState.state` 保持原状态或按命令指定

#### 调整播放速度

- 先结算 `currentMediaTime`
- `playState.mediaTime = currentMediaTime`
- `playState.speed = newRate`
- `playState.playTime = serverNow`

#### 切换到下一个媒体

- `playState.mediaUrl` 切换到下一个媒体
- `playState.mediaTime = 0`
- `playState.playTime = serverNow`
- 重置 `duration`
- `playState.state` 进入新的加载或待播状态
- `playlistCursor` 前进

## 播放列表推进与自动切歌

`channel` 本身不解析视频，因此无法自行知道媒体何时播放完成。

客户端在连接后加载完媒体，必须主动上报：

- 媒体时长
- 必要的媒体元信息

服务端拿到这些信息后，才能支持：

- 列表播放
- 结束切换
- 当前媒体越界判断

### 自动切歌触发点

服务端不做高频轮询。
自动切歌以心跳为主要触发点。

当服务端收到 audience 心跳时：

1. 根据服务端权威状态推导当前 `currentMediaTime`
2. 判断 `currentMediaTime >= duration`
3. 如果成立，则推进播放列表并切换到下一个视频

即：

```text
onHeartbeat:
  currentMediaTime = computeCurrentMediaTime(serverNow)
  if duration is known and currentMediaTime >= duration:
    advancePlaylist()
```

### 越界容忍

由于同步粒度是 5 秒级，`currentMediaTime` 超过 `duration` 几秒是正常现象。

设计上不追求精确停在最后一帧，而是采用“超过即视为结束”的策略。

### 幂等保护

媒体一旦结束，后续几个心跳仍可能继续命中 `currentMediaTime >= duration`。

因此服务端推进列表时必须具备幂等保护，避免一次结束触发多次切歌。

建议至少具备以下任一保护方式：

- 基于 `revision` 的状态前进校验
- 基于媒体实例序号的比较
- 基于一次性结束标记的防重入处理

## Audience 生命周期与心跳机制

### 加入

当客户端开始消费某个 channel 时，创建一个 `audience session`。

当前实现中，不再使用单独的 `channel_hello` / `sessionId` 握手；客户端通过首次 `subscribe(channelId)` 进入该 channel 的 audience 集合。

服务端在 audience 加入后，必须立即下发当前 channel 状态。

### 心跳

每个 audience 需要每 5 秒发送一次心跳。

心跳承担两个职责：

1. 保活
2. 状态观测上报

### 超时

如果某个 audience 30 秒内未发送心跳：

- 将该 audience 标记为断联
- 从对应 channel 的 audience 集合中移除

如果某个 channel 失去全部 audience：

- 该 channel 停止服务
- 但其持久化数据仍保留

## 客户端心跳上报内容

客户端心跳至少应包含以下信息：

- `channelId`
- `revision`
- `loaded`
- `completed`
- `duration`

其中：

- `loaded = true` 表示客户端本地媒体已经真正 ready，并拿到了有效时长
- `completed = true` 表示客户端本地媒体已经播放到结尾
- `duration` 为客户端当前观测到的媒体时长

需要注意：

- 客户端心跳现在只上报服务端真正消费的观测结果，不再传输 `state`、`clientMediaTime`、`clientPlaybackRate` 或 `loadedMediaId`
- 服务端权威状态不直接等于客户端上报状态
- 进度和最终切歌仍以服务端时间轴计算为准

## 服务端状态同步策略

### 基本原则

服务端不是周期性高频广播当前播放进度，而是仅在状态更新时下发同步。

但是，对新加入的 audience，也必须立即发送当前 channel 状态。

因此同步策略分为两类：

- 状态变更时：增量推送当前权威状态
- 新 audience 加入时：立即发送当前完整状态

### 推荐的同步内容

服务端同步包至少应包含：

- `channelId`
- `revision`
- `channelType`
- `playState`
  - `mediaUrl`
  - `state`
  - `speed`
  - `mediaTime`
  - `playTime`
- `duration`
- `playlistCursor`

### 推荐的触发场景

以下场景应触发服务端状态同步：

- 切换媒体
- 开始播放
- 暂停
- 跳转
- 调速
- 播放列表推进
- 新 audience 加入

以下场景不要求立刻广播：

- 普通心跳到达
- 客户端上报本地位置变化，但未引起服务端权威状态变化

## 控制模型

### 基本原则

客户端不能直接修改本地权威播放状态。
所有“设置播放链接、播放、暂停、停止、快进、快退、跳转、调速”等控制，都应先作为控制请求发送到服务端，由服务端修改 `channel.playState`，再通过状态同步广播给所有 audience。

统一控制流如下：

1. 客户端发起控制请求
2. 服务端校验目标 channel 与操作权限
3. 服务端根据当前 `playState` 先结算时间轴
4. 服务端应用本次控制命令并更新 `revision`
5. 服务端持久化最新 channel 状态
6. 服务端下发 `ChannelStateUpdatePacket`
7. 客户端收到更新后再执行本地播放器调整

这意味着：

- 客户端是控制发起者，但不是权威状态写入者
- 客户端 UI 可以表达“快进 10 秒”“切到 1.5 倍速”之类意图
- 真正落地到状态上的值，以服务端更新后的 `playState` 为准

### 推荐的控制命令

建议服务端统一建模以下控制命令：

- `SetMediaUrl`
- `Play`
- `Pause`
- `Stop`
- `Seek`
- `SetSpeed`
- `Next`
- `Previous`

其中：

- `SetMediaUrl`：设置当前媒体链接
- `Play`：从当前 `playState.mediaTime` 开始播放
- `Pause`：暂停当前媒体
- `Stop`：停止播放并回到起点或约定位置
- `Seek`：跳转到指定媒体时间
- `SetSpeed`：设置播放倍率
- `Next` / `Previous`：切换播放列表游标

### 设置播放链接

客户端如果要设置播放链接，不应直接在本地播放器上替换媒体后再尝试回写服务端。
正确流程应为：

1. 客户端发送 `SetMediaUrl(channelId, mediaUrl, ...)`
2. 服务端更新 `playState.mediaUrl`
3. 服务端将 `playState.mediaTime` 重置为 `0`
4. 服务端将 `playState.playTime` 设置为 `serverNow`
5. 服务端重置 `duration`
6. 服务端将 `playState.state` 设置为 `PLAYING`
7. 服务端递增 `revision` 并广播最新状态
8. 客户端收到新状态后开始加载媒体
9. 客户端加载完成后发送 `MediaInfoReportPacket`

这里最重要的是：

- 媒体链接切换是服务端状态切换，不是客户端本地行为
- 在新媒体完成加载前，服务端可以还不知道最终 `duration`

### 播放与暂停

#### 播放

客户端发起播放时：

1. 发送 `Play(channelId)`
2. 服务端设置：
   - `playState.state = PLAYING`
   - `playState.playTime = serverNow`
   - `playState.mediaTime` 保持当前结算后的位置
3. 广播更新
4. 客户端应用后开始本地推进

#### 暂停

客户端发起暂停时：

1. 发送 `Pause(channelId)`
2. 服务端先结算当前 `currentMediaTime`
3. 更新：
   - `playState.mediaTime = currentMediaTime`
   - `playState.state = PAUSED`
   - `playState.playTime = serverNow`
4. 广播更新

### 停止

客户端发起停止时：

1. 发送 `Stop(channelId)`
2. 服务端更新：
   - `playState.state = STOPPED`
   - `playState.mediaTime = 0`
   - `playState.playTime = serverNow`
3. 广播更新

`Stop` 的默认语义已经固定为“回到 `0` 并停住”。
如果后续你希望“停止后保留当前位置”，那应被定义成另一个命令，而不是复用 `Stop`。

### 快进、快退与拖动

从协议层看，快进、快退、本地拖动进度条，本质上都应该归一成 `Seek`。

也就是说：

- “快进 10 秒” = `Seek(current + 10s)`
- “快退 5 秒” = `Seek(current - 5s)`
- “拖到 01:23:45” = `Seek(targetTime)`

推荐协议只保留绝对跳转语义：

- `Seek(channelId, targetMediaTime)`

这样可以避免：

- 不同客户端对“当前时间”的理解不一致
- 相对快进命令在网络延迟下产生重复偏移
- 服务端难以做幂等和边界裁剪

服务端处理 `Seek` 时：

1. 根据当前状态先结算 `currentMediaTime`
2. 计算目标 `targetMediaTime`
3. 如果已知 `duration`，则将目标时间裁剪到合法范围
4. 更新：
   - `playState.mediaTime = targetMediaTime`
   - `playState.playTime = serverNow`
   - `playState.state` 保持原状态或按命令指定
5. 广播更新

所以客户端上的“快进”“快退”只是 UI 交互层概念，落到协议层都应变成一次标准 `Seek` 请求。

### 调速

客户端发起调速时：

1. 发送 `SetSpeed(channelId, newSpeed)`
2. 服务端先结算当前 `currentMediaTime`
3. 更新：
   - `playState.mediaTime = currentMediaTime`
   - `playState.speed = newSpeed`
   - `playState.playTime = serverNow`
4. `playState.state` 保持不变
5. 广播更新

调速必须先结算旧时间轴，再切换新倍率，否则会导致播放位置跳变。

### 客户端侧执行原则

客户端收到服务端下发的新 `playState` 后：

- 如果 `mediaUrl` 变化，则重新加载媒体
- 如果 `state` 从 `PAUSED/STOPPED` 变成 `PLAYING`，则开始或恢复播放
- 如果 `state` 变成 `PAUSED`，则暂停本地播放器
- 如果 `state` 变成 `STOPPED`，则停止本地播放器并回到起始位置
- 如果 `speed` 变化，则更新本地播放倍率
- 如果 `mediaTime` 或 `playTime` 变化，则按新的时间轴重新计算当前位置，必要时 seek

客户端不需要把每次按钮点击都立即本地生效后再纠正。
更稳妥的模型是：

- 先发送控制请求
- 等服务端返回新的权威状态
- 再按权威状态执行本地变更

### 协议建议补充

建议增加控制请求 packet：

- `ChannelControlRequestPacket`
  - 客户端向服务端发送播放控制请求

建议其至少包含：

- `channelId`
- `commandType`
- `payload`
- `expectedRevision`（可选）

其中：

- `commandType` 用于区分 `SetMediaUrl`、`Pause`、`Seek`、`SetSpeed` 等操作
- `payload` 用于携带 `mediaUrl`、`targetMediaTime`、`newSpeed` 等参数
- `expectedRevision` 可用于服务端做并发保护或提示客户端状态已过期

如果服务端拒绝执行控制请求，还可以补一个错误回包，例如：

- `ChannelControlRejectPacket`

用于表示：

- channel 不存在
- 客户端无权限控制该 channel
- 请求参数非法
- `expectedRevision` 不匹配

## 持久化模型

### 持久化目标

`channel` 需要持久化，且未来可以存在多种持久化基建实现，例如：

- filesystem
- sqlite
- postgres
- mongodb

因此服务端应定义统一的 `ChannelRepository` 抽象，而不是把业务逻辑绑定到某一种存储实现。

### 需要持久化的内容

建议持久化以下内容：

- `channelId`
- `channelType`
- 当前媒体标识
- `mediaTime`
- `playTime`
- `playbackRate`
- `playState`
- `duration`
- 播放列表
- `playlistCursor`
- 绑定关系所需的稳定标识

### 不建议持久化的内容

以下内容更适合作为运行时临时态：

- audience 当前在线集合
- 心跳最后到达时间
- 客户端瞬时观测值
- 仅用于会话期的临时缓存

### 停止服务与删除的区别

必须明确区分两个动作：

- 停止服务：运行态终止，但持久化记录仍保留
- 删除 channel：持久化数据被移除

失去全部 audience 只意味着停止服务，不意味着删除 channel。

## 服务端建议分层

### Channel Service

负责：

- 接收播放控制命令
- 串行修改 channel 状态
- 驱动播放列表前进
- 触发持久化与同步

### Audience Session Manager

负责：

- 维护 audience 加入、心跳与超时
- 维护 `channel -> audiences` 映射
- 在 audience 加入时触发初始状态下发

### Binding Registry

负责：

- 维护 `player entity -> channel` 的绑定关系
- 区分 broadcast 与 private channel 的绑定约束

### Channel Repository

负责：

- 保存 channel
- 读取 channel
- 删除 channel
- 列举可恢复 channel

## 客户端建议分层

### ClientChannelPlaybackManager

负责：

- 管理 `channelId -> playback session`
- 处理服务端状态同步
- 复用或销毁本地播放会话

### ClientChannelSession

负责：

- 按服务端状态加载媒体
- 基于本地时间推导当前播放位置
- 控制暂停、播放、跳转、调速
- 发送周期性心跳
- 在媒体加载完成时上报元信息

### Entity 外设挂载层

负责：

- 维护 screen / speaker 等外设实例
- 将外设连接到指定 channel session
- 在绑定变化时重挂接或断开

## 协议建议

建议至少存在以下类型的 packet：

- `ChannelControlRequestPacket`
  - 客户端向服务端发送播放控制请求
- `ChannelControlRejectPacket`
  - 服务端拒绝控制请求时返回错误
- `ChannelStateUpdatePacket`
  - 服务端状态更新时下发
- `ChannelStateSnapshotPacket`
  - 新 audience 加入时立即下发当前完整状态
- `AudienceHeartbeatPacket`
  - 客户端每 5 秒上报一次状态
- `MediaInfoReportPacket`
  - 客户端媒体加载完成后上报时长等元信息
- `BindChannelPacket`
  - 处理播放器实体绑定变更
- `UnbindChannelPacket`
  - 处理解绑

如果实现上最终希望减少 packet 类型，也可以保留统一结构，但语义上仍应区分快照与更新两种发送场景。

## 关键边界条件

### 1. 未拿到 duration

如果客户端尚未上报媒体时长，则服务端不能做基于时长的自动切歌判断。

### 2. 心跳延迟或抖动

心跳是 5 秒级同步机制，不保证精确到帧。
只要服务端最终权威状态一致即可。

### 3. 多客户端对同一 broadcast channel 的弱同步

该设计不追求强制逐帧同步。
服务端只在状态变更时重置权威时间轴，客户端自然按本地时间推进。

### 4. 状态漂移

客户端与服务端之间允许存在小范围漂移。
只有在服务端明确下发暂停、跳转、调速或切换媒体时，才要求客户端重新对齐。

## 实现优先级建议

建议分阶段推进：

1. 先实现服务端权威时间轴模型
2. 再实现 `AudienceHeartbeatPacket` 与 `MediaInfoReportPacket`
3. 再实现新 audience 加入时的状态快照下发
4. 再实现播放列表推进与自动切歌
5. 最后抽出 `ChannelRepository`，接入多种持久化实现

## 当前仍待你继续拍板的问题

以下问题在编码前最好继续明确：

1. `private channel` 的 `channelId` 生成规则是什么？
   - 绑定实体 UUID
   - 玩家 UUID
   - 单独的持久化主键

2. 播放列表切到下一个媒体后，是否立即进入播放态？
   - 立即 `PLAYING`
   - 还是先等客户端回报加载完成再进入正式播放态

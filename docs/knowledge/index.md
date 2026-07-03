---
okf_version: "0.1"
---
# JuggleChat Android 知识库

> 本库记录代码里读不出来的东西：架构意图、模块边界、历史决策、坑。
> 文件级细节请直接读代码（实现细节文档必然漂移，不再维护文件级清单）。

# 架构

* [系统架构全景](architecture/overview.md) - 单模块 Android IM Demo：UI → 领域仓储 → JIMChatCore(SDK 封装)/server(HTTP)，EventBus 做 SDK 到 UI 的事件总线

# 模块

* [IM 核心封装](modules/core-im.md) - JIMChatCore 单例封装 JuggleIM SDK，监听回调并经 EventBus 广播
* [身份认证](modules/auth.md) - 登录态全生命周期：校验、加密存储、启动路由、鉴权闸门、多端冲突
* [聊天消息](modules/chat-messaging.md) - 会话列表、消息流、渲染器、输入面板插件、@提及、群管理
* [音视频通话](modules/call.md) - 基于 ZEGO 的单聊/多人通话与全局来电浮窗
* [朋友圈](modules/moments.md) - 动态流、发布、点赞评论
* [搜索](modules/search.md) - 好友/群组/消息聚合搜索
* [网络请求层](modules/server.md) - OkHttp 自封装的 HTTP 服务框架与 DTO
* [应用壳层](modules/app-shell.md) - 主框架、设置页群、前台保活、公共组件

# 决策

* [ADR-001 EventBus 作为 SDK→UI 事件总线](decisions/001-eventbus-as-event-bridge.md) - 为什么用 EventBus 解耦 SDK 回调与 UI
* [ADR-002 鉴权闸门与启动路由重构](decisions/002-auth-guard-startup-route.md) - 登录态治理：AuthGuard + SessionRepository + 加密存储
* [ADR-003 会话列表单向数据流](decisions/003-conversation-list-reducer.md) - ConversationListReducer 的 Action→State 架构

# 坑

* [部分机型表情面板首击被吞](gotchas/emoji-panel-first-tap.md) - 面板未挂载时首击落空，PanelAttachPolicy 延迟策略

# 约定

* [结构化日志约定](conventions/structured-logging.md) - LogUtils 五段式日志 + TraceContext 链路追踪

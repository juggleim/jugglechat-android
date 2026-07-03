---
type: Architecture
title: 系统架构全景
description: 单模块 Android IM Demo：UI → 领域仓储 → JIMChatCore(SDK 封装)/server(HTTP)，EventBus 做 SDK 到 UI 的事件总线
tags: [architecture]
timestamp: 2026-07-03T14:00:00+08:00
---

# 系统架构全景

## 系统定位

JuggleIM SDK 的官方 Android 示例工程：既是可运行的完整 IM App（登录/聊天/群组/朋友圈/音视频通话），也是给接入方看的参考实现。**代码的首要读者是"评估接入成本的外部开发者"**——所以清晰度优先于抽象度，Java 而非 Kotlin，单 app 模块而非多模块。

## 技术栈

Java / 单 Gradle 模块（minSdk 24, target 34）/ JuggleIM SDK 1.8.44 / ZEGO 音视频 / OkHttp 自封装（未用 Retrofit 接口层）/ EventBus / Glide / EncryptedSharedPreferences

## 结构与数据流

两条独立的数据通道，UI 层只感知事件和仓储：

```
UI (Activity/Fragment/Adapter)
 ├─ IM 通道: chat/domain 仓储 ←→ [JIMChatCore](/modules/core-im.md) ←→ JuggleIM SDK
 │            ↑ EventBus 事件（event/ 包，SDK 回调 → 广播）见 ADR-001
 └─ 业务通道: [server/](/modules/server.md) ←→ 业务后端 HTTP API（好友/群组/朋友圈/账号）
```

* [IM 核心封装](/modules/core-im.md) - SDK 单例封装，连接管理 + 回调转事件
* [身份认证](/modules/auth.md) - 登录态生命周期，所有受保护操作过 AuthGuard
* [聊天消息](/modules/chat-messaging.md) - 最大的模块：会话/消息/渲染/输入/群管理
* [音视频通话](/modules/call.md) - ZEGO 通道，独立于 IM 消息通道
* [朋友圈](/modules/moments.md)、[搜索](/modules/search.md)、[应用壳层](/modules/app-shell.md)

## 关键链路

1. **启动**：Application → JIMChatCore.init → StartupRouteUseCase 决策（有会话→主页并连接 IM；无→登录页），见 [ADR-002](/decisions/002-auth-guard-startup-route.md)。
2. **收消息**：SDK 回调 → JIMChatCore 注册的监听 → EventBus 事件 → 各 UI 订阅刷新（会话列表走 [Reducer 单向流](/decisions/003-conversation-list-reducer.md)）。
3. **来电**：任意页面收到通话邀请 → CallIncomingFloatingManager 全局浮窗。

## 外部依赖

JuggleIM 服务端（IM 长连接 + token）、业务 HTTP 后端（ConfigUtils 配置地址）、ZEGO 音视频云。

## 已知债务（推断）

- `chat/` 包根目录下 Activity/Adapter 平铺较多，功能子域（群管理、转发、图片）未成包。

---
type: Module
title: 音视频通话
description: 基于 ZEGO 的单聊/多人通话与全局来电浮窗
resource:
  - app/src/main/java/com/juggle/im/android/chat/call/
tags: [call, zego]
timestamp: 2026-07-03T14:00:00+08:00
---

# 音视频通话

## Overview

通话信令走 JuggleIM 的 call 扩展（com.juggle.call.zego），媒体流走 ZEGO Express。BaseCallActivity 收敛全部通话生命周期（会话创建/权限/铃声/计时/接听挂断/浮窗恢复），单人与多人页面只做画面布局差异。

## 关系

* 入口：[聊天消息](/modules/chat-messaging.md) 的 Voice/VideoCallPlugin
* 来电浮窗 CallIncomingFloatingManager 由 Application 初始化，全局生效，与具体页面解耦
* 权限统一走 utils/PermissionComponent（[应用壳层](/modules/app-shell.md)）

## 关键入口

* `chat/call/BaseCallActivity.java` — 通话页基类，改通话行为先看这里
* `chat/call/CallIncomingFloatingManager.java` — 全局来电横幅
* `chat/call/CallUiStateStore.java` — 浮窗最小化/恢复的临时状态

## 注意事项

- 通话最小化→恢复依赖 CallUiStateStore 的内存态，进程被杀后浮窗恢复链路失效（推断，未验证极端场景）。
- ZEGO SDK 版本与 JuggleIM call 扩展版本需配套升级（见根 build.gradle 版本表）。

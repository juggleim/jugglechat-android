---
type: Module
title: IM 核心封装
description: JIMChatCore 单例封装 JuggleIM SDK，监听回调并经 EventBus 广播
resource:
  - app/src/main/java/com/juggle/im/android/core/
  - app/src/main/java/com/juggle/im/android/event/
tags: [im, core]
timestamp: 2026-07-29T14:25:27+08:00
---

# IM 核心封装

## Overview

全项目唯一与 JuggleIM SDK 直接对话的地方（双重检查锁单例）。职责：SDK 初始化、连接建立/断开、自定义消息类型注册、各类 SDK 监听注册。**边界意图：UI 层不 import SDK 的 manager 接口，一律通过本模块 + EventBus 事件获取 IM 状态变化**——见 [ADR-001](/decisions/001-eventbus-as-event-bridge.md)。

## 关系

* 广播 `event/` 包中的事件（连接状态/会话更新/消息更新/已读回执/表情回应/未读数），被 [聊天消息](/modules/chat-messaging.md) 和 [应用壳层](/modules/app-shell.md) 的 UI 订阅
* 注册 [聊天消息](/modules/chat-messaging.md) 定义的自定义消息类型（chat/message/ 包）
* 由 Application 启动时初始化，连接由 [身份认证](/modules/auth.md) 的登录流程触发

## 关键入口

* `core/JIMChatCore.java` — 单例；init / connect / disconnect / switchOrganization / 监听注册 / 会话同步 / 历史消息

## 注意事项

- 新增自定义消息类型必须在 JIMChatCore 里注册，否则收端解析为未知类型。
- 新增 SDK 事件转发时保持"一类变化一个事件类"的粒度，不要复用大杂烩事件。
- 企业切换通过 switchOrganization 先断开旧连接，再以新 appKey 和 IM 服务器重新初始化 SDK。

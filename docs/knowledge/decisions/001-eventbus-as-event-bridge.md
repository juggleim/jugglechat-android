---
type: Decision
title: ADR-001 EventBus 作为 SDK→UI 事件总线
description: 为什么用 EventBus 解耦 SDK 回调与 UI
status: accepted
tags: [architecture, eventbus]
timestamp: 2026-07-03T14:00:00+08:00
---

# ADR-001 EventBus 作为 SDK→UI 事件总线

> 本 ADR 由代码现状与 git 历史反推（推断），背景细节待项目作者确认。

## 背景

JuggleIM SDK 通过 manager 接口的回调下发变化（连接状态、消息、会话、已读、回应）。多个 UI（主页 Tab、会话列表、聊天页）都需要感知同一类变化；若各自向 SDK 注册监听，会出现监听生命周期分散、重复注册、UI 直接依赖 SDK 接口的问题。

## 选项

1. **各 UI 直接注册 SDK 监听** — 简单直接，但 SDK 依赖扩散到全 UI 层，Demo 的参考价值下降
2. **JIMChatCore 统一监听 + EventBus 广播** — UI 只依赖轻量事件类；代价是事件流隐式、跳转难追踪
3. LiveData/Flow 等响应式方案 — 对 Java + 多 Activity 的 Demo 引入成本高

## 决定

选 2：JIMChatCore 是唯一的 SDK 监听注册点，变化转成 `event/` 包下按类型细分的事件类，经 EventBus 广播。

## 后果

- 收益：UI 与 SDK 解耦，接入方能清晰看到"SDK 有哪些回调、对应什么 UI 更新"。
- 代价：EventBus 隐式订阅，新人需要靠事件类反查订阅方（IDE 查 usage）。
- 重新评估信号：若迁移 Kotlin/协程，应考虑 Flow 替代。

## 影响范围

* [IM 核心封装](/modules/core-im.md)、[聊天消息](/modules/chat-messaging.md)、[应用壳层](/modules/app-shell.md)

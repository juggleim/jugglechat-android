---
type: Decision
title: ADR-003 会话列表单向数据流
description: ConversationListReducer 的 Action→State 架构
status: accepted
tags: [chat, state, architecture]
timestamp: 2026-07-03T14:00:00+08:00
---

# ADR-003 会话列表单向数据流

> 由代码现状反推（推断），待确认。

## 背景

会话列表的状态来源多且并发：全量同步、增量事件（新消息/置顶/删除/免打扰）、分页加载。直接在 Fragment 里改 List 容易产生顺序错乱、重复项、未读数不一致。

## 决定

会话列表引入 Reducer 模式（`chat/state/ConversationListReducer.java`）：所有变化表达为 Action，纯函数 `(State, Action) → State`，Fragment 只负责 dispatch 和渲染。这是**全项目唯一**采用单向数据流的地方——只在状态来源最复杂处引入，其他简单页面保持直接命令式，避免全局架构税。

## 后果

- 收益：会话列表的合并/排序/去重规则集中可测。
- 代价：与项目其余部分风格不一致，新人需要理解两种范式。
- 重新评估信号：若消息列表也出现类似的多源并发问题，可复制此模式。

## 影响范围

* [聊天消息](/modules/chat-messaging.md)

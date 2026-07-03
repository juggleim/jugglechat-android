---
type: Module
title: 搜索
description: 好友/群组/消息聚合搜索
resource:
  - app/src/main/java/com/juggle/im/android/chat/SearchActivity.java
  - app/src/main/java/com/juggle/im/android/chat/LocalUserSearchCoordinator.java
  - app/src/main/java/com/juggle/im/android/chat/SearchResultMapper.java
tags: [search]
timestamp: 2026-07-03T14:00:00+08:00
---

# 搜索

## Overview

聚合三路数据源：本地好友、群组（HTTP）、消息（SDK 本地检索），由 LocalUserSearchCoordinator 协调聚合，SearchResultMapper 统一映射为 SearchResult 展示模型。分"综合页"与"更多结果页"两级。

## 关系

* 好友/群组数据走 [网络请求层](/modules/server.md)；消息检索走 [IM 核心封装](/modules/core-im.md) 的 SDK 能力

## 关键入口

* `chat/LocalUserSearchCoordinator.java` — 三路聚合协调，加新搜索源从这里入手

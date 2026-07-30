---
type: Module
title: 朋友圈
description: 动态流、发布、点赞评论
resource:
  - app/src/main/java/com/juggle/im/android/chat/MomentsActivity.java
  - app/src/main/java/com/juggle/im/android/chat/CreatePostActivity.java
  - app/src/main/java/com/juggle/im/android/server/http/MomentService.java
  - app/src/main/java/com/juggle/im/android/server/http/MomentServiceImpl.java
tags: [moments, social]
timestamp: 2026-07-30T10:52:00+08:00
---

# 朋友圈

## Overview

完全走业务 HTTP 通道（MomentService），不经 IM SDK；新动态提醒通过自定义消息 MomentNotifyMessage 下发。页面与服务分别挂在 chat/ 和 server/ 下，没有独立包。

## 关系

* HTTP：[网络请求层](/modules/server.md) 的 MomentService（列表/发布/点赞/评论/删除）
* 通知：[IM 核心封装](/modules/core-im.md) 注册的 MomentNotifyMessage 触达发现页红点

## 关键入口

* `chat/MomentsActivity.java` — 动态流 + 点赞评论交互
* `chat/CreatePostActivity.java` — 图文/视频发布

## 注意事项

- MomentsActivity 内有一处已知未解决的滚动 TODO（"执行滚动无效，已经在最底部"），改滚动逻辑时注意。
- 发布成功后由 CreatePostActivity 广播 `MomentPublishedEvent`，动态流订阅后自行刷新（对齐 iOS 的
  MomentDidPublish 通知）；不要再依赖 startActivityForResult 的返回链路刷新，否则从其他入口发布会漏刷。
- 点赞走乐观更新 + 失败回滚，并按 momentId 去重；SDK 的 MomentManager 回调不保证主线程，改 UI 或
  改本地列表数据前先切回主线程。

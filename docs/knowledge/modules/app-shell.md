---
type: Module
title: 应用壳层
description: 主框架、设置页群、前台保活、公共组件
resource:
  - app/src/main/java/com/juggle/im/android/app/
  - app/src/main/java/com/juggle/im/android/component/
  - app/src/main/java/com/juggle/im/android/service/
  - app/src/main/java/com/juggle/im/android/widget/
  - app/src/main/java/com/juggle/im/android/utils/
  - app/src/main/java/com/juggle/im/android/model/
tags: [app, ui]
timestamp: 2026-07-03T14:00:00+08:00
---

# 应用壳层

## Overview

聊天核心之外的一切：MainActivity 四 Tab 主框架、登录注册页、个人/通用设置页群、群创建、公共基类（AbsAppActivity 统一状态栏）、前台服务保活（ImForegroundService）、通用组件（widget/ 三件套、utils/ 工具）、全局配置与 UI 模型（model/）。

## 关系

* MainActivity 订阅 [IM 核心封装](/modules/core-im.md) 的连接状态与未读数事件
* 登录注册页逻辑委托给 [身份认证](/modules/auth.md)
* 各设置页的数据操作走 [网络请求层](/modules/server.md)

## 关键入口

* `app/MainActivity.java` — 主框架与 Tab 管理
* `service/ImForegroundService.java` — IM 长连接保活的常驻通知
* `model/ConfigUtils.java` — appKey/服务器地址/Zego ID 全局配置，换环境改这里

## 注意事项

- 日志必须走 utils/LogUtils 的结构化格式，见 [结构化日志约定](/conventions/structured-logging.md)。
- 新页面继承 AbsAppActivity，别自己处理状态栏。

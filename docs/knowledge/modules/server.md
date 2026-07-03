---
type: Module
title: 网络请求层
description: OkHttp 自封装的 HTTP 服务框架与 DTO
resource:
  - app/src/main/java/com/juggle/im/android/server/
tags: [network, http]
timestamp: 2026-07-03T14:00:00+08:00
---

# 网络请求层

> 包结构：`server/http/`（服务框架与接口实现）+ `server/beans/`（DTO）。

## Overview

**没有用 Retrofit 的接口生成**（虽然依赖里有 Retrofit）：ServiceManager 单例 + BaseService 手工封装 OkHttp 的 JSON POST/GET，鉴权头自动注入。意图（推断）：让接入方看清每个请求的原始形态，便于翻译到自己的网络栈。错误经 ApiErrorMapper 分层映射（网络/HTTP/业务码），TLS 策略在 SSLHelper（2026-05 "harden network tls baseline with debug guard"：release 强校验，debug 留 guard）。

## 关系

* UserService（30+ 接口：账号/好友/群组/黑名单/反馈）被 [身份认证](/modules/auth.md)、[聊天消息](/modules/chat-messaging.md)、[应用壳层](/modules/app-shell.md) 消费
* MomentService 被 [朋友圈](/modules/moments.md) 消费
* 服务器地址等在 model/ConfigUtils

## 关键入口

* `server/ServiceManager.java` — 服务单例与鉴权头注入
* `server/ApiErrorMapper.java` — 错误分层，新增错误处理先看映射规则

## 注意事项

- 新增 API：接口加在 UserService/MomentService + Impl，DTO 放 server/beans/，不要绕过 BaseService 直接用 OkHttp。
- 多端被踢状态码 11011 的处理在 [身份认证](/modules/auth.md)，网络层不拦截。

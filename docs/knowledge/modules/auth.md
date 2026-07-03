---
type: Module
title: 身份认证
description: 登录态全生命周期：校验、加密存储、启动路由、鉴权闸门、多端冲突
resource:
  - app/src/main/java/com/juggle/im/android/auth/
tags: [auth, security]
timestamp: 2026-07-03T14:00:00+08:00
---

# 身份认证

## Overview

经 E01 系列重构（2026-05）从散落在各 Activity 的逻辑收敛为独立包，按单一职责拆成小类：输入校验（AuthInputValidator）、请求构建（AuthRequestFactory，密码 MD5）、会话仓储（SessionRepository/SessionStorage）、启动路由（StartupRouteUseCase）、鉴权闸门（AuthGuard）、多端冲突（MultiDevicePolicy）。设计意图与取舍见 [ADR-002](/decisions/002-auth-guard-startup-route.md)。

## 关系

* 登录成功后触发 [IM 核心封装](/modules/core-im.md) 的 connect
* 登录/注册 HTTP 请求走 [网络请求层](/modules/server.md) 的 UserService
* [应用壳层](/modules/app-shell.md) 的受保护页面操作前过 AuthGuard

## 关键入口

* `auth/AuthGuard.java` — 所有需登录态的操作的前置闸门；无效跳登录页，支持被踢下线场景
* `auth/StartupRouteUseCase.java` — 启动时决策进主页还是登录页
* `auth/SessionStorage.java` — EncryptedSharedPreferences 加密存令牌，含旧版明文迁移

## 注意事项

- 多端被踢的判定依赖服务端状态码 **11011**（MultiDevicePolicy），服务端改码需同步。
- SessionStorage 含旧版本存储迁移逻辑，删除前确认无存量旧版用户。

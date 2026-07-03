---
type: Decision
title: ADR-002 鉴权闸门与启动路由重构
description: 登录态治理：AuthGuard + SessionRepository + 加密存储
status: accepted
tags: [auth, refactor]
timestamp: 2026-07-03T14:00:00+08:00
---

# ADR-002 鉴权闸门与启动路由重构

> 依据：commits `71afe70…`（E01-T002 启动路由与鉴权闸门重构）、`37aa…`（E01-T003 登录注册流程重构），2026-05。细节含推断。

## 背景

登录态判断、token 存取、被踢下线处理原先散落在各 Activity；token 曾以明文 SharedPreferences 存储；启动时"进主页还是登录页"的判断与 UI 耦合。

## 决定

收敛为 `auth/` 包内按单一职责拆分的小类：

- **AuthGuard**：所有需要登录态的操作的统一前置闸门（含被踢下线跳转）
- **StartupRouteUseCase**：启动路由决策独立成 UseCase，可单测
- **SessionRepository + SessionStorage**：会话读写唯一入口；EncryptedSharedPreferences 加密存储，保留旧版明文迁移
- **MultiDevicePolicy**：多端冲突判定（状态码 11011）独立成策略类

## 后果

- 收益：登录态逻辑可单测、可复用；token 不再明文落盘。
- 代价：类数量增多，简单改动需要跨多个小类。
- 重新评估信号：服务端鉴权协议变化（如 11011 语义调整）。

## 影响范围

* [身份认证](/modules/auth.md)、[应用壳层](/modules/app-shell.md)

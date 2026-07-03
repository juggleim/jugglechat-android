---
type: Convention
title: 结构化日志约定
description: LogUtils 五段式日志 + TraceContext 链路追踪
resource:
  - app/src/main/java/com/juggle/im/android/utils/LogUtils.java
  - app/src/main/java/com/juggle/im/android/model/TraceContext.java
tags: [convention, logging]
timestamp: 2026-07-03T14:00:00+08:00
---

# 结构化日志约定

## 规则

业务日志一律走 `utils/LogUtils`，按五段结构输出：`traceId / feature / event / result / detail`；请求级 traceId 由 `model/TraceContext`（ThreadLocal）提供。不要直接用 `android.util.Log` 打业务日志（SDK 内部日志除外）。

## 为什么

Demo 同时是排障参考：结构化字段让接入方能按 feature/event 过滤定位问题，traceId 能把一次用户操作跨 HTTP 请求串起来。

## 示例

正例：`LogUtils.i(feature="login", event="submit", result="ok", detail=...)`（随 TraceContext 自动带 traceId）
反例：`Log.d("TAG", "login ok")` —— 无法按链路检索。

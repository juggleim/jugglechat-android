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
  - app/src/main/java/com/juggle/im/android/i18n/
tags: [app, ui]
timestamp: 2026-07-30T10:50:00+08:00
---

# 应用壳层

## Overview

聊天核心之外的一切：MainActivity 四 Tab 主框架、登录注册页、个人/通用设置页群、群创建、公共基类（AbsAppActivity 统一状态栏）、前台服务保活（ImForegroundService）、通用组件（widget/ 三件套、utils/ 工具）、全局配置与 UI 模型（model/）。

## 关系

* MainActivity 订阅 [IM 核心封装](/modules/core-im.md) 的连接状态与未读数事件
* 登录注册页逻辑委托给 [身份认证](/modules/auth.md)
* 各设置页的数据操作走 [网络请求层](/modules/server.md)
* 用户协议与隐私政策随 APK 内置，由 WebViewPageActivity 按 [多语言约定](/conventions/i18n.md) 选择中英文资源；协议页禁止网络回退
* “我的”Tab 由 MainActivity 在 Activity 层连续绘制状态栏与个人信息区背景，隐藏公共标题并同步收起标题占位；切换 Tab 时 Fragment 与顶部样式在同一帧生效
* 主页个人信息入口打开账户设置；账户设置按当前用户匹配本地缓存并优先展示，再以服务端差异更新缓存和界面，同时保护未保存的编辑内容；账户区支持添加及快速切换同一企业下最多 5 个账户
* 登录页右上角仅展示企业入口图标，OrganizationLoginActivity 独立承载企业 ID 输入、加载、失败回滚和成功返回
* 启动窗口与 iOS 统一使用品牌蓝背景和底部品牌口号；Android 12+ 由系统 Splash 属性承载

## 关键入口

* `app/MainActivity.java` — 主框架与 Tab 管理
* `app/LoginActivity.java` — 账密/邮箱登录、添加账户模式及企业入口
* `app/OrganizationLoginActivity.java` — 企业 ID 页面与企业运行配置切换
* `app/PersonalSettingsActivity.java` — 账户资料展示、编辑和缓存同步
* `service/ImForegroundService.java` — IM 长连接保活的常驻通知
* `model/ConfigUtils.java` — 当前企业 ID、appKey、业务/IM 服务器和 Zego ID 运行配置
* `i18n/LanguageManager.java` — 应用语言持久化与生效，入口在 app/LanguageSettingsActivity
* `app/WebViewPageActivity.java` — 用户协议和隐私政策的离线、安全承载页

## 注意事项

- 日志必须走 utils/LogUtils 的结构化格式，见 [结构化日志约定](/conventions/structured-logging.md)。
- 新页面继承 AbsAppActivity，别自己处理状态栏。
- 界面文案一律走资源，取串规则见 [多语言约定](/conventions/i18n.md)。
- 写操作（提交/删除/确认类）的加载态统一用 widget/ 三件套，不要各页面自己维护布尔位：
  按钮型用 `SubmitButtonState`（`begin()` 返回 false 即代表已有请求在途，兼作防重判断）；
  确认弹窗型用 `AppConfirmDialog.setOnPositiveAsyncClick`（请求中不关闭弹窗、确认按钮转 spinner）；
  从别的页面返回后直接发起、页面上没有按钮可承载加载态的场景用 `LoadingOverlay`。

---
type: Module
title: 聊天消息
description: 会话列表、消息流、渲染器、输入面板插件、@提及、群管理
resource:
  - app/src/main/java/com/juggle/im/android/chat/
tags: [chat, message, core]
timestamp: 2026-07-30T10:54:00+08:00
---

# 聊天消息

## Overview

项目最大模块。内部按职责分子包，理解顺序建议：

- **domain/**（ConversationRepository/MessageRepository）：唯一允许触碰 SDK 数据读取的仓储层，做 SDK 模型 → UI 领域模型（UiConversation/UiMessage）映射
- **state/**（ConversationListReducer）：会话列表单向数据流，见 [ADR-003](/decisions/003-conversation-list-reducer.md)
- **provider/**：消息渲染器，MessageView 基类统一气泡/头像/状态，按消息类型一个子类；新消息类型 = message/ 定义 + provider/ 渲染器 + [core-im](/modules/core-im.md) 注册，三处缺一不可
- **plugin/**：输入面板"更多"插件体系，MorePlugin 基类，一功能一插件
- **mention/**、**view/**（ChatInputActionBar 输入面板）、**call/**（[独立成文](/modules/call.md)）
- 包根平铺的 Activity/Adapter：会话页、群管理页群、转发、图片预览、相册

## 关系

* 数据来源：[IM 核心封装](/modules/core-im.md) 的 EventBus 事件 + domain 仓储
* 群组/好友资料操作走 [网络请求层](/modules/server.md) UserService
* 通话入口（VoiceCallPlugin/VideoCallPlugin）唤起 [音视频通话](/modules/call.md)
* 会话级定时删除由业务服务保存周期，发送消息时通过 JuggleIM SDK 的消息时效参数落到具体消息；设置变化以状态消息同步给会话成员

## 关键入口

* `chat/ConversationActivity.java` — 聊天主页面，消息操作（转发/撤回/回复/回应/收藏）的集散地
* `chat/view/ChatInputActionBar.java` — 输入面板：文字/语音/表情/插件面板切换（有坑：[表情面板首击](/gotchas/emoji-panel-first-tap.md)）
* `chat/state/ConversationListReducer.java` — 会话列表状态唯一入口
* `chat/MyProfileFragment.java` — “我的”页资料展示；先绑定当前用户缓存，再刷新服务端资料，资料无变化时不重复刷新界面

## 注意事项

- 经 E02 重构（2026-05"聊天流与输入插件管线重构"），输入管线的扩展点是 plugin/，不要在 ConversationActivity 里直接加功能按钮。
- 消息列表的时间分割线是 UI-only 的 LocalMessage，不入 SDK 存储。
- 定时删除仅影响设置后发送的新消息；群聊入口受 `group_set_msg_life_right` 控制，当前会话周期不能附加到转发目标会话。
- “我的”页版本信息必须读取安装包元数据，不能维护独立的硬编码版本号。
- “我的”页首次展示不得重复请求资料；缓存与当前登录用户不匹配时不得展示，服务端返回相同资料时不得重新绑定界面。
- 聊天背景在「通用设置」里全局设置，落地点在 MessageListFragment 的背景图层：onViewCreated/onResume
  各应用一次，另订阅 `ChatBackgroundChangedEvent` 做即时换图；改会话页布局时别把背景图层盖掉。
- 语音播放一律走本地文件：消息批量落地后由 `VoiceMessageDownloader.prefetch` 预下载，点击时本地缺失
  才下载（按 messageId 去重）。直接把远端 URL 交给 MediaPlayer 会让每次点击都重新走网络缓冲，出声明显延迟。
- 图片消息占位图按目标宽高比在竖版/横版两张灰底图中就近取用，与 iOS MessageImagePlaceholderRenderer 同规则。

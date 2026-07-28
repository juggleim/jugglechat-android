---
type: Gotcha
title: 部分机型表情面板首击被吞
description: 面板未挂载时首击落空，PanelAttachPolicy 延迟策略
resource:
  - app/src/main/java/com/juggle/im/android/chat/view/ChatInputActionBar.java
tags: [chat, input, ui]
timestamp: 2026-07-28T15:35:00+08:00
---

# 部分机型表情面板首击被吞

## 现象

部分机型上，首次点击表情/更多按钮切出面板后，用户的第一次点击落在"尚未挂载完成"的空白面板区域被吞掉，表现为"表情点不了/无法输入表情"。

## 根因

输入面板（ChatInputActionBar）为了平滑处理键盘/面板切换，面板首次显示时有挂载延迟；可交互面板（表情/更多）在延迟窗口内接收不到点击。

## 规避 / 正确做法

已修复（commit `84e1282`，2026-05-25）：`PanelAttachPolicy.resolveAttachDelayMs()` 区分面板类型——**可交互面板立即挂载（0ms）**，仅文本模式占位面板保留 `FIRST_PANEL_ATTACH_DELAY_MS = 120ms` 维持过渡稳定。改动面板切换逻辑时不要恢复"统一延迟挂载"。

## 关联

* 代码：`chat/view/ChatInputActionBar.java`（内部类 PanelAttachPolicy）
* 相关：[聊天消息](/modules/chat-messaging.md)

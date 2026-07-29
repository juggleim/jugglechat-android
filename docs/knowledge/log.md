# 更新日志

## 2026-07-29
* **Update**: 兼容跨端一对一界面承载多人通话会话的情况，在最后一个远端成员退出时收口 Android 通话页、浮窗和 SDK 会话；同步记录通话结束灰条仍由服务端一对一房间通知生成，并复核 [音视频通话](/modules/call.md) 与 [聊天消息](/modules/chat-messaging.md)。

## 2026-07-28
* **Update**: 优化“我的”页顶部背景、账户资料缓存与 Tab 切换，消除从“消息”切换到“我的”时的中间态闪烁；同步更新 [应用壳层](/modules/app-shell.md) 和 [聊天消息](/modules/chat-messaging.md)。
* **Update**: 协议与隐私政策改为 APK 内置中英文内容，并修复“我的”页硬编码版本号；同步更新 [应用壳层](/modules/app-shell.md)、[聊天消息](/modules/chat-messaging.md) 和 [多语言约定](/conventions/i18n.md)。
* **Update**: 增加会话级定时删除后，更新 [聊天消息](/modules/chat-messaging.md) 与 [网络请求层](/modules/server.md) 的职责边界；同步复核 [多语言约定](/conventions/i18n.md) 和 [表情面板首击 Gotcha](/gotchas/emoji-panel-first-tap.md)。

## 2026-07-03
* **Deprecation**: 删除已漂移的文件级清单 `docs/project-modules.md`（记录 107 个 Java 文件，实际已 197 个），其"意图/边界"类内容已由本知识库承接，文件级细节以代码为准。同步清理 README/CLAUDE.md/AGENT.md 中的引用。
* **Creation**: 初始化知识库（okf-coding bootstrap 阶段 1+2+部分 3）：架构全景、8 个模块文档、3 个 ADR（从 git 历史反推，含推断标注）、1 个 Gotcha（源自 commit 84e1282）、1 个约定。

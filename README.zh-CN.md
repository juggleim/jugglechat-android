# JuggleChat Android — 开源即时通讯与聊天应用

[English](README.md) | [简体中文](README.zh-CN.md)

JuggleChat Android 是一个基于 Java、JuggleIM SDK 与 ZEGO 构建的、可完整运行的 **Android 即时通讯应用（IM App）**。项目面向希望开发移动聊天产品的开发者，提供单聊、群聊、通讯录、朋友圈、消息搜索以及实时音视频通话等完整参考实现。

你可以用它评估 JuggleIM Android SDK 的接入成本，学习真实聊天应用的功能组织方式，或快速搭建自己的 Android IM 应用原型。

## 功能预览

<table>
  <tr>
    <td align="center">
      <strong>消息、通讯录与群组</strong><br>
      <sub>登录 · 会话列表 · 富媒体消息 · 语音消息 · 聚合搜索 · 群聊</sub>
    </td>
    <td align="center">
      <strong>音视频、群管理与朋友圈</strong><br>
      <sub>音视频通话 · 来电提醒 · 群组设置 · 朋友圈 · 消息收藏</sub>
    </td>
  </tr>
  <tr>
    <td width="50%">
      <img src="images/img.png" alt="JuggleChat Android 即时通讯应用界面，包含登录、会话列表、聊天消息、语音消息、联系人搜索和群聊">
    </td>
    <td width="50%">
      <img src="images/img_1.png" alt="JuggleChat Android IM 应用界面，包含音视频通话、群组管理、朋友圈、消息收藏、通讯录和来电提醒">
    </td>
  </tr>
</table>

## 为什么选择 JuggleChat？

- **完整可运行**：覆盖从注册登录到会话、消息、群组、朋友圈和音视频通话的完整链路。
- **贴近真实 IM 场景**：包含未读数、分页、消息操作、登录态恢复、多端登录处理和来电浮窗等常见能力。
- **提供集成参考**：展示 Android 客户端如何连接 IM SDK、业务服务端与 ZEGO 实时音视频服务。
- **Java 架构清晰**：采用单 App 模块，明确划分 UI、领域仓储、SDK 封装、HTTP 服务和事件通知边界。
- **适合二次开发**：可用于企业内部沟通、社区聊天、在线客服或社交产品的原型验证。

## 功能特性

### 消息与会话

- 单聊与群聊
- 文本、图片、语音、文件及合并转发消息
- 消息回复、回应、翻译、撤回、收藏与置顶
- 会话分页、未读数、置顶与免打扰
- 历史消息与聊天记录搜索

### 通讯录与群组

- 注册、登录、会话恢复与多端登录态处理
- 好友申请与通讯录管理
- 群组创建、群公告、群二维码、角色与成员管理
- 好友、群组与消息的聚合搜索

### 社交与实时音视频

- 朋友圈动态、图片发布、点赞与评论
- 基于 ZEGO 的单人及多人音视频通话
- 全局来电与通话中浮窗
- 基于 CameraX 与 ML Kit 的条形码/二维码扫描

## 技术栈

| 分类 | 技术与版本 |
|---|---|
| 平台 | Android、Java |
| Android 版本 | minSdk 24、targetSdk 34 |
| 构建工具 | Android Gradle Plugin 8.4.0、Gradle 8.6、JDK 17 |
| 即时通讯 | JuggleIM Android SDK `1.8.44` |
| 音视频通话 | Juggle 通话扩展 `1.8.25`、ZEGO Express `3.17.3` |
| 应用架构 | Repository 领域仓储、SDK Facade、EventBus 事件桥接 |
| UI 与媒体 | AndroidX、Material Components、Glide、CameraX、ML Kit |
| 网络与数据 | Retrofit、Gson、Protobuf、七牛 SDK |
| 安全存储 | AndroidX Security Crypto |

## 项目架构

JuggleChat 将 SDK 回调与应用 UI 解耦。`JIMChatCore` 是项目与 JuggleIM SDK 交互的唯一 Facade；领域仓储负责组织数据，EventBus 将连接、会话与消息变化分发到 UI。

```text
Android UI
   ├── 领域仓储 ── JIMChatCore ── JuggleIM SDK
   ├── HTTP 服务层 ────────────── 业务服务端
   └── 通话 UI ── Juggle 通话扩展 ── ZEGO
```

主要源码目录：`app/src/main/java/com/juggle/im/android/`

| 目录 | 职责 |
|---|---|
| `app/` | 应用壳层、登录注册、设置与个人资料 |
| `auth/` | 会话持久化、鉴权保护与启动路由 |
| `chat/` | 会话、消息、通讯录、群组、搜索、通话与朋友圈 |
| `core/` | JuggleIM SDK 核心封装（`JIMChatCore`） |
| `server/` | HTTP 服务与数据传输对象 |
| `event/` | EventBus 事件定义 |
| `service/` | 前台服务与保活能力 |
| `utils/`、`widget/` | 通用工具与自定义 Android 组件 |

如需了解设计决策、模块边界与已知实现细节，请从[项目知识库](docs/knowledge/index.md)开始阅读。

## 快速开始

### 环境要求

- Android Studio（建议使用最新稳定版）
- JDK 17
- Android SDK 34
- Android 7.0（API 24）及以上的设备或模拟器

### 1. 克隆并打开项目

克隆本仓库，使用 Android Studio 打开项目根目录，等待 Gradle 同步完成。

### 2. 配置服务参数

修改 `app/src/main/java/com/juggle/im/android/model/ConfigUtils.java`：

| 字段 | 说明 |
|---|---|
| `appKey` | JuggleIM 应用 Key |
| `appServerUrl` | 业务服务端基础地址 |
| `imServer` | JuggleIM WebSocket 服务地址 |
| `zegoId` | 音视频通话使用的 ZEGO AppID |

仓库中提交的参数仅用于演示和测试。分发应用前，请替换为自己的服务、凭证和签名配置，切勿把生产密钥提交到代码仓库。

### 3. 构建并运行

在 Android Studio 中运行 `app` 配置，或通过命令行构建 Debug APK：

```bash
./gradlew :app:assembleDebug
```

## 常用开发命令

| 命令 | 用途 |
|---|---|
| `./gradlew :app:assembleDebug` | 构建 Debug APK |
| `./gradlew :app:assembleRelease` | 构建 Release APK |
| `./gradlew :app:testDebugUnitTest` | 运行单元测试 |
| `./gradlew :app:lintDebug` | 运行 Android Lint |

## 常见问题

### JuggleChat 是完整的 Android 聊天应用，还是只有 SDK 示例？

它是一个完整的客户端参考应用，包含注册登录、会话、富媒体消息、通讯录、群组管理、搜索、朋友圈与音视频通话界面。运行时仍需要配套的业务服务端、JuggleIM 服务以及 ZEGO 配置。

### 可以基于这个项目开发自己的即时通讯 App 吗？

可以。项目适用于学习、SDK 评估、产品原型和二次开发。用于商业项目之前，请检查各 SDK 和服务的授权条款，替换全部演示凭证，并完成隐私、安全与合规评估。

### 这个 Android 聊天 Demo 支持群聊和视频通话吗？

支持。项目实现了单聊、群聊，以及单人和多人音视频通话。

### 项目使用 Kotlin 还是 Java？

应用代码主要使用 Java 编写，适合维护 Java Android 项目的团队参考，也便于对比 IM SDK 的 Java 接入方式。

### JuggleChat 需要哪些服务端能力？

客户端通过业务 HTTP 服务获取业务数据，通过 JuggleIM 处理实时消息，并使用 ZEGO 传输音视频媒体流。相关服务地址和凭证需要在 `ConfigUtils.java` 中配置。

## 参与贡献

欢迎提交 Issue 和 Pull Request。报告问题时，请附上 Android 版本、设备型号、复现步骤、预期结果与相关日志。提交 PR 前请保持改动聚焦，并运行与改动最相关的构建、单元测试或 Lint 任务。

如果这个 Android IM 项目对你有帮助，欢迎为仓库点亮 Star。Star 和高质量的问题反馈能帮助更多 Android 开发者发现并完善 JuggleChat。

## 安全与生产环境说明

- 替换示例服务地址、应用 Key、ZEGO AppID 与签名文件。
- 复核通知、相机、麦克风、媒体读取和前台服务权限。
- 根据业务要求补充隐私政策、数据保留规则与账号注销流程。
- 商业发布前确认 SDK 授权、服务费用及所在地区的合规要求。
- 不要将仓库内的演示配置视为生产安全基线。

## 项目定位

JuggleChat Android 主要作为学习、评估和二次开发参考项目维护。它不是托管式即时通讯服务，也不能替代生产聊天应用所需的服务端、可观测性、安全加固与合规建设。

## 开源许可证

JuggleChat Android 基于 [Apache License 2.0](LICENSE) 开源。

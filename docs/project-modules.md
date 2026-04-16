# JuggleChat Android 项目功能模块说明

## 项目概述

JuggleChat Android 是一个基于 JuggleIM SDK 开发的即时通讯客户端应用，集成即构（Zego）音视频 SDK，覆盖 IM 全链路功能。

- **包名**: `com.juggle.im.android`
- **最低 SDK**: API 24 (Android 7.0)
- **目标 SDK**: API 34 (Android 14)
- **核心依赖**: JuggleIM SDK 1.8.44 / Zego Express Video 3.17.3
- **Java 文件数**: 107 个
- **注册 Activity**: 40 个

---

## 模块总览

```
app/src/main/java/com/juggle/im/android/
├── Application.java          # 应用入口
├── app/                      # 应用壳层（登录/注册/设置/群创建等页面）
├── auth/                     # 身份认证与会话管理
├── chat/                     # 聊天核心（会话/消息/朋友圈/群组/通话/搜索）
├── component/                # 公共 Activity 基类
├── core/                     # IM SDK 核心封装
├── event/                    # EventBus 事件定义
├── model/                    # UI 领域模型与全局配置
├── server/                   # 网络请求层（HTTP API）
├── service/                  # Android 前台服务
├── utils/                    # 工具类
└── widget/                   # 自定义 UI 组件
```

---

## 一、应用入口层 — `Application.java`

| 文件 | 功能说明 |
|---|---|
| `Application.java` | 应用入口类，初始化 JIMChatCore（IM 核心）和 CallIncomingFloatingManager（来电浮窗管理器） |

---

## 二、应用壳层 — `app/`

承载所有非聊天核心的 Activity 页面，包括启动、登录注册、个人设置、通用设置、群创建等。

### 2.1 启动与导航

| 文件 | 功能说明 |
|---|---|
| `Application.java` | Application 入口。初始化 IM SDK，启动时恢复 session 并做路由决策 |
| `MainActivity.java` | LAUNCHER 主入口 + 主框架页面。底部四 Tab 导航（会话/通讯录/发现/我），管理 Fragment 切换、连接状态监听、未读数更新 |
| `BottomNavView.java` | 自定义底部导航栏组件，支持未读数角标显示 |

### 2.2 登录注册

| 文件 | 功能说明 |
|---|---|
| `LoginActivity.java` | 登录页。支持账号密码登录和邮箱验证码登录两种模式，含输入校验、加载态 |
| `RegisterActivity.java` | 注册页。收集账号+密码+确认密码，校验后调用注册接口 |

### 2.3 好友与群组

| 文件 | 功能说明 |
|---|---|
| `AddFriendActivity.java` | 添加好友页。搜索防抖输入框，搜索用户并发送好友申请 |
| `CreateGroupActivity.java` | 创建群聊/添加群成员页。支持好友列表按拼音字母分组索引、多选成员 |
| `CreateGroupListAdapter.java` | 群聊创建成员列表适配器 |
| `FriendApplicationsActivity.java` | 好友申请列表页。支持接受/拒绝操作 |
| `MyGroupsActivity.java` | 我的群组列表页 |
| `BlockUsersActivity.java` | 黑名单列表页 |

### 2.4 个人设置

| 文件 | 功能说明 |
|---|---|
| `PersonalSettingsActivity.java` | 个人设置页。修改头像/昵称/账号、绑定邮箱、修改密码、退出登录 |
| `GeneralSettingsActivity.java` | 通用设置页。聊天背景选择、应用内通知开关 |
| `ChatBackgroundActivity.java` | 聊天背景选择页。8 种预设背景 |
| `FavoritesActivity.java` | 我的收藏页。支持多选删除、分页加载 |
| `FeedbackActivity.java` | 意见反馈页。文字+最多 8 张图片上传 |
| `MyQRCodeActivity.java` | 我的二维码页。展示用户头像、昵称和个人二维码 |
| `AvatarPickerActivity.java` | 头像选择页。相册图片网格选择 |
| `AvatarPreviewActivity.java` | 头像预览页 |

### 2.5 其他

| 文件 | 功能说明 |
|---|---|
| `WebViewPageActivity.java` | 通用 WebView 页面 |
| `UserAgreementActivity.java` | 用户协议页 |
| `PrivacyPolicyActivity.java` | 隐私政策页 |
| `AppSettingsStore.java` | 本地设置存储（通知开关、聊天背景索引） |

---

## 三、身份认证 — `auth/`

统一管理登录态生命周期：输入校验、请求构建、会话持久化、启动路由、多端冲突、鉴权闸门。

| 文件 | 功能说明 |
|---|---|
| `AuthGuard.java` | 鉴权闸门。对所有需要有效登录态的操作做前置校验，无效时跳转登录页，支持"被踢下线"场景 |
| `AuthInputValidator.java` | 登录/注册输入校验器 |
| `AuthRequestFactory.java` | 登录/注册请求对象工厂，封装密码 MD5 哈希 |
| `HashUtils.java` | MD5 哈希工具类 |
| `MultiDevicePolicy.java` | 多端登录冲突策略。状态码 11011 判定为被其他设备顶下线 |
| `SessionRepository.java` | 会话仓储。统一管理登录会话的读取/保存/清理 |
| `SessionStorage.java` | 会话存储实现。使用 EncryptedSharedPreferences 加密存储令牌，兼容旧版迁移 |
| `StartupRouteUseCase.java` | 启动路由决策用例。判断跳转主页或登录页 |
| `UserProfileStore.java` | 登录用户资料本地缓存（userId/nickname/avatar） |

---

## 四、聊天核心 — `chat/`

项目最核心的功能模块，包含 6 个子包，覆盖会话列表、消息收发、朋友圈、群管理、搜索、音视频通话、@提及、输入面板插件等。

### 4.1 会话与消息

| 文件 | 功能说明 |
|---|---|
| `ConversationActivity.java` | **聊天会话主页面**。承载消息列表、输入面板、@提及、消息操作（转发/置顶/撤回/删除/收藏/回复/表情回应）、群信息跳转 |
| `ConversationListFragment.java` | 会话列表 Fragment。使用 ConversationListReducer 做单向数据流管理，支持分页加载、长按操作（置顶/删除/免打扰） |
| `ConversationListAdapter.java` | 会话列表适配器 |
| `ConversationSettingsActivity.java` | 会话设置页。群/私聊详情、群成员、群公告、免打扰、置顶、清空聊天记录 |
| `MessageListFragment.java` | 消息列表 Fragment。展示聊天消息流、下拉加载更多、消息操作弹窗 |
| `MessageListAdapter.java` | 消息列表适配器，按消息类型分派渲染器 |

### 4.2 导航页面

| 文件 | 功能说明 |
|---|---|
| `FriendsFragment.java` | 通讯录 Fragment。好友列表按拼音字母分组索引 |
| `FriendsListAdapter.java` | 好友列表适配器 |
| `DiscoverFragment.java` | 发现页 Fragment。包含"朋友圈"入口 |
| `MyProfileFragment.java` | "我"页面 Fragment。头像/昵称/ID 展示，各设置项入口 |

### 4.3 朋友圈

| 文件 | 功能说明 |
|---|---|
| `MomentsActivity.java` | 朋友圈页面。展示动态流、点赞/评论交互、图片/视频发布 |
| `CreatePostActivity.java` | 朋友圈发帖页。文字+图片/视频发布 |

### 4.4 消息转发与合并

| 文件 | 功能说明 |
|---|---|
| `ForwardConversationListActivity.java` | 转发页面，选择目标会话 |
| `ForwardConversationListFragment.java` | 转发会话列表 Fragment |
| `ForwardConversationListAdapter.java` | 转发会话列表适配器 |
| `MergeMessageActivity.java` | 合并消息查看页 |
| `MergeListAdapter.java` | 合并消息列表适配器 |

### 4.5 图片与相册

| 文件 | 功能说明 |
|---|---|
| `ImagePreviewActivity.java` | 图片预览页。全屏查看聊天中的图片 |
| `ImagePreviewAdapter.java` | 图片预览适配器 |
| `AlbumActivity.java` | 相册页。展示设备相册图片供选择 |
| `AlbumImageAdapter.java` | 相册图片适配器 |

### 4.6 搜索

| 文件 | 功能说明 |
|---|---|
| `SearchActivity.java` | 搜索页。搜索好友/群组/消息 |
| `SearchAdapter.java` | 搜索结果适配器 |
| `SearchMoreResultsActivity.java` | 搜索更多结果页 |
| `SearchMoreResultAdapter.java` | 搜索更多结果适配器 |
| `SearchResult.java` | 搜索结果数据类 |
| `SearchResultMapper.java` | 搜索结果映射器 |
| `LocalUserSearchCoordinator.java` | 本地用户搜索协调器。聚合好友搜索、群组搜索、全局用户搜索 |

### 4.7 群组管理

| 文件 | 功能说明 |
|---|---|
| `GroupInfoActivity.java` | 群信息页。展示群详情、群头像、群名称、群二维码 |
| `GroupMembersActivity.java` | 群成员列表页 |
| `GroupManagementActivity.java` | 群管理设置页（群公告、群管理员、群权限等） |
| `GroupAdminsActivity.java` | 群管理员列表页 |
| `GroupAnnouncementActivity.java` | 群公告页 |
| `GroupQrcodeActivity.java` | 群二维码页 |
| `SelectMemberActivity.java` | 群成员选择页（@提及/群管理选人） |
| `EditNicknameActivity.java` | 编辑群昵称页 |
| `ContactListAdapter.java` | 通用联系人列表适配器 |

### 4.8 音视频通话 — `chat/call/`

| 文件 | 功能说明 |
|---|---|
| `BaseCallActivity.java` | 通话页基类。统一处理通话会话创建/权限检查/铃声/计时/接听/挂断/浮窗恢复 |
| `SingleCallActivity.java` | 单人音视频通话页。展示本地/远端视频画面 |
| `MultiCallActivity.java` | 多人音视频通话页。GridLayout 多方视频画面 |
| `CallIncomingFloatingManager.java` | 全局来电浮窗管理器。任何页面收到来电时显示横幅浮窗 |
| `CallUiStateStore.java` | 通话 UI 临时状态仓库（浮窗恢复信息） |

### 4.9 @提及功能 — `chat/mention/`

| 文件 | 功能说明 |
|---|---|
| `MentionManager.java` | @提及管理器。初始化 EditText 文本监听，插入@块，提取提及信息 |
| `MentionCallback.java` | @提及回调接口 |
| `MentionConfig.java` | @提及配置（触发字符等） |
| `MentionModel.java` | @提及数据模型 |
| `MentionSpan.java` | @提及文本 Span，高亮显示@成员 |
| `MentionWatcher.java` | @提及文本变化监听器 |

### 4.10 自定义消息类型 — `chat/message/`

| 文件 | 功能说明 |
|---|---|
| `MessageTypes.java` | 消息类型常量定义 |
| `FriendNotifyMessage.java` | 好友通知消息 |
| `GroupNotifyMessage.java` | 群通知消息 |
| `LifeTimeNotifyMessage.java` | 阅后即焚时效消息 |
| `MomentNotifyMessage.java` | 朋友圈通知消息 |
| `SyncDataNotifyMessage.java` | 数据同步通知消息 |
| `TimelineNotifyMessage.java` | 时间线通知消息 |
| `TypingNotifyMessage.java` | 输入状态通知消息 |
| `StickerEmojiMessage.java` | 表情贴纸消息 |
| `StickerGameMessage.java` | 骰子/猜拳游戏消息 |
| `InsertTimeStatusMessage.java` | 时间分割线状态消息 |

### 4.11 聊天输入面板插件 — `chat/plugin/`

| 文件 | 功能说明 |
|---|---|
| `MorePlugin.java` | 插件基类 |
| `ImagePlugin.java` | 图片插件（跳转相册） |
| `CameraPlugin.java` | 拍照插件 |
| `FilePlugin.java` | 文件插件 |
| `ContactPlugin.java` | 名片插件 |
| `LocationPlugin.java` | 位置插件（占位） |
| `VideoCallPlugin.java` | 视频通话插件 |
| `VoiceCallPlugin.java` | 语音通话插件 |
| `TimedDeletePlugin.java` | 阅后即焚定时删除插件 |

### 4.12 消息视图渲染器 — `chat/provider/`

| 文件 | 功能说明 |
|---|---|
| `MessageView.java` | 消息视图抽象基类。统一处理头像、发送者名称、发送状态、气泡样式 |
| `TextMessageView.java` | 文本消息渲染器 |
| `StreamTextMessageView.java` | 流式文本消息渲染器 |
| `ImageMessageView.java` | 图片消息渲染器 |
| `VoiceMessageView.java` | 语音消息渲染器 |
| `FileMessageView.java` | 文件消息渲染器 |
| `MergeMessageView.java` | 合并转发消息渲染器 |
| `StatusMessageView.java` | 状态/通知消息渲染器 |

### 4.13 领域层 — `chat/domain/`

| 文件 | 功能说明 |
|---|---|
| `ConversationRepository.java` | 会话仓储。统一承接会话读取、映射和排序规则 |
| `MessageRepository.java` | 消息仓储。封装 SDK 消息读取、领域映射与已读回执逻辑 |

### 4.14 状态管理 — `chat/state/`

| 文件 | 功能说明 |
|---|---|
| `ConversationListReducer.java` | 会话列表 Reducer。单向数据流（Action -> State），支持增量合并/分页/删除/置顶 |

### 4.15 聊天工具 — `chat/utils/`

| 文件 | 功能说明 |
|---|---|
| `FileUtils.java` | 文件操作工具 |
| `MessageUtils.java` | 消息工具类（摘要提取、时间格式化） |

### 4.16 聊天自定义 View — `chat/view/`

| 文件 | 功能说明 |
|---|---|
| `ChatInputActionBar.java` | 聊天输入面板。集成文字输入、语音、表情、更多功能面板、插件管理 |
| `SendImeEditText.java` | 自定义 EditText，支持键盘发送键 |
| `VoiceInputAction.java` | 语音输入控件，处理录音手势和状态 |

### 4.17 聊天专用 Widget — `chat/widget/`

| 文件 | 功能说明 |
|---|---|
| `IndexBar.java` | 字母索引侧边栏 |
| `LetterHeaderDecoration.java` | RecyclerView 字母分组悬浮头 |
| `SettingRowView.java` | 通用设置行 View |

---

## 五、公共基类 — `component/`

| 文件 | 功能说明 |
|---|---|
| `AbsAppActivity.java` | 所有 Activity 的抽象基类。统一设置状态栏和导航栏样式 |

---

## 六、IM 核心封装 — `core/`

| 文件 | 功能说明 |
|---|---|
| `JIMChatCore.java` | IM 核心管理类（单例）。初始化 JIM SDK、建立/管理连接、注册会话/消息/已读回执/表情回应监听（EventBus 广播）、同步会话列表、获取消息历史 |

---

## 七、事件定义 — `event/`

| 文件 | 功能说明 |
|---|---|
| `ConnectStatusEvent.java` | 连接状态变更事件（连接中/已连接/断开/失败） |
| `ConversationUpdatedEvent.java` | 会话列表更新事件 |
| `MessageUpdatedEvent.java` | 消息更新事件 |
| `MessageReadUpdatedEvent.java` | 消息已读回执更新事件 |
| `MessageTopEvent.java` | 消息置顶事件 |
| `ReactionUpdatedEvent.java` | 消息表情回应变更事件 |
| `UnreadMessageCountEvent.java` | 未读消息总数更新事件 |

---

## 八、数据模型与配置 — `model/`

| 文件 | 功能说明 |
|---|---|
| `ConfigUtils.java` | 全局配置（appKey、服务器 URL、Zego ID、Token 等） |
| `UiConversation.java` | 会话 UI 领域状态模型 |
| `UiMessage.java` | 消息 UI 领域状态模型 |
| `LocalMessage.java` | 轻量本地 Message 实现（UI-only 消息，如时间分割线） |
| `TraceContext.java` | 请求级 Trace 上下文（ThreadLocal traceId） |

---

## 九、网络请求层 — `server/`

### 9.1 HTTP 服务框架

| 文件 | 功能说明 |
|---|---|
| `ServiceManager.java` | 服务管理器（单例）。OkHttp 构建 UserService/MomentService，自动注入鉴权头 |
| `BaseService.java` | HTTP 服务基类。封装 JSON POST/GET 请求流程 |
| `ApiCallback.java` | API 回调接口 |
| `ApiException.java` | API 异常类 |
| `ApiErrorMapper.java` | API 错误分层映射器 |
| `SSLHelper.java` | TLS/SSL 策略配置 |

### 9.2 用户服务

| 文件 | 功能说明 |
|---|---|
| `UserService.java` | 用户服务接口。30+ 个 API 方法：登录/注册、好友管理、群组管理、用户信息、二维码、黑名单、反馈等 |
| `UserServiceImpl.java` | 用户服务 OkHttp 实现 |

### 9.3 朋友圈服务

| 文件 | 功能说明 |
|---|---|
| `MomentService.java` | 朋友圈服务接口。列表/发布/点赞/评论/删除 |
| `MomentServiceImpl.java` | 朋友圈服务 OkHttp 实现 |

### 9.4 API 数据传输对象 — `server/beans/`

| 文件 | 功能说明 |
|---|---|
| `HttpResult.java` | 通用 HTTP 响应包装（code/msg/data） |
| `ListResult.java` | 通用分页列表结果 |
| `LoginRequest.java` | 登录请求体 |
| `LoginResult.java` | 登录响应体 |
| `RegisterRequest.java` | 注册请求体 |
| `CodeRequest.java` | 验证码请求体 |
| `UserInfoBean.java` | 用户信息 |
| `UserInfoRequest.java` | 用户信息更新请求 |
| `FriendBean.java` | 好友信息 |
| `FriendsListData.java` | 好友列表分页数据 |
| `FriendApplicationBean.java` | 好友申请 |
| `FriendApplicationsData.java` | 好友申请列表数据 |
| `GroupBean.java` | 群组信息 |
| `GroupListData.java` | 群组列表数据 |
| `GroupDetailBean.java` | 群详情 |
| `GroupMemberBean.java` | 群成员 |
| `GroupMembersData.java` | 群成员列表数据 |
| `GroupManagementBean.java` | 群管理设置 |
| `GroupAnnouncementBean.java` | 群公告 |
| `CreateGroupResult.java` | 创建群组响应 |
| `BlockUsersData.java` | 黑名单列表数据 |
| `QRCodeBean.java` | 二维码 |
| `PostBean.java` | 朋友圈帖子 |
| `PostsListData.java` | 朋友圈帖子列表 |
| `TopCommentBean.java` | 热门评论 |
| `SearchUserBean.java` | 搜索用户 |
| `SelectFriendBean.java` | 选择好友 |
| `ReactionItem.java` | 表情回应项 |
| `ImageBean.java` | 图片 |
| `VideoBean.java` | 视频 |
| `ContentBean.java` | 内容 |
| `BotBean.java` | 机器人 |
| `ChatRoomBean.java` | 聊天室 |

---

## 十、Android 服务 — `service/`

| 文件 | 功能说明 |
|---|---|
| `ImForegroundService.java` | IM 前台服务。保持 IM 长连接不被系统回收，显示常驻通知 |

---

## 十一、工具类 — `utils/`

| 文件 | 功能说明 |
|---|---|
| `AvatarUtils.java` | 头像加载工具。URL 有效时 Glide 加载圆形头像；为空时生成彩色首字母默认头像 |
| `LogUtils.java` | 结构化日志工具（traceId/feature/event/result/detail） |
| `PermissionComponent.java` | 运行时权限公共组件。统一获取通话权限 |
| `ResourceUtils.java` | 资源工具（dp2px 转换） |
| `ToastUtils.java` | 自定义 Toast 工具 |

---

## 十二、自定义 UI 组件 — `widget/`

| 文件 | 功能说明 |
|---|---|
| `AppConfirmDialog.java` | 统一确认弹窗组件（Builder 模式） |
| `JuggleCheckBox.java` | 自定义 Checkbox（checked/unchecked/disabled 三态） |
| `JuggleSwitch.java` | 自定义 Switch 开关（开/关/禁用三态，带动画过渡） |

---

## 功能特性总结

### 用户与认证
- 账号密码登录、邮箱验证码登录
- 用户注册
- Token 加密存储（EncryptedSharedPreferences）
- 多端登录冲突处理（被踢下线）
- 启动路由决策（自动登录）

### 会话管理
- 会话列表展示（单向数据流 Reducer 架构）
- 会话置顶、免打扰、删除
- 未读消息计数
- 会话草稿保存
- 聊天背景自定义

### 消息功能
- 文本消息收发
- 图片消息（相册选择、拍照）
- 语音消息（录音发送）
- 文件消息
- 合并转发消息
- 表情贴纸消息
- 骰子/猜拳游戏消息
- 消息已读回执
- 消息撤回、删除
- 消息置顶
- 消息收藏
- 消息回复（引用回复）
- 消息表情回应（Reaction）
- 阅后即焚消息
- 流式文本消息
- 输入状态提示
- @提及功能（群聊中 @群成员）
- 消息搜索

### 社交关系
- 好友搜索与添加
- 好友申请（发送/接受/拒绝）
- 好友列表（拼音字母索引）
- 黑名单管理
- 二维码名片

### 群组功能
- 创建群聊
- 群成员管理（添加/移除）
- 群管理员设置
- 群公告管理
- 群二维码分享
- 群昵称编辑
- 群权限设置
- 退群/解散群

### 朋友圈
- 发布图文动态
- 浏览好友动态
- 点赞和评论
- 下拉刷新和上拉加载

### 音视频通话
- 单人音视频通话
- 多人音视频通话
- 来电浮窗（全局悬浮窗）
- 通话最小化恢复
- 通话计时

### 平台能力
- IM 前台服务保活
- 结构化日志
- 请求级 Trace 链路追踪
- TLS 安全策略
- 运行时权限管理
- 意见反馈
- 用户协议与隐私政策

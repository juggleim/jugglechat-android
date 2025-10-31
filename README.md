# JuggleIM Android Demo Application

JuggleIM Android Demo 是一个基于 [JuggleIM SDK](https://juggle.im) 开发的即时通讯 Android 应用示例。该应用展示了如何使用 JuggleIM SDK 实现一个完整的即时通讯功能，包括用户认证、会话管理、消息收发等核心功能。

## 功能特性

- 用户登录与认证
- 实时消息收发（文本、图片、语音等）
- 会话列表展示与管理
- 联系人管理
- 群组聊天
- 消息已读回执
- 多媒体消息支持（图片、视频等）
- 图片预览与选择
- 朋友圈功能
- 表情输入支持

## 技术架构

### 项目结构

```
app/
├── src/main/java/com/juggle/im/android/
│   ├── Application.java              # 应用入口点
│   ├── app/                          # 应用相关页面 (登录、注册、主界面等)
│   ├── chat/                         # 聊天相关功能模块
│   │   ├── ConversationActivity.java # 会话界面
│   │   ├── MessageListFragment.java  # 消息列表
│   │   ├── MomentsActivity.java      # 朋友圈功能
│   │   ├── view/                     # 聊天界面组件
│   │   │   ├── ChatInputActionBar.java # 聊天输入框组件
│   │   │   └── ...                    # 其他视图组件
│   │   ├── plugin/                   # 扩展插件 (图片、相机、文件等)
│   │   └── ...
│   ├── core/                         # 核心功能封装
│   │   └── JIMChatCore.java         # JuggleIM SDK 封装
│   ├── model/                        # 数据模型
│   └── ...
├── build.gradle                      # 项目依赖配置
└── src/main/AndroidManifest.xml     # 应用配置文件
```

### 核心组件

#### 1. JuggleIM SDK 集成

项目通过 Gradle 依赖方式集成了 JuggleIM SDK：

```gradle
implementation 'com.juggle.im:juggle:1.8.13.2'
```

#### 2. SDK 初始化

在 [Application.java](app/src/main/java/com/juggle/im/android/Application.java) 中进行 SDK 初始化：

```java
public class Application extends MultiDexApplication {
    @Override
    public void onCreate() {
        super.onCreate();
        JIMChatCore.getInstance().init(this, Collections.singletonList(ConfigUtils.imServer), ConfigUtils.appKey);
    }
}
```

#### 3. 核心功能封装

[JIMChatCore.java](app/src/main/java/com/juggle/im/android/core/JIMChatCore.java) 是对 JuggleIM SDK 的核心功能封装，主要包括：

- SDK 初始化
- 连接管理
- 会话同步
- 消息获取
- 事件监听

```java
public void init(Context context, List<String> serverList, String appKey) {
    if (context == null || serverList == null || appKey == null) {
        throw new IllegalArgumentException("Invalid arguments");
    }
    JIM.getInstance().setServerUrls(serverList);
    JIM.InitConfig.Builder builder = new JIM.InitConfig.Builder();
    JLogConfig.Builder logBuilder = new JLogConfig.Builder(context);
    logBuilder.setLogConsoleLevel(JLogLevel.JLogLevelVerbose);
    builder.setJLogConfig(new JLogConfig(logBuilder));
    JIM.getInstance().getMessageManager().registerContentType(FriendNotifyMessage.class);
    JIM.getInstance().getMessageManager().registerContentType(GroupNotifyMessage.class);
    JIM.getInstance().init(context, appKey, builder.build());
    initListener();
    // ... 连接状态监听
}
```

#### 4. 消息收发

在 [ConversationActivity.java](app/src/main/java/com/juggle/im/android/chat/ConversationActivity.java) 中展示了如何使用 SDK 发送各种类型的消息：

##### 文本消息发送

```java
private void sendTextMessage(TextMessage text, Conversation conversation) {
    IMessageManager.ISendMessageCallback callback = new IMessageManager.ISendMessageCallback() {
        @Override
        public void onSuccess(Message message) {
            MessageListFragment frag = (MessageListFragment) getSupportFragmentManager().findFragmentById(R.id.fragment_container);
            if (frag != null) {
                frag.onUpdateMessage(Arrays.asList(message));
            }
        }

        @Override
        public void onError(Message message, int errorCode) {
            Log.i("TAG", "send message error: " + errorCode);
            MessageListFragment frag = (MessageListFragment) getSupportFragmentManager().findFragmentById(R.id.fragment_container);
            if (frag != null) {
                frag.onUpdateMessage(Arrays.asList(message));
            }
        }
    };
    Message message = JIM.getInstance().getMessageManager().sendMessage(text, conversation, callback);
    MessageListFragment frag = (MessageListFragment) getSupportFragmentManager().findFragmentById(R.id.fragment_container);
    if (frag != null) {
        frag.onNewMessage(message);
    }
}
```

##### 媒体消息发送（图片、语音、文件）

```java
private void sendImageMessage(ImageMessage image, Conversation conversation) {
    final MessageListFragment frag = (MessageListFragment) getSupportFragmentManager().findFragmentById(R.id.fragment_container);
    IMessageManager.ISendMediaMessageCallback callback = new IMessageManager.ISendMediaMessageCallback() {
        @Override
        public void onProgress(int progress, Message message) {
            Log.i("sendImageMessage", "onProgress: " + progress);
        }

        @Override
        public void onSuccess(Message message) {
            Log.i("sendImageMessage", "send message success");
            frag.onUpdateMessage(Arrays.asList(message));
        }

        @Override
        public void onError(Message message, int errorCode) {
            Log.i("sendImageMessage", "send message error: " + errorCode);
            frag.onUpdateMessage(Arrays.asList(message));
        }

        @Override
        public void onCancel(Message message) {
            Log.i("sendImageMessage", "onCancel");
        }
    };
    Message message = JIM.getInstance().getMessageManager().sendMediaMessage(image, conversation, callback);
    Log.i("TAG", "sendImageMessage msgId= " + message.getMessageId());
    if (frag != null) {
        frag.onNewMessage(message);
    }
}
```

##### 消息接收

通过实现 `IMessageManager.IMessageListener` 接口来监听消息接收事件：

```java
JIM.getInstance().getMessageManager().addListener("msg", new IMessageManager.IMessageListener() {
    @Override
    public void onMessageReceive(Message message) {
        Log.d(tag, "onMessageReceive: " + message.toString());
        EventBus.getDefault().post(new MessageUpdatedEvent(message));
    }

    @Override
    public void onMessageRecall(Message message) {
        // 处理消息撤回
    }

    @Override
    public void onMessageDelete(Conversation conversation, List<Long> list) {
        // 处理消息删除
    }

    // ... 其他消息事件处理
});
```

#### 5. 会话管理

##### 会话列表同步

在 [JIMChatCore.java](app/src/main/java/com/juggle/im/android/core/JIMChatCore.java) 中展示了如何同步会话列表：

```java
/**
 * 同步会话列表
 * 应用初次启动，先进行会话列表同步、更新
 * 1、先用当前时间戳取第一屏会话
 * 2、如果还有，用第一屏的最后一条会话的sortTime取第二屏会话，拉取 OLDER 会话数据
 */
public void syncConversationList() {
    long cursor = -1;
    for(;;) {
        List<ConversationInfo> conversationInfoList = JIM.getInstance().getConversationManager().getConversationInfoList(20, cursor, JIMConst.PullDirection.NEWER);
        if (conversationInfoList == null || conversationInfoList.isEmpty()) {
            Log.i(tag, "empty conversation");
            break;
        }
        Log.d(tag, "fetch conversation size: " + conversationInfoList.size());

        // Post conversation update event for this page
        EventBus.getDefault().post(new ConversationUpdatedEvent(conversationInfoList));

        // prepare for next page: use the last item's sortTime as cursor
        ConversationInfo last = conversationInfoList.get(conversationInfoList.size() - 1);
        cursor = last.getSortTime();

        if (conversationInfoList.size() < 20) {
            Log.i(tag, "fetch conversation end");
            break;
        }
    }
    int c = JIM.getInstance().getConversationManager().getTotalUnreadCount();
    EventBus.getDefault().post(new UnreadMessageCountEvent(c));
}
```

##### 会话事件监听

```java
JIM.getInstance().getConversationManager().addListener("conversationList", new IConversationManager.IConversationListener() {
    @Override
    public void onConversationInfoAdd(List<ConversationInfo> list) {
        Log.i(tag, "onConversationInfoAdd: " + list.size());
        EventBus.getDefault().post(new ConversationUpdatedEvent(list));
    }

    @Override
    public void onConversationInfoUpdate(List<ConversationInfo> list) {
        Log.i(tag, "onConversationInfoUpdate: " + list.size());
        EventBus.getDefault().post(new ConversationUpdatedEvent(list));
    }

    @Override
    public void onConversationInfoDelete(List<ConversationInfo> list) {
        // 处理会话删除
    }

    @Override
    public void onTotalUnreadMessageCountUpdate(int i) {
        EventBus.getDefault().post(new UnreadMessageCountEvent(i));
    }
});
```

##### 获取会话中的消息

在 [JIMChatCore.java](app/src/main/java/com/juggle/im/android/core/JIMChatCore.java) 中展示了如何获取会话中的消息列表：

```java
/**
 * 根据会话 ID 获取会话的消息列表
 * @param conversationId 会话 ID
 * @param count 获取消息数量
 * @param msgTime 消息时间戳 第一次获取传 0
 */
public void getMessages(String conversationId, Conversation.ConversationType conversationType, int count, long msgTime, IMessageManager.IGetMessagesCallbackV3 callback) {
    GetMessageOptions options = new GetMessageOptions();
    options.setCount(count);
    options.setStartTime(msgTime);
    Conversation conversation = new Conversation(conversationType, conversationId);
    JIM.getInstance().getMessageManager().getMessages(conversation, JIMConst.PullDirection.OLDER, options, new IMessageManager.IGetMessagesCallbackV3() {
        @Override
        public void onGetMessages(List<Message> messages, long timestamp, boolean hasMore, int code) {
            Log.d("TAG", "messageList count is " + messages.size());
            callback.onGetMessages(messages, timestamp, hasMore, code);
        }
    });
}
```

##### 会话操作

在 [ConversationListFragment.java](app/src/main/java/com/juggle/im/android/chat/ConversationListFragment.java) 中展示了会话列表的管理和操作：

```java
@Override
public void onConversationClick(UiConversation uiConversation) {
    JIM.getInstance().getConversationManager().clearUnreadCount(uiConversation.getConversationInfo().getConversation(), null);
    Intent intent = ConversationActivity.intentFor(this.getActivity(),
            uiConversation.getConversationInfo().getConversation().getConversationId(),
            uiConversation.getName(),
            uiConversation.getConversationInfo().getConversation().getConversationType().equals(Conversation.ConversationType.GROUP),
            uiConversation.isTop(),
            uiConversation.isMuted());
    startActivity(intent);
}
```

#### 6. 多媒体支持

项目支持多种媒体类型消息，包括图片、视频等。在 [AlbumActivity.java](app/src/main/java/com/juggle/im/android/chat/AlbumActivity.java) 中展示了图片选择功能的实现。

### 事件处理

项目使用 EventBus 进行组件间通信，处理消息、会话等事件：

```java
JIM.getInstance().getConversationManager().addListener("conversationList", new IConversationManager.IConversationListener() {
    @Override
    public void onConversationInfoAdd(List<ConversationInfo> list) {
        Log.i(tag, "onConversationInfoAdd: " + list.size());
        EventBus.getDefault().post(new ConversationUpdatedEvent(list));
    }

    @Override
    public void onConversationInfoUpdate(List<ConversationInfo> list) {
        Log.i(tag, "onConversationInfoUpdate: " + list.size());
        EventBus.getDefault().post(new ConversationUpdatedEvent(list));
    }
    // ...
});
```

#### 6. 朋友圈功能

在 [MomentsActivity.java](app/src/main/java/com/juggle/im/android/chat/MomentsActivity.java) 中实现了类似微信的朋友圈功能，支持：

- 发布图文动态
- 浏览好友动态
- 点赞和评论
- 下拉刷新和上拉加载更多

主要功能实现：

```java
// 发布动态
private void submitPost() {
    String content = editPostContent.getText().toString().trim();
    if (TextUtils.isEmpty(content) && mImageUrls.isEmpty()) {
        Toast.makeText(this, "内容不能为空", Toast.LENGTH_SHORT).show();
        return;
    }
    
    PostBean post = new PostBean();
    post.setContent(content);
    // ... 设置图片、视频等信息
    ServiceManager.getPostService().createPost(post, new ApiCallback<PostBean>() {
        @Override
        public void onSuccess(PostBean data) {
            // 发布成功处理
        }
        
        @Override
        public void onError(int code, String message) {
            // 错误处理
        }
    });
}
```

#### 7. 输入框和表情支持

在 [ChatInputActionBar.java](app/src/main/java/com/juggle/im/android/chat/view/ChatInputActionBar.java) 中实现了完整的聊天输入框功能：

##### 文本输入和表情面板

```java
// 显示表情面板
private void showEmojiPanel() {
    showInputArea(true);
    ensurePanelHeight();
    showPanel(getEmojiPanel(), InputMode.EMOJI);
    // 隐藏软键盘
    imm.hideSoftInputFromWindow(editMessage.getWindowToken(), 0);
}

// 构建表情列表
private List<String> buildEmojiListLarge() {
    String[] arr = new String[]{"\uD83D\uDE00", "\uD83D\uDE01", "\uD83D\uDE02", "\uD83D\uDE03"};
    List<String> list = new ArrayList<>();
    for (String s : arr) list.add(s);
    return list;
}
```

##### 扩展功能面板

```java
// 显示更多功能面板（图片、相机等）
private void showMorePanel() {
    showInputArea(true);
    ensurePanelHeight();
    showPanel(getMorePanel(), InputMode.MORE);
    imm.hideSoftInputFromWindow(editMessage.getWindowToken(), 0);
}
```

##### 输入框事件监听

```java
public interface Listener {
    void onSend(String text);  // 发送文本消息
    void onStartVoiceRecord(); // 开始语音录制
    void onFinishRecord(String voiceUrl, long duration); // 完成语音录制
    void onMoreAction(String pluginId, String action, Object data); // 扩展功能操作
    void onPanelVisibilityChanged(boolean visible); // 面板可见性变化
}
```

## 运行项目

### 环境要求

- Android Studio 4.0 或更高版本
- JDK 8 或更高版本
- Android SDK API 34 (Android 14)
- 支持 Android 6.0 (API 23) 及以上版本的设备

### 构建和运行

1. 克隆项目到本地：
   ```bash
   git clone <项目地址>
   ```

2. 在 Android Studio 中打开项目

3. 同步 Gradle 依赖

4. 构建并运行项目

### 构建命令

```bash
# 构建调试版本
./gradlew assembleDebug

# 构建发布版本
./gradlew assembleRelease

# 运行单元测试
./gradlew test
```

## 依赖库

- [JuggleIM SDK](https://juggle.im) - 即时通讯核心功能
- [EventBus](https://github.com/greenrobot/EventBus) - 事件总线
- [Glide](https://github.com/bumptech/glide) - 图片加载
- [PhotoView](https://github.com/chrisbanes/PhotoView) - 图片缩放查看
- [Retrofit](https://square.github.io/retrofit/) - 网络请求
- AndroidX 系列库

## 许可证

本项目仅供学习和参考使用，不提供商业授权。
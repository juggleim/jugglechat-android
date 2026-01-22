package com.juggle.im.android.core;

import android.content.Context;
import android.util.Log;

import com.juggle.im.JIM;
import com.juggle.im.JIMConst;
import com.juggle.im.android.chat.message.FriendNotifyMessage;
import com.juggle.im.android.chat.message.GroupNotifyMessage;
import com.juggle.im.android.event.ConnectStatusEvent;
import com.juggle.im.android.event.MessageReadUpdatedEvent;
import com.juggle.im.android.event.MessageTopEvent;
import com.juggle.im.android.event.MessageUpdatedEvent;
import com.juggle.im.android.event.UnreadMessageCountEvent;
import com.juggle.im.android.model.ConfigUtils;
import com.juggle.im.interfaces.IConnectionManager;
import com.juggle.im.interfaces.IConversationManager;
import com.juggle.im.interfaces.IMessageManager;
import com.juggle.im.internal.logger.JLogConfig;
import com.juggle.im.internal.logger.JLogLevel;
import com.juggle.im.model.Conversation;
import com.juggle.im.model.ConversationInfo;

import java.util.List;
import java.util.Map;

import org.greenrobot.eventbus.EventBus;
import com.juggle.im.android.event.ConversationUpdatedEvent;
import com.juggle.im.model.GetMessageOptions;
import com.juggle.im.model.GroupMessageReadInfo;
import com.juggle.im.model.Message;
import com.juggle.im.model.MessageReaction;
import com.juggle.im.model.UserInfo;

/**
 * IM核心类，负责IM功能的初始化和连接管理
 * 采用单例模式确保全局唯一实例
 */
public class JIMChatCore {
    private static final String tag = "JIMCore";
    private static volatile JIMChatCore instance;
    private static final Object lock = new Object();
    
    private JIMChatCore() {
        // 私有构造函数，防止外部实例化
    }
    
    /**
     * 获取IMCore的单例实例
     * @return IMCore实例
     */
    public static JIMChatCore getInstance() {
        if (instance == null) {
            synchronized (lock) {
                if (instance == null) {
                    instance = new JIMChatCore();
                }
            }
        }
        return instance;
    }
    
    /**
     * 初始化IM
     * @param context Android上下文
     * @param serverList 服务器列表
     * @param appKey 应用密钥
     */
    public void init(Context context, List<String> serverList, String appKey) {
        if (context == null || serverList == null || appKey == null) {
            throw new IllegalArgumentException("Invalid arguments");
        }
        /**
         * 需要申请对应自己音视频 ID，这个仅仅用来测试
         */
        JIM.getInstance().getCallManager().initZegoEngine(ConfigUtils.zegoId, context);
        JIM.getInstance().setServerUrls(serverList);
        JIM.InitConfig.Builder builder = new JIM.InitConfig.Builder();
        JLogConfig.Builder logBuilder = new JLogConfig.Builder(context);
        logBuilder.setLogConsoleLevel(JLogLevel.JLogLevelVerbose);
        builder.setJLogConfig(new JLogConfig(logBuilder));
        JIM.getInstance().getMessageManager().registerContentType(FriendNotifyMessage.class);
        JIM.getInstance().getMessageManager().registerContentType(GroupNotifyMessage.class);
        JIM.getInstance().init(context, appKey, builder.build());
        initListener();
        JIM.getInstance().getConnectionManager().addConnectionStatusListener("conn", new IConnectionManager.IConnectionStatusListener() {
            @Override
            public void onStatusChange(JIMConst.ConnectionStatus connectionStatus, int i, String s) {
                Log.i(tag, "connection status change: " + connectionStatus + ", user = " + s);
                EventBus.getDefault().post(new ConnectStatusEvent(connectionStatus, i, s));
            }

            @Override
            public void onDbOpen() {
                Log.i(tag, "db open");
                syncConversationList();
            }

            @Override
            public void onDbClose() {
                Log.i(tag, "db close");
            }
        });
    }

    /**
     * 链接 IM
     * @param token 访问令牌
     */
    public void connect(String token) {
        JIM.getInstance().getConnectionManager().connect(token);
    }

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

    /**
     * 加载更多会话（分页）
     * @param cursor 上次加载的最后一条会话的sortTime，第一次传-1
     * @param pageSize 每页数量
     * @return 加载的会话数量
     */
    public int loadMoreConversations(long cursor, int pageSize) {
        List<ConversationInfo> conversationInfoList = JIM.getInstance().getConversationManager()
                .getConversationInfoList(pageSize, cursor, JIMConst.PullDirection.OLDER);
        if (conversationInfoList == null || conversationInfoList.isEmpty()) {
            Log.i(tag, "no more conversations to load");
            return 0;
        }
        Log.d(tag, "load more conversations size: " + conversationInfoList.size());

        // Post conversation update event for this page
        EventBus.getDefault().post(new ConversationUpdatedEvent(conversationInfoList));

        return conversationInfoList.size();
    }

    private void initListener() {

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

            }

            @Override
            public void onTotalUnreadMessageCountUpdate(int i) {
                EventBus.getDefault().post(new UnreadMessageCountEvent(i));
            }
        });
        JIM.getInstance().getMessageManager().addListener("msg", new IMessageManager.IMessageListener() {
            @Override
            public void onMessageReceive(Message message) {
                Log.d(tag, "onMessageReceive: " + message.toString());
                EventBus.getDefault().post(new MessageUpdatedEvent(message));
            }

            @Override
            public void onMessageRecall(Message message) {
                Log.d(tag, "onMessageRecall: " + message.toString());
            }

            @Override
            public void onMessageDelete(Conversation conversation, List<Long> list) {

            }

            @Override
            public void onMessageClear(Conversation conversation, long l, String s) {

            }

            @Override
            public void onMessageUpdate(Message message) {
                Log.d(tag, "onMessageUpdate: " + message.toString());

            }

            @Override
            public void onMessageReactionAdd(Conversation conversation, MessageReaction messageReaction) {
                Log.d(tag, "onMessageReactionAdd: " + messageReaction.toString());
            }

            @Override
            public void onMessageReactionRemove(Conversation conversation, MessageReaction messageReaction) {
                Log.d(tag, "onMessageReactionRemove: " + messageReaction.toString());
            }

            @Override
            public void onMessageSetTop(Message message, UserInfo userInfo, boolean b) {
                Log.d(tag, "onMessageSetTop: " + message.toString());
                EventBus.getDefault().post(new MessageTopEvent(message, userInfo, b));
            }
        });

        JIM.getInstance().getMessageManager().addReadReceiptListener("readReceipt", new IMessageManager.IMessageReadReceiptListener() {
            @Override
            public void onMessagesRead(Conversation conversation, List<String> list) {
                EventBus.getDefault().post(new MessageReadUpdatedEvent(conversation, list));
            }

            @Override
            public void onGroupMessagesRead(Conversation conversation, Map<String, GroupMessageReadInfo> map) {

            }
        });
    }

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
}

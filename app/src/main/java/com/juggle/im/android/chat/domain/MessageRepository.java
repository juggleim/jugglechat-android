package com.juggle.im.android.chat.domain;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.juggle.im.JIM;
import com.juggle.im.android.core.JIMChatCore;
import com.juggle.im.android.model.UiMessage;
import com.juggle.im.interfaces.IMessageManager;
import com.juggle.im.model.Conversation;
import com.juggle.im.model.Message;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 消息仓储：统一封装 SDK 消息读取、领域映射与已读回执逻辑。
 * <p>
 * 设计目标：
 * 1. UI 层只处理 UiMessage，避免分散的 SDK 细节依赖。
 * 2. 已读回执判定集中处理，避免页面间重复和规则漂移。
 * 3. 通过 Gateway 抽象便于替换实现并做单测。
 */
public final class MessageRepository {

    /**
     * SDK/数据源访问抽象。
     */
    public interface Gateway {

        /**
         * 拉取消息分页。
         */
        void getMessages(@NonNull String conversationId,
                @NonNull Conversation.ConversationType conversationType,
                int count,
                long startTime,
                @NonNull IMessageManager.IGetMessagesCallbackV3 callback);

        /**
         * 发送已读回执。
         */
        void sendReadReceipt(@NonNull Conversation conversation,
                @NonNull List<String> messageIds);

        /**
         * 当前登录用户 ID。
         */
        @Nullable
        String getCurrentUserId();
    }

    /**
     * 消息分页回调。
     */
    public interface MessagePageCallback {
        void onResult(@NonNull MessagePage page);
    }

    /**
     * 消息分页结果。
     */
    public static final class MessagePage {
        private final List<UiMessage> messages;
        private final long cursor;
        private final boolean hasMore;
        private final int code;

        public MessagePage(@NonNull List<UiMessage> messages, long cursor, boolean hasMore, int code) {
            this.messages = messages;
            this.cursor = cursor;
            this.hasMore = hasMore;
            this.code = code;
        }

        @NonNull
        public List<UiMessage> getMessages() {
            return messages;
        }

        public long getCursor() {
            return cursor;
        }

        public boolean isHasMore() {
            return hasMore;
        }

        public int getCode() {
            return code;
        }
    }

    private final Gateway gateway;

    public MessageRepository() {
        this(new JuggleGateway());
    }

    public MessageRepository(@NonNull Gateway gateway) {
        this.gateway = gateway;
    }

    /**
     * 拉取消息分页并映射为 UiMessage。
     */
    public void fetchPage(@NonNull String conversationId,
            @NonNull Conversation.ConversationType conversationType,
            int count,
            long startTime,
            @NonNull MessagePageCallback callback) {
        gateway.getMessages(conversationId, conversationType, count, startTime,
                (messages, timestamp, hasMore, code) -> {
                    List<UiMessage> uiMessages = toUiMessages(messages);
                    callback.onResult(new MessagePage(uiMessages, timestamp, hasMore, code));
                });
    }

    /**
     * SDK Message -> UiMessage 统一映射。
     */
    @NonNull
    public List<UiMessage> toUiMessages(@Nullable List<Message> messages) {
        if (messages == null || messages.isEmpty()) {
            return new ArrayList<>();
        }

        List<UiMessage> uiMessages = new ArrayList<>(messages.size());
        for (Message message : messages) {
            UiMessage uiMessage = UiMessage.fromMessage(message);
            if (uiMessage != null) {
                uiMessages.add(uiMessage);
            }
        }

        // 统一按时间倒序输出，保证不同入口消费时序一致。
        uiMessages.sort((left, right) -> Long.compare(right.getTimestamp(), left.getTimestamp()));
        return uiMessages;
    }

    /**
     * 收集需要发送已读回执的消息 ID。
     */
    @NonNull
    public List<String> collectUnreadMessageIds(@Nullable List<UiMessage> messages) {
        if (messages == null || messages.isEmpty()) {
            return Collections.emptyList();
        }

        List<String> result = new ArrayList<>();
        for (UiMessage uiMessage : messages) {
            if (uiMessage == null || uiMessage.getMessage() == null) {
                continue;
            }
            Message raw = uiMessage.getMessage();
            if (!raw.isHasRead() && raw.getDirection() == Message.MessageDirection.RECEIVE) {
                result.add(uiMessage.getMessageId());
            }
        }
        return result;
    }

    /**
     * 按当前规则发送已读回执（仅 RECEIVE 且未读消息）。
     */
    public void markReadIfNeeded(@NonNull Conversation conversation,
            @Nullable List<UiMessage> messages) {
        List<String> unreadIds = collectUnreadMessageIds(messages);
        if (unreadIds.isEmpty()) {
            return;
        }
        gateway.sendReadReceipt(conversation, unreadIds);
    }

    /**
     * 当前登录用户 ID（空值时返回空串）。
     */
    @NonNull
    public String currentUserId() {
        return trimToEmpty(gateway.getCurrentUserId());
    }

    @NonNull
    private static String trimToEmpty(@Nullable String value) {
        return value == null ? "" : value.trim();
    }

    private static final class JuggleGateway implements Gateway {

        @Override
        public void getMessages(@NonNull String conversationId,
                @NonNull Conversation.ConversationType conversationType,
                int count,
                long startTime,
                @NonNull IMessageManager.IGetMessagesCallbackV3 callback) {
            JIMChatCore.getInstance().getMessages(conversationId, conversationType, count, startTime, callback);
        }

        @Override
        public void sendReadReceipt(@NonNull Conversation conversation,
                @NonNull List<String> messageIds) {
            JIM.getInstance().getMessageManager().sendReadReceipt(conversation, messageIds, null);
        }

        @Nullable
        @Override
        public String getCurrentUserId() {
            return JIM.getInstance().getCurrentUserId();
        }
    }
}

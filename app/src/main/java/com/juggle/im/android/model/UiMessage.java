package com.juggle.im.android.model;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.juggle.im.model.Message;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * 消息 UI 领域状态。
 * <p>
 * 设计说明：
 * - 对外提供稳定字段（contentType、rendererKey），为自定义消息类型扩展做准备。
 * - 保留原始 SDK Message，兼容当前存量渲染链路。
 */
public class UiMessage {
    @Nullable
    private final Message message;
    private final String messageId;
    private final long timestamp;
    private final Message.MessageDirection direction;
    private final String senderId;
    private String senderName = "";
    private String messageSummary = "";
    private final String contentType;
    private String rendererKey;
    private final String conversationId;
    private final String conversationTypeKey;
    private final long clientMsgNo;
    private final boolean synthetic;
    private final Map<String, Object> extensions = new LinkedHashMap<>();

    private UiMessage(@Nullable Message message,
            @NonNull String messageId,
            long timestamp,
            @NonNull Message.MessageDirection direction,
            @NonNull String senderId,
            @NonNull String contentType,
            @NonNull String rendererKey,
            @NonNull String conversationId,
            @NonNull String conversationTypeKey,
            long clientMsgNo,
            boolean synthetic) {
        this.message = message;
        this.messageId = messageId;
        this.timestamp = timestamp;
        this.direction = direction;
        this.senderId = senderId;
        this.contentType = contentType;
        this.rendererKey = rendererKey;
        this.conversationId = conversationId;
        this.conversationTypeKey = conversationTypeKey;
        this.clientMsgNo = clientMsgNo;
        this.synthetic = synthetic;
    }

    @Nullable
    public Message getMessage() {
        return message;
    }

    @NonNull
    public String getMessageId() {
        return messageId;
    }

    public long getTimestamp() {
        return timestamp;
    }

    @NonNull
    public Message.MessageDirection getDirection() {
        return direction;
    }

    @NonNull
    public String getSenderId() {
        return senderId;
    }

    @NonNull
    public String getSenderName() {
        return senderName;
    }

    public void setSenderName(@Nullable String senderName) {
        this.senderName = trimToEmpty(senderName);
    }

    @NonNull
    public String getMessageSummary() {
        return messageSummary;
    }

    public void setMessageSummary(@Nullable String messageSummary) {
        this.messageSummary = trimToEmpty(messageSummary);
    }

    /**
     * SDK contentType（例如 text/image/custom:red_packet）。
     */
    @NonNull
    public String getContentType() {
        return contentType;
    }

    /**
     * 渲染路由 key。默认与 contentType 一致，可按业务重写到自定义渲染器。
     */
    @NonNull
    public String getRendererKey() {
        return rendererKey;
    }

    public void setRendererKey(@Nullable String rendererKey) {
        String normalized = trimToEmpty(rendererKey);
        this.rendererKey = normalized.isEmpty() ? contentType : normalized;
    }

    @NonNull
    public String getConversationId() {
        return conversationId;
    }

    @NonNull
    public String getConversationTypeKey() {
        return conversationTypeKey;
    }

    public long getClientMsgNo() {
        return clientMsgNo;
    }

    public boolean isSynthetic() {
        return synthetic;
    }

    /**
     * 扩展字段：为业务差异化展示提供附加信息，不影响主流程字段。
     */
    public void putExtension(@NonNull String key, @Nullable Object value) {
        extensions.put(key, value);
    }

    @Nullable
    public Object getExtension(@NonNull String key) {
        return extensions.get(key);
    }

    @NonNull
    public Map<String, Object> getExtensions() {
        return Collections.unmodifiableMap(extensions);
    }

    /**
     * Create a UiMessage from SDK Message.
     */
    @Nullable
    public static UiMessage fromMessage(@Nullable Message message) {
        if (message == null) {
            return null;
        }

        String messageId = trimToEmpty(message.getMessageId());
        long timestamp = message.getTimestamp();
        Message.MessageDirection direction = message.getDirection() != null
                ? message.getDirection()
                : Message.MessageDirection.RECEIVE;
        String senderId = trimToEmpty(message.getSenderUserId());
        String contentType = resolveContentType(message);
        String conversationId = "";
        String conversationTypeKey = "";
        if (message.getConversation() != null) {
            conversationId = trimToEmpty(message.getConversation().getConversationId());
            if (message.getConversation().getConversationType() != null) {
                conversationTypeKey = message.getConversation().getConversationType().name();
            }
        }

        return new UiMessage(
                message,
                messageId,
                timestamp,
                direction,
                senderId,
                contentType,
                contentType,
                conversationId,
                conversationTypeKey,
                message.getClientMsgNo(),
                message instanceof LocalMessage);
    }

    /**
     * 用于 Diff/去重的稳定键：优先 messageId，其次 clientMsgNo。
     */
    @NonNull
    public String getStableKey() {
        if (!messageId.isEmpty()) {
            return messageId;
        }
        if (clientMsgNo > 0L) {
            return String.valueOf(clientMsgNo);
        }
        return direction.name() + "_" + senderId + "_" + timestamp + "_" + contentType;
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }
        if (!(object instanceof UiMessage)) {
            return false;
        }
        UiMessage that = (UiMessage) object;
        return Objects.equals(getStableKey(), that.getStableKey());
    }

    @Override
    public int hashCode() {
        return Objects.hash(getStableKey());
    }

    @NonNull
    private static String resolveContentType(@NonNull Message message) {
        String contentType = trimToEmpty(message.getContentType());
        if (!contentType.isEmpty()) {
            return contentType;
        }
        if (message.getContent() != null) {
            return message.getContent().getClass().getName();
        }
        return "unknown";
    }

    @NonNull
    private static String trimToEmpty(@Nullable String value) {
        return value == null ? "" : value.trim();
    }
}

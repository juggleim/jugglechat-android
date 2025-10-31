package com.juggle.im.android.model;

import androidx.annotation.Nullable;

import com.juggle.im.android.chat.utils.MessageUtils;
import com.juggle.im.model.Conversation;
import com.juggle.im.model.Message;

import java.lang.reflect.Method;
import java.util.Objects;

/**
 * Lightweight UI wrapper around the SDK Message model.
 * Extracts common fields (id, timestamp, direction, sender, content) used by adapter/fragment.
 * Uses reflection to try several common timestamp getters on the SDK Message when needed.
 */
public class UiMessage {
    private final Message message;
    private final String messageId;
    private final long timestamp;
    private final Message.MessageDirection direction;
    private final String senderId;
    private String senderName;
    private String messageSummary;

    private UiMessage(Message message, String messageId, long timestamp, Message.MessageDirection direction, String senderId) {
        this.message = message;
        this.messageId = messageId;
        this.timestamp = timestamp;
        this.direction = direction;
        this.senderId = senderId;
    }

    public Message getMessage() {
        return message;
    }

    public String getMessageId() {
        return messageId;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public Message.MessageDirection getDirection() {
        return direction;
    }

    public String getSenderId() {
        return senderId;
    }

    /**
     * Create a UiMessage from an SDK Message. We try to extract a timestamp using common getter names
     * via reflection to avoid coupling to a specific SDK version.
     */
    @Nullable
    public static UiMessage fromMessage(@Nullable Message m) {
        if (m == null) return null;

        String id = null;
        long ts = 0L;
        String sender = null;
        id = m.getMessageId();
        ts = m.getTimestamp();
        sender = m.getSenderUserId();
        UiMessage uiMessage = new UiMessage(m, id, ts, m.getDirection(), sender);
        return uiMessage;
    }

    public String getSenderName() {
        return senderName;
    }

    public void setSenderName(String senderName) {
        this.senderName = senderName;
    }

    public String getMessageSummary() {
        return messageSummary;
    }

    public void setMessageSummary(String messageSummary) {
        this.messageSummary = messageSummary;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof UiMessage)) return false;
        UiMessage uiMessage = (UiMessage) o;
        return Objects.equals(messageId, uiMessage.messageId);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(messageId);
    }
}
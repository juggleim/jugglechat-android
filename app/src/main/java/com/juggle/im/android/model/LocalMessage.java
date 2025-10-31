package com.juggle.im.android.model;

import com.juggle.im.model.Message;
import com.juggle.im.model.MessageContent;

/**
 * Lightweight local Message implementation used for synthetic UI-only messages
 * such as InsertTimeStatusMessage. Only methods used by UiMessage#fromMessage
 * need to be implemented.
 */
public class LocalMessage extends Message {
    private String messageId;
    private long timestamp;
    private MessageDirection direction = MessageDirection.RECEIVE;
    private String senderUserId;
    private MessageContent content;

    @Override
    public MessageState getState() {
        return MessageState.UNKNOWN;
    }

    public LocalMessage(long ts, MessageContent content) {
        this.messageId = System.currentTimeMillis() + "";
        this.timestamp = ts;
        this.content = content;
    }

    public void setDirection(MessageDirection d) { this.direction = d; }

    public void setSenderUserId(String sender) { this.senderUserId = sender; }

    @Override
    public String getMessageId() {
        return messageId;
    }

    @Override
    public long getTimestamp() {
        return timestamp;
    }

    @Override
    public MessageDirection getDirection() {
        return direction;
    }

    @Override
    public String getSenderUserId() {
        return senderUserId;
    }

    @Override
    public MessageContent getContent() {
        return content;
    }
}

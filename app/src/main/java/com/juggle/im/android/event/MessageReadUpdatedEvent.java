package com.juggle.im.android.event;


import com.juggle.im.model.Conversation;

import java.util.List;

public class MessageReadUpdatedEvent {
    private final List<String> messageIds;
    private final Conversation conversation;

    public MessageReadUpdatedEvent(Conversation conversation, List<String> messageIds) {
        this.messageIds = messageIds;
        this.conversation = conversation;
    }

    public List<String> getMessageIds() {
        return messageIds;
    }

    public Conversation getConversation() {
        return conversation;
    }
}

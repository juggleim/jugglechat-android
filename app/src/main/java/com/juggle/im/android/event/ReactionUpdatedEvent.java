package com.juggle.im.android.event;

import com.juggle.im.model.Conversation;
import com.juggle.im.model.MessageReaction;

public class ReactionUpdatedEvent {
    private final Conversation conversation;
    private final MessageReaction messageReaction;
    private final boolean isAdd;

    public ReactionUpdatedEvent(Conversation conversation, MessageReaction messageReaction, boolean isAdd) {
        this.conversation = conversation;
        this.messageReaction = messageReaction;
        this.isAdd = isAdd;
    }

    public Conversation getConversation() {
        return conversation;
    }

    public MessageReaction getMessageReaction() {
        return messageReaction;
    }

    public boolean isAdd() {
        return isAdd;
    }
}
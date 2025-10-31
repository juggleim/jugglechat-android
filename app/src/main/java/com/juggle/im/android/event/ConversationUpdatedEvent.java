package com.juggle.im.android.event;

import com.juggle.im.model.ConversationInfo;

import java.util.List;

public class ConversationUpdatedEvent {
    private final List<ConversationInfo> conversationInfoList;

    public ConversationUpdatedEvent(List<ConversationInfo> conversationInfoList) {
        this.conversationInfoList = conversationInfoList;
    }

    public List<ConversationInfo> getConversationInfoList() {
        return conversationInfoList;
    }
}

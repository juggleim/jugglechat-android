package com.juggle.im.android.model;

import com.juggle.im.model.ConversationInfo;
import com.juggle.im.model.Message;

public class UiConversation {
    private ConversationInfo conversationInfo;
    private String id;
    private String name;
    private String lastMessageUserName;
    private String avatar;
    private boolean isGroup;
    private boolean isMuted;
    private boolean isTop;
    private long topTime;

    // Getters and Setters
    public String getId() { return conversationInfo.getConversation().getConversationId(); }
    public String getName() { return name; }
    public String getAvatar() { return avatar; }

    public String getLastMessageUserName() {
        return lastMessageUserName;
    }

    public void setLastMessageUserName(String lastMessageUserName) {
        this.lastMessageUserName = lastMessageUserName;
    }

    public Message getLastMessage() { return conversationInfo.getLastMessage(); }
    public long getSortTime() { return conversationInfo.getSortTime(); }

    public void setId(String id) {
        this.id = id;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setAvatar(String avatar) {
        this.avatar = avatar;
    }

    public boolean isTop() { return isTop; }
    public long getTopTime() { return topTime; }

    public boolean isMuted() {
        return conversationInfo.isMute();
    }

    public int getUnreadCount() {
        return conversationInfo != null ? conversationInfo.getUnreadCount() : 0;
    }

    public static UiConversation fromConversationInfo(ConversationInfo info) {
        UiConversation ui = new UiConversation();
        ui.conversationInfo = info;
        if (info != null) {
            ui.id = info.getConversation().getConversationId();
            ui.name = info.getConversation().getConversationId();
            try {
                ui.isTop = info.isTop();
            } catch (Exception e) {
                // fallback: try getter name getIsTop
                try { ui.isTop = info.isTop(); } catch (Exception ex) { ui.isTop = false; }
            }

            try {
                ui.topTime = info.getTopTime();
            } catch (Exception e) {
                try { ui.topTime = info.getTopTime(); } catch (Exception ex) { ui.topTime = 0L; }
            }
        }
        return ui;
    }

    public ConversationInfo getConversationInfo() {
        return conversationInfo;
    }
}
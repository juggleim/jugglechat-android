package com.juggle.im.android.chat;

import com.juggle.im.model.Conversation;

public class SearchResult {
    private String name;
    private String avatar;
    private String type;
    private String description;
    private String id;

    private Conversation conversation;


    /**
     * 搜索结果
     * @param id  搜索结果id
     * @param name 搜索结果名称
     * @param avatar 搜索结果头像
     * @param type 搜索结果类型
     * @param description 搜索结果描述
     */
    public SearchResult(String id, String name, String avatar, String type, String description) {
        this.name = name;
        this.avatar = avatar;
        this.type = type;
        this.description = description;
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public String getAvatar() {
        return avatar;
    }

    public String getType() {
        return type;
    }

    public String getDescription() {
        return description;
    }

    public String getId() {
        return id;
    }

    public Conversation getConversation() {
        return conversation;
    }

    public void setConversation(Conversation conversation) {
        this.conversation = conversation;
    }
}
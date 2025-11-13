package com.juggle.im.android.chat;

public class SearchResult {
    private String name;
    private String avatar;
    private String type;
    private String description;

    public SearchResult(String name, String avatar, String type) {
        this.name = name;
        this.avatar = avatar;
        this.type = type;
    }

    public SearchResult(String name, String avatar, String type, String description) {
        this.name = name;
        this.avatar = avatar;
        this.type = type;
        this.description = description;
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
}
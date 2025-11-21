package com.juggle.im.android.chat.mention;

public class MentionModel {
    public String userId;
    public String displayName;
    public int start;
    public int end;

    public MentionModel(String userId, String displayName, int start, int end) {
        this.userId = userId;
        this.displayName = displayName;
        this.start = start;
        this.end = end;
    }

    public String getUserId() {
        return userId;
    }

    public String getDisplayName() {
        return displayName;
    }
}

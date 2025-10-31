package com.juggle.im.android.event;

public class UnreadMessageCountEvent {
    private int totalCount;

    public UnreadMessageCountEvent(int totalCount) {
        this.totalCount = totalCount;
    }

    public int getTotalCount() {
        return totalCount;
    }
}

package com.juggle.im.android.server.beans;

import java.util.List;

/**
 * Data wrapper for block users list API.
 */
public class BlockUsersData {
    private List<FriendBean> items;
    private String offset;

    public List<FriendBean> getItems() {
        return items;
    }

    public void setItems(List<FriendBean> items) {
        this.items = items;
    }

    public String getOffset() {
        return offset;
    }

    public void setOffset(String offset) {
        this.offset = offset;
    }
}

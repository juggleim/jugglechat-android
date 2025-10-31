package com.juggle.im.android.server.beans;

import java.util.List;

/**
 * Data wrapper for friends list API: { items: [FriendBean, ...] }
 */
public class FriendsListData {
    private java.util.List<FriendBean> items;

    public java.util.List<FriendBean> getItems() {
        return items;
    }

    public void setItems(java.util.List<FriendBean> items) {
        this.items = items;
    }
}

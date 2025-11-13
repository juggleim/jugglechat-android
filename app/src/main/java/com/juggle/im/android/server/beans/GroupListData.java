package com.juggle.im.android.server.beans;

import java.util.List;

/**
 * Data wrapper for friends list API: { items: [FriendBean, ...] }
 */
public class GroupListData {
    private List<GroupBean> items;

    public List<GroupBean> getItems() {
        return items;
    }

    public void setItems(List<GroupBean> items) {
        this.items = items;
    }
}

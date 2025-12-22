package com.juggle.im.android.server.beans;

import java.util.List;

public class FriendApplicationsData {
    private List<FriendApplicationBean> items;

    public List<FriendApplicationBean> getItems() {
        return items;
    }

    public void setItems(List<FriendApplicationBean> items) {
        this.items = items;
    }
}

package com.juggle.im.android.server.beans;

import com.google.gson.annotations.SerializedName;

import java.util.List;

public class GroupMembersData {
    @SerializedName("items")
    private List<GroupMemberBean> items;
    @SerializedName("offset")
    private String offset;

    public List<GroupMemberBean> getItems() {
        return items;
    }

    public void setItems(List<GroupMemberBean> items) {
        this.items = items;
    }

    public String getOffset() {
        return offset;
    }

    public void setOffset(String offset) {
        this.offset = offset;
    }
}

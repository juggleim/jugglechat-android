package com.juggle.im.android.server.beans;

public class ReactionItem {
    private String value;
    private UserInfoBean user_info;

    public String getValue() { return value; }
    public void setValue(String value) { this.value = value; }
    public UserInfoBean getUser_info() { return user_info; }
    public void setUser_info(UserInfoBean user_info) { this.user_info = user_info; }
}

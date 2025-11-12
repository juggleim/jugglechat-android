package com.juggle.im.android.server.beans;

import com.google.gson.annotations.SerializedName;

import java.util.Objects;

public class GroupMemberBean {
    @SerializedName("user_id")
    private String userId;
    private String nickname;
    private String avatar;
    @SerializedName("member_type")
    private int memberType;
    private int role;

    public int getRole() {
        return role;
    }

    public void setRole(int role) {
        this.role = role;
    }

    public int getMemberType() {
        return memberType;
    }

    public void setMemberType(int memberType) {
        this.memberType = memberType;
    }

    public String getAvatar() {
        return avatar;
    }

    public void setAvatar(String avatar) {
        this.avatar = avatar;
    }

    public String getNickname() {
        return nickname;
    }

    public void setNickname(String nickname) {
        this.nickname = nickname;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof GroupMemberBean)) return false;
        GroupMemberBean that = (GroupMemberBean) o;
        return Objects.equals(userId, that.userId);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(userId);
    }
}

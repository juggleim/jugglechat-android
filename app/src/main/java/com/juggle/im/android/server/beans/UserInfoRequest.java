package com.juggle.im.android.server.beans;

import com.google.gson.annotations.SerializedName;

public class UserInfoRequest {
    @SerializedName("user_id")
    private String userId;
    private String nickname;
    private String avatar;

    public String getAvatar() {
        return avatar;
    }

    public void setAvatar(String avatar) {
        String normalized = normalize(avatar);
        this.avatar = normalized;
    }

    /**
     * 头像更新协议约束：当前仅接受 http/https URL。
     */
    public boolean hasValidAvatarProtocol() {
        if (avatar == null || avatar.isEmpty()) {
            return true;
        }
        return avatar.startsWith("http://") || avatar.startsWith("https://");
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
        this.userId = normalize(userId);
    }

    private String normalize(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}

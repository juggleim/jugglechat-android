package com.juggle.im.android.event;


import com.juggle.im.model.Message;
import com.juggle.im.model.UserInfo;

public class MessageTopEvent {
    private final Message message;
    private final UserInfo userInfo;
    private final boolean isTop;


    public MessageTopEvent(Message message, UserInfo userInfo, boolean isTop) {
        this.message = message;
        this.userInfo = userInfo;
        this.isTop = isTop;
    }

    public boolean isTop() {
        return isTop;
    }

    public UserInfo getUserInfo() {
        return userInfo;
    }

    public Message getMessage() {
        return message;
    }
}

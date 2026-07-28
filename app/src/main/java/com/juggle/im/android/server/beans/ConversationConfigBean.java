package com.juggle.im.android.server.beans;

import com.google.gson.annotations.SerializedName;

/**
 * 会话级消息配置。
 */
public class ConversationConfigBean {
    @SerializedName("msg_life_time")
    private int messageLifeTimeDays;

    /**
     * 获取新消息自动删除周期。
     *
     * @return 自动删除天数，0 表示关闭
     */
    public int getMessageLifeTimeDays() {
        return messageLifeTimeDays;
    }

    /**
     * 设置新消息自动删除周期。
     *
     * @param messageLifeTimeDays 自动删除天数，0 表示关闭
     */
    public void setMessageLifeTimeDays(int messageLifeTimeDays) {
        this.messageLifeTimeDays = messageLifeTimeDays;
    }
}

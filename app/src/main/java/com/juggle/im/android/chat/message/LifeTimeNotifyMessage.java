package com.juggle.im.android.chat.message;

import android.util.Log;

import com.juggle.im.model.MessageContent;

import org.json.JSONException;
import org.json.JSONObject;

import java.nio.charset.StandardCharsets;

/**
 * 阅后即焚时效消息通知
 * 用于设置消息自动删除时间
 */
public class LifeTimeNotifyMessage extends MessageContent {
    public final static String ACTION = "jgd:lifetime_msg";

    private int mType = 0;

    public LifeTimeNotifyMessage() {
        mContentType = ACTION;
    }

    @Override
    public int getFlags() {
        return MessageContent.MessageFlag.IS_SAVE.getValue();
    }

    @Override
    public byte[] encode() {
        JSONObject jsonObject = new JSONObject();
        try {
            jsonObject.put(TYPE, mType);
        } catch (JSONException e) {
            Log.e("LifeTimeNotifyMessage", "encode JSONException " + e.getMessage());
        }
        return jsonObject.toString().getBytes(StandardCharsets.UTF_8);
    }

    @Override
    public void decode(byte[] data) {
        if (data == null) {
            Log.e("LifeTimeNotifyMessage", "decode data is null");
            return;
        }
        String jsonStr = new String(data, StandardCharsets.UTF_8);
        try {
            JSONObject jsonObject = new JSONObject(jsonStr);
            mType = jsonObject.optInt(TYPE, 0);
        } catch (JSONException e) {
            Log.e("LifeTimeNotifyMessage", "decode JSONException " + e.getMessage());
        }
    }

    @Override
    public String conversationDigest() {
        return "[消息时效设置]";
    }

    public int getType() {
        return mType;
    }

    public void setType(int type) {
        this.mType = type;
    }

    /**
     * 获取时效消息描述
     * @param senderName 发送者名称
     * @return 描述文本
     */
    public String description(String senderName) {
        if (mType == 0) {
            return senderName + " 关闭了消息自动删除";
        }
        return senderName + " 设置 " + mType + " 天后消息自动删除";
    }

    private static final String TYPE = "type";
}

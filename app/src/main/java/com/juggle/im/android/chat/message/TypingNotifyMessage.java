package com.juggle.im.android.chat.message;

import android.util.Log;

import com.juggle.im.model.MessageContent;

import org.json.JSONException;
import org.json.JSONObject;

import java.nio.charset.StandardCharsets;

/**
 * 输入状态通知消息
 * 用于显示对方正在输入的状态
 * 此消息为状态消息，不存储到本地数据库
 */
public class TypingNotifyMessage extends MessageContent {
    public final static String ACTION = "snl:typing";

    public static final int TYPE_END = 0;
    public static final int TYPE_START = 1;

    private int mType = TYPE_END;

    public TypingNotifyMessage() {
        mContentType = ACTION;
    }

    @Override
    public int getFlags() {
        // 状态消息，不存储
        return MessageContent.MessageFlag.IS_STATUS.getValue();
    }

    @Override
    public byte[] encode() {
        JSONObject jsonObject = new JSONObject();
        try {
            jsonObject.put(TYPE, mType);
        } catch (JSONException e) {
            Log.e("TypingNotifyMessage", "encode JSONException " + e.getMessage());
        }
        return jsonObject.toString().getBytes(StandardCharsets.UTF_8);
    }

    @Override
    public void decode(byte[] data) {
        if (data == null) {
            Log.e("TypingNotifyMessage", "decode data is null");
            return;
        }
        String jsonStr = new String(data, StandardCharsets.UTF_8);
        try {
            JSONObject jsonObject = new JSONObject(jsonStr);
            mType = jsonObject.optInt(TYPE, TYPE_END);
        } catch (JSONException e) {
            Log.e("TypingNotifyMessage", "decode JSONException " + e.getMessage());
        }
    }

    @Override
    public String conversationDigest() {
        return "";
    }

    public int getType() {
        return mType;
    }

    public void setType(int type) {
        this.mType = type;
    }

    /**
     * 是否正在输入
     */
    public boolean isTyping() {
        return mType == TYPE_START;
    }

    private static final String TYPE = "type";
}

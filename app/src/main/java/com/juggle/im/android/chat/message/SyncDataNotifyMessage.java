package com.juggle.im.android.chat.message;

import android.util.Log;

import com.juggle.im.model.MessageContent;

import org.json.JSONException;
import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import com.juggle.im.android.i18n.AppRes;
import com.juggle.im.android.R;

/**
 * 数据同步通知消息
 * 用于通知客户端同步数据（如好友备注同步）
 */
public class SyncDataNotifyMessage extends MessageContent {
    public final static String ACTION = "snl:syncdntf";

    public static final int TYPE_NONE = 0;
    public static final int TYPE_FRIEND_ALIAS = 1;

    private int mType = TYPE_NONE;
    private String mContent = "";

    public SyncDataNotifyMessage() {
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
            jsonObject.put(CONTENT, mContent);
        } catch (JSONException e) {
            Log.e("SyncDataNotifyMessage", "encode JSONException " + e.getMessage());
        }
        return jsonObject.toString().getBytes(StandardCharsets.UTF_8);
    }

    @Override
    public void decode(byte[] data) {
        if (data == null) {
            Log.e("SyncDataNotifyMessage", "decode data is null");
            return;
        }
        String jsonStr = new String(data, StandardCharsets.UTF_8);
        try {
            JSONObject jsonObject = new JSONObject(jsonStr);
            mType = jsonObject.optInt(TYPE, TYPE_NONE);
            mContent = jsonObject.optString(CONTENT, "");
        } catch (JSONException e) {
            Log.e("SyncDataNotifyMessage", "decode JSONException " + e.getMessage());
        }
    }

    @Override
    public String conversationDigest() {
        return AppRes.string(R.string.msg_sync_data_notify);
    }

    public int getType() {
        return mType;
    }

    public void setType(int type) {
        this.mType = type;
    }

    public String getContent() {
        return mContent;
    }

    public void setContent(String content) {
        this.mContent = content;
    }

    private static final String TYPE = "type";
    private static final String CONTENT = "content";
}

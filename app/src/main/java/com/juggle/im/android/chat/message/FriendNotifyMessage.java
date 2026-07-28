package com.juggle.im.android.chat.message;

import android.util.Log;

import com.juggle.im.model.MessageContent;

import org.json.JSONException;
import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import com.juggle.im.android.i18n.AppRes;
import com.juggle.im.android.R;

public class FriendNotifyMessage extends MessageContent {
    public final static String ACTION = "jgd:friendntf";

    public FriendNotifyMessage() {
        mContentType = ACTION;
    }

    @Override
    public byte[] encode() {
        JSONObject jsonObject = new JSONObject();
        try {
            jsonObject.put(TYPE, mType);
        } catch (JSONException e) {
            Log.e("FriendNotifyMessage", "encode JSONException " + e.getMessage());
        }

        return jsonObject.toString().getBytes(StandardCharsets.UTF_8);
    }

    @Override
    public void decode(byte[] data) {
        if (data == null) {
            Log.e("FriendNotifyMessage", "decode data is null");
            return;
        }
        String jsonStr = new String(data, StandardCharsets.UTF_8);

        try {
            JSONObject jsonObject = new JSONObject(jsonStr);
            if (jsonObject.has(TYPE)) {
                mType = jsonObject.optInt(TYPE);
            }
        } catch (JSONException e) {
            Log.e("FriendNotifyMessage", "decode JSONException " + e.getMessage());
        }
    }

    @Override
    public int getFlags() {
        return MessageFlag.IS_SAVE.getValue();
    }

    @Override
    public String conversationDigest() {
        return AppRes.string(R.string.msg_friend_notify);
    }

    public int getType() {
        return mType;
    }

    public void setType(int type) {
        this.mType = type;
    }

    /**
     * 好友通知描述文案。
     *
     * @param senderName 触发通知的用户名
     * @return 完整描述文案
     */
    public String description(String senderName) {
        return AppRes.string(mType == 0
                ? R.string.friend_notify_added : R.string.friend_notify_accepted, senderName);
    }

    private int mType = 0;
    private static final String TYPE = "type";
}

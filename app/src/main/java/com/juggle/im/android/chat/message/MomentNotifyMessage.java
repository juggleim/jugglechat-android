package com.juggle.im.android.chat.message;

import android.util.Log;

import com.juggle.im.model.MessageContent;

import org.json.JSONException;
import org.json.JSONObject;

import java.nio.charset.StandardCharsets;

/**
 * 朋友圈通知消息
 * 用于朋友圈相关通知（点赞、评论等）
 */
public class MomentNotifyMessage extends MessageContent {
    public final static String ACTION = "jgd:postnotify";

    private int mType = 0;
    private String mSponsorId = "";

    public MomentNotifyMessage() {
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
            jsonObject.put(POST_BUS_TYPE, mType);
            jsonObject.put(SPONSOR_ID, mSponsorId);
        } catch (JSONException e) {
            Log.e("MomentNotifyMessage", "encode JSONException " + e.getMessage());
        }
        return jsonObject.toString().getBytes(StandardCharsets.UTF_8);
    }

    @Override
    public void decode(byte[] data) {
        if (data == null) {
            Log.e("MomentNotifyMessage", "decode data is null");
            return;
        }
        String jsonStr = new String(data, StandardCharsets.UTF_8);
        try {
            JSONObject jsonObject = new JSONObject(jsonStr);
            mType = jsonObject.optInt(POST_BUS_TYPE, 0);
            mSponsorId = jsonObject.optString(SPONSOR_ID, "");
        } catch (JSONException e) {
            Log.e("MomentNotifyMessage", "decode JSONException " + e.getMessage());
        }
    }

    @Override
    public String conversationDigest() {
        return "[朋友圈通知]";
    }

    public int getType() {
        return mType;
    }

    public void setType(int type) {
        this.mType = type;
    }

    public String getSponsorId() {
        return mSponsorId;
    }

    public void setSponsorId(String sponsorId) {
        this.mSponsorId = sponsorId;
    }

    private static final String POST_BUS_TYPE = "post_bus_type";
    private static final String SPONSOR_ID = "sponsor_id";
}

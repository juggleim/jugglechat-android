package com.juggle.im.android.chat.message;

import android.util.Log;

import com.juggle.im.model.MessageContent;

import org.json.JSONException;
import org.json.JSONObject;

import java.nio.charset.StandardCharsets;

/**
 * 表情贴纸消息
 * 用于发送表情贴纸
 */
public class StickerEmojiMessage extends MessageContent {
    public final static String ACTION = "snl:sticker";

    private String mPath = "";
    private String mName = "";

    public StickerEmojiMessage() {
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
            jsonObject.put(PATH, mPath);
            jsonObject.put(NAME, mName);
        } catch (JSONException e) {
            Log.e("StickerEmojiMessage", "encode JSONException " + e.getMessage());
        }
        return jsonObject.toString().getBytes(StandardCharsets.UTF_8);
    }

    @Override
    public void decode(byte[] data) {
        if (data == null) {
            Log.e("StickerEmojiMessage", "decode data is null");
            return;
        }
        String jsonStr = new String(data, StandardCharsets.UTF_8);
        if (jsonStr.isEmpty()) {
            return;
        }
        try {
            JSONObject jsonObject = new JSONObject(jsonStr);
            mPath = jsonObject.optString(PATH, "");
            mName = jsonObject.optString(NAME, "");
        } catch (JSONException e) {
            Log.e("StickerEmojiMessage", "decode JSONException " + e.getMessage());
        }
    }

    @Override
    public String conversationDigest() {
        return "[表情]";
    }

    public String getPath() {
        return mPath;
    }

    public void setPath(String path) {
        this.mPath = path;
    }

    public String getName() {
        return mName;
    }

    public void setName(String name) {
        this.mName = name;
    }

    private static final String PATH = "path";
    private static final String NAME = "name";
}

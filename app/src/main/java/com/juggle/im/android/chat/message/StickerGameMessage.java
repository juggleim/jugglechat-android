package com.juggle.im.android.chat.message;

import android.util.Log;

import com.juggle.im.model.MessageContent;

import org.json.JSONException;
import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import com.juggle.im.android.i18n.AppRes;
import com.juggle.im.android.R;

/**
 * 骰子/猜拳游戏消息
 * 用于发送骰子或猜拳游戏表情
 */
public class StickerGameMessage extends MessageContent {
    public final static String ACTION = "jgd:sticker_game";

    public static final String TYPE_DICE = "dice";
    public static final String TYPE_MORA = "mora";

    private String mName = "";
    private String mType = "";

    public StickerGameMessage() {
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
            jsonObject.put(NAME, mName);
            jsonObject.put(TYPE, mType);
        } catch (JSONException e) {
            Log.e("StickerGameMessage", "encode JSONException " + e.getMessage());
        }
        return jsonObject.toString().getBytes(StandardCharsets.UTF_8);
    }

    @Override
    public void decode(byte[] data) {
        if (data == null) {
            Log.e("StickerGameMessage", "decode data is null");
            return;
        }
        String jsonStr = new String(data, StandardCharsets.UTF_8);
        if (jsonStr.isEmpty()) {
            return;
        }
        try {
            JSONObject jsonObject = new JSONObject(jsonStr);
            mName = jsonObject.optString(NAME, "");
            mType = jsonObject.optString(TYPE, "");
        } catch (JSONException e) {
            Log.e("StickerGameMessage", "decode JSONException " + e.getMessage());
        }
    }

    @Override
    public String conversationDigest() {
        if (TYPE_DICE.equals(mType)) {
            return AppRes.string(R.string.msg_sticker_game_dice);
        } else if (TYPE_MORA.equals(mType)) {
            return AppRes.string(R.string.msg_sticker_game_mora);
        }
        return AppRes.string(R.string.msg_sticker_game);
    }

    public String getName() {
        return mName;
    }

    public void setName(String name) {
        this.mName = name;
    }

    public String getGameType() {
        return mType;
    }

    public void setGameType(String type) {
        this.mType = type;
    }

    /**
     * 是否是骰子游戏
     */
    public boolean isDice() {
        return TYPE_DICE.equals(mType);
    }

    /**
     * 是否是猜拳游戏
     */
    public boolean isMora() {
        return TYPE_MORA.equals(mType);
    }

    private static final String NAME = "name";
    private static final String TYPE = "type";
}

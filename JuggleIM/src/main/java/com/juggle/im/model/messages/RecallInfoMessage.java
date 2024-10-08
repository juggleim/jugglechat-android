package com.juggle.im.model.messages;

import com.juggle.im.internal.util.JLogger;
import com.juggle.im.model.MessageContent;

import org.json.JSONException;
import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

public class RecallInfoMessage extends MessageContent {
    public RecallInfoMessage() {
        mContentType = CONTENT_TYPE;
    }

    @Override
    public byte[] encode() {
        JSONObject jsonObject = new JSONObject();
        try {
            if (mExtra != null) {
                JSONObject extObject = new JSONObject();
                for (Map.Entry<String, String> entry : mExtra.entrySet()) {
                    extObject.put(entry.getKey(), entry.getValue());
                }
                jsonObject.put(EXTRA, extObject);
            }
        } catch (JSONException e) {
            JLogger.e("MSG-Encode", "RecallInfoMessage JSONException " + e.getMessage());
        }
        return jsonObject.toString().getBytes(StandardCharsets.UTF_8);
    }

    @Override
    public void decode(byte[] data) {
        if (data == null) {
            JLogger.e("MSG-Decode", "RecallInfoMessage decode data is null");
            return;
        }
        String jsonStr = new String(data, StandardCharsets.UTF_8);
        try {
            JSONObject jsonObject = new JSONObject(jsonStr);
            if (jsonObject.has(EXTRA)) {
                decodeExt(jsonObject.optJSONObject(EXTRA));
            }

        } catch (JSONException e) {
            JLogger.e("MSG-Decode", "RecallInfoMessage decode JSONException " + e.getMessage());
        }
    }

    private void decodeExt(JSONObject jsonObject) {
        if (jsonObject == null) return;

        mExtra = new HashMap<>();
        for (Iterator<String> it = jsonObject.keys(); it.hasNext(); ) {
            try {
                String key = it.next();
                String value = jsonObject.getString(key);
                mExtra.put(key, value);
            } catch (JSONException e) {
                JLogger.e("MSG-Decode", "RecallInfoMessage decodeExt JSONException " + e.getMessage());
            }
        }
    }

    public Map<String, String> getExtra() {
        return mExtra;
    }

    public void setExtra(Map<String, String> extra) {
        mExtra = extra;
    }

    public static final String CONTENT_TYPE = "jg:recallinfo";

    private Map<String, String> mExtra;

    private static final String EXTRA = "exts";

}

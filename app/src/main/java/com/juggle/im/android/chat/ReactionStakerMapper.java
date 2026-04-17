package com.juggle.im.android.chat;

import android.text.TextUtils;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * 消息 Reaction 映射工具。
 * <p>
 * 统一维护与 snailchat 一致的 stakerReactions（reactionId ↔ emoji）映射，
 * 同时兼容 Android 历史版本使用的旧 reactionId（如 :ok_hand）。
 */
public final class ReactionStakerMapper {
    public static final String REACTION_ID_OK_HAND = "1f44c";
    public static final String REACTION_ID_THUMB_UP = "1f44d";
    public static final String REACTION_ID_SMILING_FACE_WITH_HEARTS = "1f970";
    public static final String REACTION_ID_SALUTE = "1fae1";
    public static final String REACTION_ID_HEART = "1f276";
    public static final String REACTION_ID_BROKEN_HEART = "1f494";
    public static final String REACTION_ID_POOP = "1f4a9";
    public static final String REACTION_ID_PARTY = "1f389";

    private static final Map<String, String> ID_TO_EMOJI;
    private static final Map<String, String> EMOJI_TO_ID;
    private static final Map<String, String> LEGACY_ID_TO_CANONICAL_ID;

    static {
        Map<String, String> idToEmoji = new HashMap<>();
        idToEmoji.put(REACTION_ID_OK_HAND, "👌");
        idToEmoji.put(REACTION_ID_THUMB_UP, "👍");
        idToEmoji.put(REACTION_ID_SMILING_FACE_WITH_HEARTS, "🥰");
        idToEmoji.put(REACTION_ID_SALUTE, "🫡");
        idToEmoji.put(REACTION_ID_HEART, "❤️");
        idToEmoji.put(REACTION_ID_BROKEN_HEART, "💔");
        idToEmoji.put(REACTION_ID_POOP, "💩");
        idToEmoji.put(REACTION_ID_PARTY, "🎉");
        ID_TO_EMOJI = Collections.unmodifiableMap(idToEmoji);

        Map<String, String> emojiToId = new HashMap<>();
        for (Map.Entry<String, String> entry : idToEmoji.entrySet()) {
            emojiToId.put(entry.getValue(), entry.getKey());
        }
        EMOJI_TO_ID = Collections.unmodifiableMap(emojiToId);

        Map<String, String> legacyToCanonical = new HashMap<>();
        legacyToCanonical.put(":ok_hand", REACTION_ID_OK_HAND);
        legacyToCanonical.put(":thumb_up", REACTION_ID_THUMB_UP);
        legacyToCanonical.put(":heart_eyes", REACTION_ID_SMILING_FACE_WITH_HEARTS);
        legacyToCanonical.put(":salute", REACTION_ID_SALUTE);
        legacyToCanonical.put(":heart", REACTION_ID_HEART);
        legacyToCanonical.put(":broken_heart", REACTION_ID_BROKEN_HEART);
        legacyToCanonical.put(":poop", REACTION_ID_POOP);
        legacyToCanonical.put(":tada", REACTION_ID_PARTY);
        LEGACY_ID_TO_CANONICAL_ID = Collections.unmodifiableMap(legacyToCanonical);
    }

    private ReactionStakerMapper() {
    }

    /**
     * 将输入值归一化成 stakerReactionId。
     *
     * @param value 输入值（可能是 emoji、stakerReactionId 或历史旧 ID）
     * @return 归一化后的 stakerReactionId，无法识别时返回原值（去空白后）
     */
    public static String toCanonicalReactionId(String value) {
        if (TextUtils.isEmpty(value)) {
            return "";
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            return "";
        }
        if (ID_TO_EMOJI.containsKey(trimmed)) {
            return trimmed;
        }
        String fromEmoji = EMOJI_TO_ID.get(trimmed);
        if (!TextUtils.isEmpty(fromEmoji)) {
            return fromEmoji;
        }
        String fromLegacy = LEGACY_ID_TO_CANONICAL_ID.get(trimmed);
        if (!TextUtils.isEmpty(fromLegacy)) {
            return fromLegacy;
        }
        return trimmed;
    }

    /**
     * 将 reactionId 映射为可展示 emoji。
     *
     * @param reactionId reactionId
     * @return emoji（未命中映射时返回原值，避免丢信息）
     */
    public static String toEmoji(String reactionId) {
        if (TextUtils.isEmpty(reactionId)) {
            return "";
        }
        String canonicalId = toCanonicalReactionId(reactionId);
        String emoji = ID_TO_EMOJI.get(canonicalId);
        return TextUtils.isEmpty(emoji) ? reactionId : emoji;
    }
}

package com.juggle.im.android.i18n;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

import com.juggle.im.android.R;

/**
 * 应用支持的界面语言。
 *
 * <p>{@link #FOLLOW_SYSTEM} 表示跟随系统语言（默认值），其余为用户显式指定的语言。</p>
 */
public enum AppLanguage {

    /**
     * 跟随系统语言
     */
    FOLLOW_SYSTEM("", R.string.language_follow_system),

    /**
     * 简体中文
     */
    SIMPLIFIED_CHINESE("zh-CN", R.string.language_simplified_chinese),

    /**
     * 英文
     */
    ENGLISH("en", R.string.language_english);

    private final String tag;
    @StringRes
    private final int displayNameRes;

    AppLanguage(@NonNull String tag, @StringRes int displayNameRes) {
        this.tag = tag;
        this.displayNameRes = displayNameRes;
    }

    /**
     * BCP 47 语言标签，跟随系统时为空串。
     */
    @NonNull
    public String getTag() {
        return tag;
    }

    /**
     * 语言在设置页的展示名资源 id。
     */
    @StringRes
    public int getDisplayNameRes() {
        return displayNameRes;
    }

    /**
     * 由持久化的语言标签还原枚举，无法识别时回落到跟随系统。
     *
     * @param tag 语言标签
     * @return 对应的语言枚举
     */
    @NonNull
    public static AppLanguage fromTag(@Nullable String tag) {
        if (tag == null || tag.isEmpty()) {
            return FOLLOW_SYSTEM;
        }
        for (AppLanguage language : values()) {
            if (language.tag.equalsIgnoreCase(tag)) {
                return language;
            }
        }
        return FOLLOW_SYSTEM;
    }
}

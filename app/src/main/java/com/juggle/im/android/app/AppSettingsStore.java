package com.juggle.im.android.app;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;

import com.juggle.im.android.R;

/**
 * “通用设置”本地配置存储。
 */
public final class AppSettingsStore {
    private static final String PREF_NAME = "my_settings";
    private static final String KEY_APP_NOTIFY = "general_app_notify";
    private static final String KEY_CHAT_BG_INDEX = "chat_background_index";

    /**
     * 聊天背景候选项，与 iOS ChatBackgroundStore.BackgroundOption 一一对应：
     * 首项为「无背景」（0 表示不铺图，仅用纯色底），其后为 8 张与 iOS 同源的背景图。
     */
    private static final int[] CHAT_BACKGROUNDS = new int[] {
            0,
            R.drawable.chat_background_1,
            R.drawable.chat_background_2,
            R.drawable.chat_background_3,
            R.drawable.chat_background_4,
            R.drawable.chat_background_5,
            R.drawable.chat_background_6,
            R.drawable.chat_background_7,
            R.drawable.chat_background_8
    };

    private AppSettingsStore() {
    }

    public static boolean isAppNotifyEnabled(@NonNull Context context) {
        return prefs(context).getBoolean(KEY_APP_NOTIFY, true);
    }

    public static void setAppNotifyEnabled(@NonNull Context context, boolean enabled) {
        prefs(context).edit().putBoolean(KEY_APP_NOTIFY, enabled).apply();
    }

    public static int[] getChatBackgrounds() {
        return CHAT_BACKGROUNDS;
    }

    public static int getChatBackgroundIndex(@NonNull Context context) {
        int index = prefs(context).getInt(KEY_CHAT_BG_INDEX, 0);
        if (index < 0 || index >= CHAT_BACKGROUNDS.length) {
            return 0;
        }
        return index;
    }

    public static void setChatBackgroundIndex(@NonNull Context context, int index) {
        int safeIndex = index;
        if (safeIndex < 0 || safeIndex >= CHAT_BACKGROUNDS.length) {
            safeIndex = 0;
        }
        prefs(context).edit().putInt(KEY_CHAT_BG_INDEX, safeIndex).apply();
    }

    /**
     * 获取当前选中的聊天背景图资源。
     *
     * @param context 上下文
     * @return 背景图资源 id；选择「无背景」时返回 0
     */
    public static int getChatBackgroundRes(@NonNull Context context) {
        return CHAT_BACKGROUNDS[getChatBackgroundIndex(context)];
    }

    /**
     * 是否设置过聊天背景图。
     *
     * @param context 上下文
     * @return true 表示选择了背景图而非「无背景」
     */
    public static boolean hasChatBackground(@NonNull Context context) {
        return getChatBackgroundRes(context) != 0;
    }

    private static SharedPreferences prefs(@NonNull Context context) {
        return context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }
}

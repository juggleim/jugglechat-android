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

    private static final int[] CHAT_BACKGROUNDS = new int[] {
            R.drawable.bg_chat_preview_1,
            R.drawable.bg_chat_preview_2,
            R.drawable.bg_chat_preview_3,
            R.drawable.bg_chat_preview_4,
            R.drawable.bg_chat_preview_5,
            R.drawable.bg_chat_preview_6,
            R.drawable.bg_chat_preview_7,
            R.drawable.bg_chat_preview_8
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

    public static int getChatBackgroundRes(@NonNull Context context) {
        return CHAT_BACKGROUNDS[getChatBackgroundIndex(context)];
    }

    private static SharedPreferences prefs(@NonNull Context context) {
        return context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }
}

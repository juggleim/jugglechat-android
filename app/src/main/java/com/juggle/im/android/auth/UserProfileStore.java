package com.juggle.im.android.auth;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;

/**
 * 登录用户资料本地缓存，用于冷启动/断网场景下的 UI 展示。
 */
public final class UserProfileStore {
    private static final String PREF_NAME = "login_user_profile";
    private static final String KEY_USER_ID = "user_id";
    private static final String KEY_NICKNAME = "nickname";
    private static final String KEY_AVATAR = "avatar";

    private UserProfileStore() {
    }

    public static void save(@NonNull Context context,
                            String userId,
                            String nickname,
                            String avatar) {
        SharedPreferences.Editor editor = prefs(context).edit();
        editor.putString(KEY_USER_ID, trimToEmpty(userId));
        editor.putString(KEY_NICKNAME, trimToEmpty(nickname));
        editor.putString(KEY_AVATAR, trimToEmpty(avatar));
        editor.apply();
    }

    @NonNull
    public static UserProfile read(@NonNull Context context) {
        SharedPreferences preferences = prefs(context);
        return new UserProfile(
                trimToEmpty(preferences.getString(KEY_USER_ID, "")),
                trimToEmpty(preferences.getString(KEY_NICKNAME, "")),
                trimToEmpty(preferences.getString(KEY_AVATAR, ""))
        );
    }

    public static void clear(@NonNull Context context) {
        prefs(context).edit()
                .remove(KEY_USER_ID)
                .remove(KEY_NICKNAME)
                .remove(KEY_AVATAR)
                .apply();
    }

    @NonNull
    private static SharedPreferences prefs(@NonNull Context context) {
        return context.getApplicationContext().getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    @NonNull
    private static String trimToEmpty(String value) {
        return value == null ? "" : value.trim();
    }

    public static final class UserProfile {
        private final String userId;
        private final String nickname;
        private final String avatar;

        public UserProfile(@NonNull String userId, @NonNull String nickname, @NonNull String avatar) {
            this.userId = userId;
            this.nickname = nickname;
            this.avatar = avatar;
        }

        @NonNull
        public String getUserId() {
            return userId;
        }

        @NonNull
        public String getNickname() {
            return nickname;
        }

        @NonNull
        public String getAvatar() {
            return avatar;
        }

        public boolean isEmpty() {
            return userId.isEmpty() && nickname.isEmpty() && avatar.isEmpty();
        }
    }
}

package com.juggle.im.android.auth;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.security.crypto.EncryptedSharedPreferences;
import androidx.security.crypto.MasterKeys;

import java.io.IOException;
import java.security.GeneralSecurityException;

/**
 * 会话存储实现：
 * - securePrefs：新版本加密存储（主存）
 * - legacyPrefs：旧版明文存储（仅迁移读取）
 */
public final class SessionStorage implements SessionRepository.Storage {
    private static final String TAG = "SessionStorage";

    private static final String LEGACY_PREFS_NAME = "login_prefs";
    private static final String SECURE_PREFS_NAME = "secure_login_prefs";

    private static final String KEY_APP_TOKEN = "app_token";
    private static final String KEY_IM_TOKEN = "im_token";
    private static final String KEY_EXPIRE_TIME = "expire_time";

    private final SharedPreferences securePrefs;
    private final SharedPreferences legacyPrefs;

    public SessionStorage(@NonNull Context context) {
        Context appContext = context.getApplicationContext();
        this.securePrefs = createSecurePreferences(appContext);
        this.legacyPrefs = appContext.getSharedPreferences(LEGACY_PREFS_NAME, Context.MODE_PRIVATE);
    }

    @Nullable
    @Override
    public SessionRepository.SessionState readSecureSession() {
        return readFrom(securePrefs);
    }

    @Nullable
    @Override
    public SessionRepository.SessionState readLegacySession() {
        return readFrom(legacyPrefs);
    }

    @Override
    public void writeSecureSession(@NonNull SessionRepository.SessionState sessionState) {
        securePrefs.edit()
                .putString(KEY_APP_TOKEN, sessionState.getAppToken())
                .putString(KEY_IM_TOKEN, sessionState.getImToken())
                .putLong(KEY_EXPIRE_TIME, sessionState.getExpireAtMillis())
                .apply();
    }

    @Override
    public void clearSecureSession() {
        clearSessionFields(securePrefs);
    }

    @Override
    public void clearLegacySession() {
        clearSessionFields(legacyPrefs);
    }

    @Nullable
    private SessionRepository.SessionState readFrom(@NonNull SharedPreferences preferences) {
        String appToken = safeTrim(preferences.getString(KEY_APP_TOKEN, null));
        String imToken = safeTrim(preferences.getString(KEY_IM_TOKEN, null));
        long expireAt = preferences.getLong(KEY_EXPIRE_TIME, 0L);

        if (appToken.isEmpty() && imToken.isEmpty() && expireAt <= 0L) {
            return null;
        }
        return new SessionRepository.SessionState(appToken, imToken, expireAt);
    }

    private void clearSessionFields(@NonNull SharedPreferences preferences) {
        preferences.edit()
                .remove(KEY_APP_TOKEN)
                .remove(KEY_IM_TOKEN)
                .remove(KEY_EXPIRE_TIME)
                .apply();
    }

    private SharedPreferences createSecurePreferences(@NonNull Context context) {
        try {
            String masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC);
            return EncryptedSharedPreferences.create(
                    SECURE_PREFS_NAME,
                    masterKeyAlias,
                    context,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM);
        } catch (GeneralSecurityException | IOException e) {
            Log.w(TAG, "Encrypted storage unavailable, fallback to regular storage", e);
            return context.getSharedPreferences(SECURE_PREFS_NAME, Context.MODE_PRIVATE);
        }
    }

    @NonNull
    private String safeTrim(@Nullable String value) {
        return value == null ? "" : value.trim();
    }
}

package com.juggle.im.android.auth;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * 统一管理登录会话的读取、迁移和清理。
 * <p>
 * 规则：
 * 1. 优先读取加密存储；
 * 2. 若加密存储缺失，则尝试读取旧版 SharedPreferences 并迁移；
 * 3. 任何无效或过期会话都会被主动清理，避免脏数据反复命中。
 */
public final class SessionRepository {

    /**
     * 兼容旧逻辑的默认 token 有效期（2 天）。
     */
    public static final long DEFAULT_TOKEN_VALIDITY_DURATION_MILLIS = 2L * 24 * 60 * 60 * 1000;

    /**
     * 存储抽象，便于测试与替换具体存储实现。
     */
    public interface Storage {
        /**
         * 读取加密存储中的会话。
         */
        @Nullable
        SessionState readSecureSession();

        /**
         * 读取旧版明文存储中的会话。
         */
        @Nullable
        SessionState readLegacySession();

        /**
         * 写入加密存储。
         */
        void writeSecureSession(@NonNull SessionState sessionState);

        /**
         * 清空加密存储中的会话字段。
         */
        void clearSecureSession();

        /**
         * 清空旧版明文存储中的会话字段。
         */
        void clearLegacySession();
    }

    /**
     * 时间源抽象，便于单测精确控制过期判断。
     */
    interface Clock {
        long now();
    }

    /**
     * 会话值对象。
     */
    public static final class SessionState {
        private final String appToken;
        private final String imToken;
        private final long expireAtMillis;

        public SessionState(@Nullable String appToken, @Nullable String imToken, long expireAtMillis) {
            this.appToken = appToken == null ? "" : appToken.trim();
            this.imToken = imToken == null ? "" : imToken.trim();
            this.expireAtMillis = expireAtMillis;
        }

        @NonNull
        public String getAppToken() {
            return appToken;
        }

        @NonNull
        public String getImToken() {
            return imToken;
        }

        public long getExpireAtMillis() {
            return expireAtMillis;
        }
    }

    private final Storage storage;
    private final Clock clock;

    public SessionRepository(@NonNull Storage storage) {
        this(storage, System::currentTimeMillis);
    }

    SessionRepository(@NonNull Storage storage, @NonNull Clock clock) {
        this.storage = storage;
        this.clock = clock;
    }

    public static SessionRepository create(@NonNull Context context) {
        return new SessionRepository(new SessionStorage(context));
    }

    /**
     * 判断持久化存储中是否存在过会话数据，不校验令牌是否完整或过期。
     * <p>
     * TIPS：该结果必须在 {@link #getValidSession()} 之前读取，因为有效性检查会主动清理失效数据。
     *
     * @return true 表示存在待校验的会话数据；false 表示用户从未登录或已主动清理会话
     */
    public boolean hasStoredSession() {
        return storage.readSecureSession() != null || storage.readLegacySession() != null;
    }

    /**
     * 持久化当前会话。写入加密存储成功后会清理旧版明文字段。
     */
    public void saveSession(@Nullable String appToken, @Nullable String imToken, long expireAtMillis) {
        SessionState sessionState = new SessionState(appToken, imToken, expireAtMillis);
        validateSessionState(sessionState);
        storage.writeSecureSession(sessionState);
        storage.clearLegacySession();
    }

    /**
     * 读取可用会话。若仅命中旧字段，会完成一次“读时迁移”。
     */
    @Nullable
    public SessionState getValidSession() {
        SessionState secureSession = storage.readSecureSession();
        if (isSessionValid(secureSession)) {
            return secureSession;
        }
        if (secureSession != null) {
            storage.clearSecureSession();
        }

        SessionState legacySession = storage.readLegacySession();
        if (!isSessionValid(legacySession)) {
            if (legacySession != null) {
                storage.clearLegacySession();
            }
            return null;
        }

        // 复杂逻辑说明：旧字段只作为迁移输入，一旦命中立即写入加密存储并清除旧值。
        storage.writeSecureSession(legacySession);
        storage.clearLegacySession();
        return legacySession;
    }

    /**
     * 清理所有会话数据，通常在主动登出或鉴权失败后调用。
     */
    public void clearSession() {
        storage.clearSecureSession();
        storage.clearLegacySession();
    }

    private void validateSessionState(@NonNull SessionState sessionState) {
        if (!isSessionValid(sessionState)) {
            throw new IllegalArgumentException("Session state is invalid or expired");
        }
    }

    private boolean isSessionValid(@Nullable SessionState sessionState) {
        if (sessionState == null) {
            return false;
        }
        return !sessionState.getAppToken().isEmpty()
                && !sessionState.getImToken().isEmpty()
                && sessionState.getExpireAtMillis() > clock.now();
    }
}

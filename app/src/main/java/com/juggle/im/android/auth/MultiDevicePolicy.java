package com.juggle.im.android.auth;

/**
 * 多端登录冲突策略。
 * <p>
 * 当前策略：当连接状态码为 11011 时，判定为账号在其他设备登录，当前端执行强制登出。
 */
public final class MultiDevicePolicy {
    public static final int CODE_REMOTE_LOGIN_CONFLICT = 11011;

    private MultiDevicePolicy() {
    }

    public static boolean shouldForceLogout(int connectCode) {
        return connectCode == CODE_REMOTE_LOGIN_CONFLICT;
    }
}

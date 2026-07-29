package com.juggle.im.android.auth;

import android.app.Activity;
import android.content.Intent;

import androidx.annotation.NonNull;

import com.juggle.im.android.R;
import com.juggle.im.android.app.LoginActivity;
import com.juggle.im.android.model.ConfigUtils;
import com.juggle.im.android.model.TraceContext;
import com.juggle.im.android.utils.LogUtils;
import com.juggle.im.android.utils.ToastUtils;

/**
 * 统一鉴权闸门。
 * <p>
 * 对“需要有效登录态的动作”做前置校验，登录态无效时统一阻断并跳转登录页。
 */
public final class AuthGuard {
    private static final String TAG = "AuthGuard";
    private static final String FEATURE = "auth-shell";

    private final SessionRepository sessionRepository;

    private AuthGuard(@NonNull SessionRepository sessionRepository) {
        this.sessionRepository = sessionRepository;
    }

    public static AuthGuard create(@NonNull Activity activity) {
        return new AuthGuard(SessionRepository.create(activity));
    }

    /**
     * 校验当前是否存在有效会话。
     *
     * @return true 表示可继续执行写操作；false 表示已被闸门阻断并跳转登录页
     */
    public boolean requireValidSessionForWrite(@NonNull Activity activity, @NonNull String action) {
        String traceId = TraceContext.currentOrNew();
        try {
            // TIPS：先记录会话存在性，getValidSession 会清理过期数据；未登录用户不应收到过期提示。
            boolean hadStoredSession = sessionRepository.hasStoredSession();
            SessionRepository.SessionState sessionState = sessionRepository.getValidSession();
            if (sessionState == null) {
                blockAndRedirect(
                        activity,
                        traceId,
                        action,
                        "session_absent_or_expired",
                        false,
                        hadStoredSession);
                return false;
            }
            ConfigUtils.appToken = sessionState.getAppToken();
            ConfigUtils.imToken = sessionState.getImToken();
            LogUtils.i(TAG, traceId, FEATURE, "auth.guard.pass", "success", "action=" + action);
            return true;
        } finally {
            TraceContext.clear();
        }
    }

    /**
     * 主动处理登录态失效场景（例如被其他设备顶下线）。
     */
    public void handleSessionInvalid(@NonNull Activity activity, @NonNull String reason) {
        String traceId = TraceContext.currentOrNew();
        try {
            blockAndRedirect(activity, traceId, "session.invalid", reason, true, true);
        } finally {
            TraceContext.clear();
        }
    }

    private void blockAndRedirect(@NonNull Activity activity,
                                  @NonNull String traceId,
                                  @NonNull String action,
                                  @NonNull String reason,
                                  boolean remoteKickOut,
                                  boolean showMessage) {
        sessionRepository.clearSession();
        UserProfileStore.clear(activity);
        ConfigUtils.appToken = null;
        ConfigUtils.imToken = null;
        LogUtils.e(TAG, traceId, FEATURE, "auth.guard.blocked", "fail",
                "action=" + action + ",reason=" + reason);

        if (showMessage) {
            int toastRes = remoteKickOut
                    ? R.string.auth_error_account_logged_in_other_device
                    : R.string.auth_error_session_invalid;
            ToastUtils.show(activity, toastRes);
        }

        Intent intent = new Intent(activity, LoginActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_NEW_TASK);
        activity.startActivity(intent);
        activity.finish();
    }
}

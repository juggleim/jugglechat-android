package com.juggle.im.android.chat.call;

import android.app.Activity;
import android.app.Application;
import android.graphics.Rect;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.juggle.im.JIM;
import com.juggle.im.android.R;
import com.juggle.im.android.utils.AvatarUtils;
import com.juggle.im.call.CallConst;
import com.juggle.im.call.ICallSession;
import com.juggle.im.model.UserInfo;
import com.qiniu.android.utils.StringUtils;

import org.json.JSONException;
import org.json.JSONObject;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * 全局来电横向浮窗管理器。
 *
 * 简要描述：
 * 1. 在 Application 维度监听来电
 * 2. 通过 ActivityLifecycleCallbacks 获取当前前台 Activity
 * 3. 将来电横向浮窗挂载到当前 Activity 的 decorView，保证全局页面可见
 */
public final class CallIncomingFloatingManager {
    private static final String RECEIVE_LISTENER_KEY = "GlobalIncomingCallFloat";
    private static final String SESSION_LISTENER_KEY = "GlobalIncomingCallFloatSession";
    private static final String ONGOING_SESSION_LISTENER_KEY = "GlobalOngoingCallFloatSession";

    private static final CallIncomingFloatingManager INSTANCE = new CallIncomingFloatingManager();

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final Application.ActivityLifecycleCallbacks lifecycleCallbacks = new Application.ActivityLifecycleCallbacks() {
        @Override
        public void onActivityCreated(@NonNull Activity activity, @Nullable Bundle savedInstanceState) {
        }

        @Override
        public void onActivityStarted(@NonNull Activity activity) {
        }

        @Override
        public void onActivityResumed(@NonNull Activity activity) {
            currentActivityRef = new WeakReference<>(activity);
            attachIncomingFloatToCurrentActivity();
            attachOngoingFloatToCurrentActivity();
        }

        @Override
        public void onActivityPaused(@NonNull Activity activity) {
            Activity current = currentActivityRef.get();
            if (current == activity) {
                currentActivityRef = new WeakReference<>(null);
            }
        }

        @Override
        public void onActivityStopped(@NonNull Activity activity) {
        }

        @Override
        public void onActivitySaveInstanceState(@NonNull Activity activity, @NonNull Bundle outState) {
        }

        @Override
        public void onActivityDestroyed(@NonNull Activity activity) {
            if (attachedIncomingActivity == activity) {
                removeIncomingFloatViewOnly();
            }
            if (attachedOngoingActivity == activity) {
                removeOngoingFloatViewOnly();
            }
        }
    };

    private final ICallSession.ICallSessionListener sessionListener = new ICallSession.ICallSessionListener() {
        @Override
        public void onCallConnect() {
            mainHandler.post(() -> dismissIncomingFloat());
        }

        @Override
        public void onCallFinish(CallConst.CallFinishReason callFinishReason) {
            mainHandler.post(() -> dismissIncomingFloat());
        }

        @Override
        public void onErrorOccur(CallConst.CallErrorCode callErrorCode) {
        }

        @Override
        public void onUsersInvite(String s, List<String> list) {
        }

        @Override
        public void onUsersConnect(List<String> list) {
        }

        @Override
        public void onUsersLeave(List<String> list) {
        }

        @Override
        public void onUserCameraEnable(String s, boolean b) {
        }

        @Override
        public void onUserMicrophoneEnable(String s, boolean b) {
        }

        @Override
        public void onSoundLevelUpdate(HashMap<String, Float> hashMap) {
        }

        @Override
        public void onVideoFirstFrameRender(String s) {
        }
    };

    private final ICallSession.ICallSessionListener ongoingSessionListener = new ICallSession.ICallSessionListener() {
        @Override
        public void onCallConnect() {
            mainHandler.post(() -> {
                CallUiStateStore.FloatingCallInfo info = CallUiStateStore.getFloatingCallInfo();
                if (info == null) {
                    return;
                }
                info.connected = true;
                if (info.connectedStartAt <= 0L) {
                    info.connectedStartAt = System.currentTimeMillis();
                }
                CallUiStateStore.saveFloatingCallInfo(info);
                attachOngoingFloatToCurrentActivity();
            });
        }

        @Override
        public void onCallFinish(CallConst.CallFinishReason callFinishReason) {
            mainHandler.post(() -> {
                CallUiStateStore.clearFloatingCallInfo();
                dismissOngoingFloat();
            });
        }

        @Override
        public void onErrorOccur(CallConst.CallErrorCode callErrorCode) {
        }

        @Override
        public void onUsersInvite(String s, List<String> list) {
        }

        @Override
        public void onUsersConnect(List<String> list) {
        }

        @Override
        public void onUsersLeave(List<String> list) {
        }

        @Override
        public void onUserCameraEnable(String s, boolean b) {
        }

        @Override
        public void onUserMicrophoneEnable(String s, boolean b) {
        }

        @Override
        public void onSoundLevelUpdate(HashMap<String, Float> hashMap) {
        }

        @Override
        public void onVideoFirstFrameRender(String s) {
        }
    };

    private boolean initialized;
    private Application application;
    private WeakReference<Activity> currentActivityRef = new WeakReference<>(null);
    private Activity attachedIncomingActivity;
    private Activity attachedOngoingActivity;
    private ICallSession incomingCallSession;
    private ICallSession ongoingCallSession;
    private View incomingFloatView;
    private View ongoingFloatView;
    private TextView tvOngoingTimer;
    private MediaPlayer incomingRingPlayer;
    private Runnable ongoingTimerRunnable;

    private CallIncomingFloatingManager() {
    }

    /**
     * 获取全局单例。
     */
    public static CallIncomingFloatingManager getInstance() {
        return INSTANCE;
    }

    /**
     * 初始化全局来电浮窗能力。
     *
     * @param application Application
     */
    public synchronized void init(Application application) {
        if (initialized) {
            return;
        }
        if (application == null) {
            return;
        }
        this.application = application;
        this.application.registerActivityLifecycleCallbacks(lifecycleCallbacks);

        JIM.getInstance().getCallManager().addReceiveListener(RECEIVE_LISTENER_KEY, iCallSession ->
                mainHandler.post(() -> handleIncomingCall(iCallSession)));

        initialized = true;
    }

    /**
     * 处理来电事件。
     */
    private void handleIncomingCall(ICallSession callSession) {
        if (callSession == null) {
            return;
        }

        if (incomingCallSession != null) {
            incomingCallSession.removeListener(SESSION_LISTENER_KEY);
        }

        incomingCallSession = callSession;
        incomingCallSession.addListener(SESSION_LISTENER_KEY, sessionListener);
        playIncomingRing();
        attachIncomingFloatToCurrentActivity();
    }

    /**
     * 将来电浮窗挂载到当前前台 Activity。
     *
     * 简要描述：
     * 若当前页面切换，浮窗会自动从旧页面解绑并挂载到新页面。
     */
    private void attachIncomingFloatToCurrentActivity() {
        if (incomingCallSession == null) {
            return;
        }

        Activity activity = currentActivityRef.get();
        if (activity == null || activity.isFinishing()) {
            return;
        }
        if (activity instanceof BaseCallActivity) {
            // 通话全屏页面内不再叠加来电横向浮窗
            return;
        }

        Window window = activity.getWindow();
        if (window == null) {
            return;
        }
        View decorView = window.getDecorView();
        if (!(decorView instanceof ViewGroup)) {
            return;
        }

        removeIncomingFloatViewOnly();

        incomingFloatView = LayoutInflater.from(activity)
                .inflate(R.layout.layout_call_incoming_floating, (ViewGroup) decorView, false);

        bindIncomingFloatContent(incomingFloatView, incomingCallSession);

        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                activity.getResources().getDimensionPixelSize(R.dimen.call_incoming_floating));
        params.leftMargin = dp(activity, 12);
        params.rightMargin = dp(activity, 12);
        params.topMargin = resolveTopMargin(activity);

        ((ViewGroup) decorView).addView(incomingFloatView, params);
        attachedIncomingActivity = activity;
    }

    /**
     * 绑定来电浮窗文案与事件。
     */
    private void bindIncomingFloatContent(View root, ICallSession callSession) {
        ImageView ivAvatar = root.findViewById(R.id.iv_avatar);
        TextView tvName = root.findViewById(R.id.tv_name);
        TextView tvDesc = root.findViewById(R.id.tv_desc);
        View btnReject = root.findViewById(R.id.btn_reject);
        View btnAccept = root.findViewById(R.id.btn_accept);

        UserInfo inviterInfo = JIM.getInstance().getUserInfoManager().getUserInfo(callSession.getInviter());
        String inviterName = inviterInfo == null || StringUtils.isNullOrEmpty(inviterInfo.getUserName())
                ? getSafeString(R.string.main_default_user_name)
                : inviterInfo.getUserName();
        String inviterAvatar = inviterInfo == null ? "" : inviterInfo.getPortrait();

        tvName.setText(inviterName);
        tvDesc.setText(callSession.getMediaType() == CallConst.CallMediaType.VIDEO
                ? R.string.call_status_incoming_video
                : R.string.call_status_incoming_voice);
        AvatarUtils.loadAvatar(ivAvatar, inviterAvatar, inviterName, callSession.getInviter());

        root.setOnClickListener(v -> {
            startIncomingCallPage(callSession, false);
            dismissIncomingFloat();
        });
        btnAccept.setOnClickListener(v -> {
            startIncomingCallPage(callSession, true);
            dismissIncomingFloat();
        });
        btnReject.setOnClickListener(v -> {
            callSession.hangup();
            dismissIncomingFloat();
        });
    }

    /**
     * 打开来电通话页。
     */
    private void startIncomingCallPage(ICallSession callSession, boolean autoAccept) {
        Activity activity = currentActivityRef.get();
        if (activity == null || activity.isFinishing()) {
            return;
        }
        activity.startActivity(buildIncomingCallIntent(activity, callSession, autoAccept));
    }

    /**
     * 构造来电页跳转 Intent。
     */
    private android.content.Intent buildIncomingCallIntent(Activity activity,
                                                           ICallSession callSession,
                                                           boolean autoAccept) {
        ArrayList<String> ids = callSession.getMembers().stream()
                .map(member -> member.getUserInfo().getUserId())
                .collect(Collectors.toCollection(ArrayList::new));

        boolean isMultiCall = ids.size() > 2;
        android.content.Intent intent = new android.content.Intent(
                activity,
                isMultiCall ? MultiCallActivity.class : SingleCallActivity.class
        );

        intent.putExtra(BaseCallActivity.EXTRA_INVITER, callSession.getInviter());
        intent.putExtra(BaseCallActivity.EXTRA_IS_VIDEO_CALL,
                callSession.getMediaType() == CallConst.CallMediaType.VIDEO);
        intent.putStringArrayListExtra(BaseCallActivity.EXTRA_TARGET_USER_IDS, ids);
        intent.putExtra(BaseCallActivity.EXTRA_DIRECTION, "incoming");
        intent.putExtra(BaseCallActivity.EXTRA_CALL_ID, callSession.getCallId());
        intent.putExtra(BaseCallActivity.EXTRA_AUTO_ACCEPT, autoAccept);
        intent.putExtra(BaseCallActivity.EXTRA_IS_GROUP_CALL, isMultiCall);

        String conversationId = parseConversationId(callSession.getExtra());
        if (!StringUtils.isNullOrEmpty(conversationId)) {
            intent.putExtra(BaseCallActivity.EXTRA_CONVERSATION_ID, conversationId);
        }
        return intent;
    }

    private String parseConversationId(String extra) {
        if (StringUtils.isBlank(extra)) {
            return "";
        }
        try {
            JSONObject jsonObject = new JSONObject(extra);
            return jsonObject.optString("conversationId", "");
        } catch (JSONException e) {
            return "";
        }
    }

    /**
     * 关闭来电浮窗并释放资源。
     */
    private void dismissIncomingFloat() {
        stopIncomingRing();

        if (incomingCallSession != null) {
            incomingCallSession.removeListener(SESSION_LISTENER_KEY);
            incomingCallSession = null;
        }

        removeIncomingFloatViewOnly();
    }

    private void removeIncomingFloatViewOnly() {
        if (incomingFloatView != null && incomingFloatView.getParent() instanceof ViewGroup) {
            ((ViewGroup) incomingFloatView.getParent()).removeView(incomingFloatView);
        }
        incomingFloatView = null;
        attachedIncomingActivity = null;
    }

    /**
     * 将通话中最小化浮窗挂载到当前前台 Activity。
     *
     * 简要描述：
     * 最小化状态来自 CallUiStateStore，页面切换时浮窗会自动迁移并保持计时连续。
     */
    private void attachOngoingFloatToCurrentActivity() {
        CallUiStateStore.FloatingCallInfo info = CallUiStateStore.getFloatingCallInfo();
        if (info == null || StringUtils.isNullOrEmpty(info.callId)) {
            dismissOngoingFloat();
            return;
        }

        Activity activity = currentActivityRef.get();
        if (activity == null || activity.isFinishing()) {
            return;
        }
        if (activity instanceof BaseCallActivity) {
            return;
        }

        Window window = activity.getWindow();
        if (window == null) {
            return;
        }
        View decorView = window.getDecorView();
        if (!(decorView instanceof ViewGroup)) {
            return;
        }

        removeOngoingFloatViewOnly();

        ongoingFloatView = LayoutInflater.from(activity)
                .inflate(R.layout.layout_call_ongoing_floating, (ViewGroup) decorView, false);
        tvOngoingTimer = ongoingFloatView.findViewById(R.id.tv_float_time);

        ImageView ivAvatar = ongoingFloatView.findViewById(R.id.iv_float_avatar);
        TextView tvName = ongoingFloatView.findViewById(R.id.tv_float_name);
        String displayUserId = resolveFloatingDisplayUserId(info);
        UserInfo userInfo = JIM.getInstance().getUserInfoManager().getUserInfo(displayUserId);
        String displayName = userInfo == null || StringUtils.isNullOrEmpty(userInfo.getUserName())
                ? getSafeString(R.string.call_status_float_ongoing)
                : userInfo.getUserName();
        String displayAvatar = userInfo == null ? "" : userInfo.getPortrait();
        tvName.setText(displayName);
        AvatarUtils.loadAvatar(ivAvatar, displayAvatar, displayName, displayUserId);

        ongoingFloatView.setOnClickListener(v -> {
            Activity current = currentActivityRef.get();
            CallUiStateStore.FloatingCallInfo currentInfo = CallUiStateStore.getFloatingCallInfo();
            if (current == null || currentInfo == null) {
                return;
            }
            current.startActivity(BaseCallActivity.buildRestoreIntent(current, currentInfo));
            CallUiStateStore.clearFloatingCallInfo();
            dismissOngoingFloat();
        });

        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT);
        params.gravity = android.view.Gravity.TOP | android.view.Gravity.END;
        params.rightMargin = dp(activity, 12);
        params.topMargin = resolveTopMargin(activity) + dp(activity, 36);
        ((ViewGroup) decorView).addView(ongoingFloatView, params);
        attachedOngoingActivity = activity;

        long startAt = info.connectedStartAt > 0L ? info.connectedStartAt : System.currentTimeMillis();
        startOngoingTimer(startAt);
        bindOngoingCallSession(info.callId);
    }

    /**
     * 关闭通话中最小化浮窗并释放资源。
     */
    private void dismissOngoingFloat() {
        stopOngoingTimer();
        if (ongoingCallSession != null) {
            ongoingCallSession.removeListener(ONGOING_SESSION_LISTENER_KEY);
            ongoingCallSession = null;
        }
        removeOngoingFloatViewOnly();
    }

    private void removeOngoingFloatViewOnly() {
        if (ongoingFloatView != null && ongoingFloatView.getParent() instanceof ViewGroup) {
            ((ViewGroup) ongoingFloatView.getParent()).removeView(ongoingFloatView);
        }
        ongoingFloatView = null;
        tvOngoingTimer = null;
        attachedOngoingActivity = null;
    }

    /**
     * 绑定最小化通话会话监听，用于自动刷新连接态和结束态。
     */
    private void bindOngoingCallSession(String callId) {
        if (ongoingCallSession != null) {
            ongoingCallSession.removeListener(ONGOING_SESSION_LISTENER_KEY);
            ongoingCallSession = null;
        }
        if (StringUtils.isNullOrEmpty(callId)) {
            return;
        }
        ongoingCallSession = JIM.getInstance().getCallManager().getCallSession(callId);
        if (ongoingCallSession != null) {
            ongoingCallSession.addListener(ONGOING_SESSION_LISTENER_KEY, ongoingSessionListener);
        }
    }

    /**
     * 启动最小化通话计时任务。
     */
    private void startOngoingTimer(long startAt) {
        if (tvOngoingTimer == null) {
            return;
        }
        stopOngoingTimer();
        tvOngoingTimer.setText(formatDuration(startAt));
        ongoingTimerRunnable = new Runnable() {
            @Override
            public void run() {
                if (tvOngoingTimer == null) {
                    return;
                }
                tvOngoingTimer.setText(formatDuration(startAt));
                mainHandler.postDelayed(this, 1000L);
            }
        };
        mainHandler.post(ongoingTimerRunnable);
    }

    /**
     * 停止最小化通话计时任务。
     */
    private void stopOngoingTimer() {
        if (ongoingTimerRunnable != null) {
            mainHandler.removeCallbacks(ongoingTimerRunnable);
            ongoingTimerRunnable = null;
        }
    }

    private void playIncomingRing() {
        stopIncomingRing();
        if (application == null) {
            return;
        }
        incomingRingPlayer = MediaPlayer.create(application, R.raw.call_incoming);
        if (incomingRingPlayer != null) {
            incomingRingPlayer.setLooping(true);
            incomingRingPlayer.start();
        }
    }

    private void stopIncomingRing() {
        if (incomingRingPlayer == null) {
            return;
        }
        if (incomingRingPlayer.isPlaying()) {
            incomingRingPlayer.stop();
        }
        incomingRingPlayer.release();
        incomingRingPlayer = null;
    }

    private int resolveTopMargin(Activity activity) {
        View decor = activity.getWindow().getDecorView();
        Rect frame = new Rect();
        decor.getWindowVisibleDisplayFrame(frame);
        int statusBar = Math.max(frame.top, 0);
        return statusBar + dp(activity, 12);
    }

    private int dp(Activity activity, int value) {
        return Math.round(activity.getResources().getDisplayMetrics().density * value);
    }

    private String formatDuration(long startAt) {
        long elapsedMillis = Math.max(0L, System.currentTimeMillis() - startAt);
        int seconds = (int) (elapsedMillis / 1000L);
        int minutes = seconds / 60;
        seconds = seconds % 60;
        return String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds);
    }

    private String resolveFloatingDisplayUserId(CallUiStateStore.FloatingCallInfo info) {
        if (info == null) {
            return "";
        }
        String currentUserId = JIM.getInstance().getCurrentUserId();
        if (info.targetUserIds != null) {
            for (String userId : info.targetUserIds) {
                if (!StringUtils.isNullOrEmpty(userId) && !userId.equals(currentUserId)) {
                    return userId;
                }
            }
        }
        return info.inviterUserId;
    }

    private String getSafeString(int stringRes) {
        if (application == null) {
            return "";
        }
        return application.getString(stringRes);
    }
}

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
            if (attachedActivity == activity) {
                removeIncomingFloatViewOnly();
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

    private boolean initialized;
    private Application application;
    private WeakReference<Activity> currentActivityRef = new WeakReference<>(null);
    private Activity attachedActivity;
    private ICallSession incomingCallSession;
    private View incomingFloatView;
    private MediaPlayer incomingRingPlayer;

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
        attachedActivity = activity;
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
        attachedActivity = null;
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

    private String getSafeString(int stringRes) {
        if (application == null) {
            return "";
        }
        return application.getString(stringRes);
    }
}

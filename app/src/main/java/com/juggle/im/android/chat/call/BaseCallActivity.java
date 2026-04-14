package com.juggle.im.android.chat.call;

import static android.view.View.VISIBLE;

import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.media.MediaPlayer;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import com.juggle.im.JIM;
import com.juggle.im.android.R;
import com.juggle.im.android.app.MainActivity;
import com.juggle.im.android.utils.PermissionComponent;
import com.juggle.im.call.CallConst;
import com.juggle.im.call.ICallSession;
import com.juggle.im.model.UserInfo;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * 通话页面基类，统一处理：
 * 1. 通话会话创建/绑定
 * 2. 权限检查与申请
 * 3. 来电铃声与通话计时
 * 4. 最小化到浮窗后的状态保存与恢复
 */
public abstract class BaseCallActivity extends AppCompatActivity {
    /** 会话ID */
    public static final String EXTRA_CALL_ID = "callId";
    /** 会话方向：incoming/outgoing */
    public static final String EXTRA_DIRECTION = "direction";
    /** 会话所属会话ID（私聊ID或群ID） */
    public static final String EXTRA_CONVERSATION_ID = "conversationId";
    /** 主叫用户ID */
    public static final String EXTRA_INVITER = "inviter";
    /** 参与人ID列表 */
    public static final String EXTRA_TARGET_USER_IDS = "targetUserIds";
    /** 是否视频通话 */
    public static final String EXTRA_IS_VIDEO_CALL = "is_video_call";
    /** 是否群组通话（群组才允许拉人） */
    public static final String EXTRA_IS_GROUP_CALL = "is_group_call";
    /** 进入页面后自动接听（用于来电浮窗快速接听） */
    public static final String EXTRA_AUTO_ACCEPT = "auto_accept";
    /** 恢复页面时是否已连接 */
    public static final String EXTRA_RESTORE_CONNECTED = "restore_connected";
    /** 恢复页面时连接起始时间（毫秒） */
    public static final String EXTRA_CONNECTED_START_AT = "connected_start_at";

    protected ICallSession callSession;
    protected String conversationId;
    protected MediaPlayer mediaPlayer;
    protected final Handler handler = new Handler(Looper.getMainLooper());
    protected long startTime = 0L;
    private Runnable timerRunnable;
    protected String direction;
    protected UserInfo inviterUserInfo;
    protected ArrayList<String> targetUserIds;
    protected boolean isVideoCall;
    protected boolean isGroupCall;
    protected boolean connected = false;
    protected boolean autoAccept = false;

    private static final int REQUEST_CODE_CALL_PERMISSION = 1001;

    /**
     * 发起单聊通话页面。
     *
     * @param context       上下文
     * @param conversationId 会话ID
     * @param isGroup       是否群组会话
     * @param isVideoCall   是否视频通话
     * @param inviter       发起人用户ID
     * @param targetUserIds 目标用户ID列表
     * @param direction     呼叫方向：outgoing/incoming
     */
    public static void startSingleCall(Context context,
                                       String conversationId,
                                       boolean isGroup,
                                       boolean isVideoCall,
                                       String inviter,
                                       ArrayList<String> targetUserIds,
                                       String direction) {
        if (!PermissionComponent.hasAllPermissions(context, PermissionComponent.getCallPermissions(isVideoCall))) {
            return;
        }

        Intent it = new Intent(context, SingleCallActivity.class);
        it.putExtra(EXTRA_CONVERSATION_ID, conversationId);
        it.putExtra(EXTRA_IS_VIDEO_CALL, isVideoCall);
        it.putExtra(EXTRA_INVITER, inviter);
        it.putExtra(EXTRA_DIRECTION, direction);
        it.putExtra(EXTRA_IS_GROUP_CALL, isGroup);
        it.putStringArrayListExtra(EXTRA_TARGET_USER_IDS, targetUserIds);
        context.startActivity(it);
    }

    /**
     * 发起多人通话页面。
     *
     * @param context        上下文
     * @param conversationId 会话ID
     * @param isVideoCall    是否视频通话
     * @param inviter        发起人用户ID
     * @param targetUserIds  目标用户ID列表
     * @param direction      呼叫方向：outgoing/incoming
     */
    public static void startMultiCall(Context context,
                                      String conversationId,
                                      boolean isVideoCall,
                                      String inviter,
                                      List<String> targetUserIds,
                                      String direction) {
        if (!PermissionComponent.hasAllPermissions(context, PermissionComponent.getCallPermissions(isVideoCall))) {
            return;
        }

        Intent it = new Intent(context, MultiCallActivity.class);
        it.putExtra(EXTRA_CONVERSATION_ID, conversationId);
        it.putExtra(EXTRA_IS_VIDEO_CALL, isVideoCall);
        it.putExtra(EXTRA_INVITER, inviter);
        it.putExtra(EXTRA_DIRECTION, direction);
        it.putExtra(EXTRA_IS_GROUP_CALL, true);
        it.putStringArrayListExtra(EXTRA_TARGET_USER_IDS, new ArrayList<>(targetUserIds));
        context.startActivity(it);
    }

    /**
     * 构建“从最小化浮窗恢复通话页”的跳转Intent。
     *
     * @param context 上下文
     * @param info    浮窗中的通话信息快照
     * @return 恢复通话页Intent
     */
    public static Intent buildRestoreIntent(Context context, CallUiStateStore.FloatingCallInfo info) {
        Intent it = new Intent(context, info.isMultiCall ? MultiCallActivity.class : SingleCallActivity.class);
        it.putExtra(EXTRA_CONVERSATION_ID, info.conversationId);
        it.putExtra(EXTRA_IS_VIDEO_CALL, info.isVideoCall);
        it.putExtra(EXTRA_INVITER, info.inviterUserId);
        it.putExtra(EXTRA_DIRECTION, info.direction);
        it.putExtra(EXTRA_CALL_ID, info.callId);
        it.putExtra(EXTRA_IS_GROUP_CALL, info.isGroupCall);
        it.putExtra(EXTRA_RESTORE_CONNECTED, info.connected);
        it.putExtra(EXTRA_CONNECTED_START_AT, info.connectedStartAt);
        it.putStringArrayListExtra(EXTRA_TARGET_USER_IDS, new ArrayList<>(info.targetUserIds));
        it.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        return it;
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
//        enableFullscreen();
        if (getSupportActionBar() != null) {
            getSupportActionBar().hide();
        }

        conversationId = getIntent().getStringExtra(EXTRA_CONVERSATION_ID);
        String callId = getIntent().getStringExtra(EXTRA_CALL_ID);
        direction = safeDirection(getIntent().getStringExtra(EXTRA_DIRECTION));
        String inviter = getIntent().getStringExtra(EXTRA_INVITER);
        targetUserIds = getIntent().getStringArrayListExtra(EXTRA_TARGET_USER_IDS);
        if (targetUserIds == null) {
            targetUserIds = new ArrayList<>();
        }
        inviterUserInfo = JIM.getInstance().getUserInfoManager().getUserInfo(inviter);
        isVideoCall = getIntent().getBooleanExtra(EXTRA_IS_VIDEO_CALL, false);
        isGroupCall = getIntent().getBooleanExtra(EXTRA_IS_GROUP_CALL, false);
        autoAccept = getIntent().getBooleanExtra(EXTRA_AUTO_ACCEPT, false);

        boolean restoreConnected = getIntent().getBooleanExtra(EXTRA_RESTORE_CONNECTED, false);
        long restoreConnectedStartAt = getIntent().getLongExtra(EXTRA_CONNECTED_START_AT, 0L);
        if (restoreConnected) {
            connected = true;
            startTime = restoreConnectedStartAt;
        }

        if (isIncoming(direction) || callId != null) {
            callSession = JIM.getInstance().getCallManager().getCallSession(callId);
            if (callSession == null) {
                CallUiStateStore.clearFloatingCallInfo();
                finish();
                return;
            }
            callSession.addListener(getSessionListenerTag(), listener);
        } else {
            onStartCall();
        }
    }

    /**
     * 当前页面使用的会话监听key，子类可覆盖。
     */
    protected String getSessionListenerTag() {
        return getClass().getSimpleName();
    }

    private String safeDirection(String value) {
        return value == null ? "outgoing" : value;
    }

    protected boolean isIncoming(String value) {
        return "incoming".equals(value);
    }

    protected void enableFullscreen() {
        Window window = getWindow();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            WindowCompat.setDecorFitsSystemWindows(window, false);
            window.setStatusBarColor(Color.TRANSPARENT);
            WindowInsetsControllerCompat insetsController =
                    new WindowInsetsControllerCompat(window, window.getDecorView());
            insetsController.hide(WindowInsetsCompat.Type.statusBars());
            insetsController.setSystemBarsBehavior(
                    WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
        } else {
            View decor = window.getDecorView();
            int flags = View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_FULLSCREEN;
            decor.setSystemUiVisibility(flags);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                window.addFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS);
            }
        }
        window.setNavigationBarColor(getColor(R.color.call_gb));
    }

    /**
     * 发起呼叫入口（主叫场景）。
     */
    protected abstract void onStartCall();

    /**
     * 通话建立成功回调。
     */
    public void onCallConnected() {
        connected = true;
        if (startTime <= 0L) {
            startTime = System.currentTimeMillis();
        }
    }

    /**
     * 远端用户加入回调。
     */
    public void onRemoteUserJoin(List<String> remoteUserIds) {
        // 默认空实现，子类按场景处理
    }

    /**
     * 远端用户离开回调。
     */
    public void onRemoteUserLeave(List<String> remoteUserIds) {
        // 默认空实现，子类按场景处理
    }

    /**
     * 通话结束回调。
     */
    public void onCallFinished(CallConst.CallFinishReason callFinishReason) {
        CallUiStateStore.clearFloatingCallInfo();
        finish();
    }

    private final ICallSession.ICallSessionListener listener = new ICallSession.ICallSessionListener() {
        @Override
        public void onCallConnect() {
            Log.d("CallActivity", "onCallConnect");
            onCallConnected();
        }

        @Override
        public void onCallFinish(CallConst.CallFinishReason callFinishReason) {
            Log.d("CallActivity", "onCallFinish: " + callFinishReason);
            onCallFinished(callFinishReason);
        }

        @Override
        public void onErrorOccur(CallConst.CallErrorCode callErrorCode) {
            Log.d("CallActivity", "onErrorOccur: " + callErrorCode);
        }

        @Override
        public void onUsersInvite(String userId, List<String> users) {
            Log.d("CallActivity", "onUsersInvite: " + userId);
        }

        @Override
        public void onUsersConnect(List<String> list) {
            Log.d("CallActivity", "onUsersConnect: " + list);
            onRemoteUserJoin(list);
        }

        @Override
        public void onUsersLeave(List<String> list) {
            Log.d("CallActivity", "onUsersLeave: " + list);
            onRemoteUserLeave(list);
        }

        @Override
        public void onUserCameraEnable(String userId, boolean enable) {
            Log.d("CallActivity", "onUserCameraEnable: " + userId + " " + enable);
        }

        @Override
        public void onUserMicrophoneEnable(String userId, boolean enable) {
            Log.d("CallActivity", "onUserMicrophoneEnable: " + userId + " " + enable);
        }

        @Override
        public void onSoundLevelUpdate(HashMap<String, Float> hashMap) {
            Log.d("CallActivity", "onSoundLevelUpdate: " + hashMap);
        }

        @Override
        public void onVideoFirstFrameRender(String userId) {
            Log.d("CallActivity", "onVideoFirstFrameRender: " + userId);
        }
    };

    /**
     * 发起单聊通话。
     */
    protected void startSingleCall(String userId, CallConst.CallMediaType mediaType) {
        if (!checkCurrentCallPermissions()) {
            requestCallPermissions();
            return;
        }
        callSession = JIM.getInstance().getCallManager().startSingleCall(userId, mediaType, listener);
    }

    /**
     * 发起多人通话。
     */
    protected void startMultiCall(List<String> userIdList, CallConst.CallMediaType mediaType) {
        if (!checkCurrentCallPermissions()) {
            requestCallPermissions();
            return;
        }
        callSession = JIM.getInstance().getCallManager().startMultiCall(userIdList, mediaType, listener);
    }

    /**
     * 接听来电。
     */
    protected void acceptCall() {
        if (!checkCurrentCallPermissions()) {
            requestCallPermissions();
            return;
        }
        if (callSession != null) {
            callSession.accept();
        }
    }

    /**
     * 检查当前通话权限。
     */
    private boolean checkCurrentCallPermissions() {
        return PermissionComponent.hasAllPermissions(this, PermissionComponent.getCallPermissions(isVideoCall));
    }

    /**
     * 请求通话权限。
     */
    private void requestCallPermissions() {
        PermissionComponent.requestPermissions(
                this,
                REQUEST_CODE_CALL_PERMISSION,
                PermissionComponent.getCallPermissions(isVideoCall)
        );
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode != REQUEST_CODE_CALL_PERMISSION) {
            return;
        }

        if (!PermissionComponent.areGrantResultsGranted(grantResults)) {
            finish();
            return;
        }

        if (callSession == null && "outgoing".equals(direction)) {
            onStartCall();
        } else if (callSession != null && isIncoming(direction)) {
            callSession.accept();
        }
    }

    /**
     * 挂断通话。
     */
    protected void hangupCall() {
        if (callSession != null) {
            callSession.hangup();
        }
    }

    /**
     * 设置摄像头开关。
     */
    protected void enableCamera(boolean enable) {
        if (callSession != null) {
            callSession.enableCamera(enable);
        }
    }

    /**
     * 设置麦克风静音。
     */
    protected void muteMicrophone(boolean mute) {
        if (callSession != null) {
            callSession.muteMicrophone(mute);
        }
    }

    /**
     * 设置扬声器静音。
     */
    protected void muteSpeaker(boolean mute) {
        if (callSession != null) {
            callSession.muteSpeaker(mute);
        }
    }

    /**
     * 打开/关闭扬声器。
     */
    protected void setSpeakerEnable(boolean enable) {
        if (callSession != null) {
            callSession.setSpeakerEnable(enable);
        }
    }

    /**
     * 切换前置/后置摄像头。
     */
    protected void useFrontCamera(boolean enable) {
        if (callSession != null) {
            callSession.useFrontCamera(enable);
        }
    }

    /**
     * 邀请用户加入通话。
     */
    protected void inviteUsers(List<String> userIdList) {
        if (callSession != null) {
            callSession.inviteUsers(userIdList);
        }
    }

    /**
     * 播放来电铃声。
     */
    protected void playCallRing() {
        stopAndRelease();
        mediaPlayer = MediaPlayer.create(this, R.raw.call_incoming);
        if (mediaPlayer != null) {
            mediaPlayer.setLooping(true);
            mediaPlayer.start();
        }
    }

    /**
     * 停止并释放铃声播放器。
     */
    protected void stopAndRelease() {
        if (mediaPlayer == null) {
            return;
        }
        if (mediaPlayer.isPlaying()) {
            mediaPlayer.stop();
        }
        mediaPlayer.release();
        mediaPlayer = null;
    }

    /**
     * 启动通话计时。
     *
     * 简要描述：
     * startTime 为空时使用当前时间；从浮窗恢复时会复用原 startTime，保证计时连续。
     */
    protected void setupTimer(TextView tvCallTimer) {
        tvCallTimer.setVisibility(VISIBLE);
        if (startTime <= 0L) {
            startTime = System.currentTimeMillis();
        }
        stopTimer();
        timerRunnable = new Runnable() {
            @Override
            public void run() {
                long elapsedMillis = System.currentTimeMillis() - startTime;
                int seconds = (int) (elapsedMillis / 1000);
                int minutes = seconds / 60;
                seconds = seconds % 60;
                String time = String.format("%02d:%02d", minutes, seconds);
                tvCallTimer.setText(time);
                handler.postDelayed(this, 1000L);
            }
        };
        handler.post(timerRunnable);
    }

    /**
     * 停止通话计时任务。
     */
    protected void stopTimer() {
        if (timerRunnable != null) {
            handler.removeCallbacks(timerRunnable);
            timerRunnable = null;
        }
    }

    /**
     * 最小化通话页，回到主页面并保留浮窗状态。
     *
     * 简要描述：
     * 这里只保存UI恢复所需的最小状态，不改变SDK层通话会话。
     */
    protected void minimizeToFloating(boolean multiCall) {
        if (callSession == null) {
            finish();
            return;
        }
        CallUiStateStore.FloatingCallInfo info = new CallUiStateStore.FloatingCallInfo();
        info.callId = callSession.getCallId();
        info.conversationId = conversationId;
        info.inviterUserId = inviterUserInfo == null ? "" : inviterUserInfo.getUserId();
        info.targetUserIds = new ArrayList<>(targetUserIds == null ? new ArrayList<>() : targetUserIds);
        info.isVideoCall = isVideoCall;
        info.isMultiCall = multiCall;
        info.isGroupCall = isGroupCall;
        info.direction = direction;
        info.connected = connected;
        info.connectedStartAt = startTime > 0L ? startTime : System.currentTimeMillis();
        CallUiStateStore.saveFloatingCallInfo(info);

        Intent mainIntent = new Intent(this, MainActivity.class);
        mainIntent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(mainIntent);
        finish();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (callSession != null) {
            callSession.removeListener(getSessionListenerTag());
        }
        stopAndRelease();
        stopTimer();
    }
}

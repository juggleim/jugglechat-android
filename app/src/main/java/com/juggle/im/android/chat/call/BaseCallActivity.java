package com.juggle.im.android.chat.call;

import static android.view.View.VISIBLE;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
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
import androidx.core.content.ContextCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import com.juggle.im.JIM;
import com.juggle.im.android.R;
import com.juggle.im.android.chat.SelectMemberActivity;
import com.juggle.im.call.CallConst;
import com.juggle.im.call.ICallSession;
import com.juggle.im.model.UserInfo;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public abstract class BaseCallActivity extends AppCompatActivity {
    protected ICallSession callSession;
    protected String conversationId;
    protected MediaPlayer mediaPlayer;
    protected Handler handler = new Handler(Looper.getMainLooper());
    protected long startTime = 0L;
    private Runnable timerRunnable;
    protected String direction;
    protected UserInfo inviterUserInfo;
    protected ArrayList<String> targetUserIds;
    protected boolean isVideoCall;
    protected boolean connected = false;
    private static final int REQUEST_CODE_CALL_PERMISSION = 1001;

    public static void startSingleCall(Context context, String conversationId, boolean isGroup,
                                 boolean isVideoCall, String inviter,
                                 ArrayList<String> targetUserIds, String direction) {
        // 检查所需权限
        if (!checkCallPermissions(context, isVideoCall)) {
            // 如果没有权限，可以考虑通知调用方或者给出提示
            return;
        }
        
        Intent it = isGroup ? new Intent(context, SelectMemberActivity.class) : new Intent(context, SingleCallActivity.class);
        it.putExtra("conversationId", conversationId);
        it.putExtra("is_video_call", isVideoCall);
        it.putExtra("inviter", inviter);
        it.putExtra("direction", direction);
        it.putExtra("GROUP_ID", conversationId);
        it.putStringArrayListExtra("targetUserIds", targetUserIds);
        context.startActivity(it);
    }

    public static void startMultiCall(Context context, String conversationId,
                                       boolean isVideoCall, String inviter,
                                       List<String> targetUserIds, String direction) {
        // 检查所需权限
        if (!checkCallPermissions(context, isVideoCall)) {
            // 如果没有权限，可以考虑通知调用方或者给出提示
            return;
        }
        
        Intent it = new Intent(context, MultiCallActivity.class);
        it.putExtra("conversationId", conversationId);
        it.putExtra("is_video_call", isVideoCall);
        it.putExtra("inviter", inviter);
        it.putExtra("direction", direction);
        it.putExtra("GROUP_ID", conversationId);
        it.putStringArrayListExtra("targetUserIds", (ArrayList<String>) targetUserIds);
        context.startActivity(it);
    }

    // 检查通话所需的权限
    private static boolean checkCallPermissions(Context context, boolean isVideoCall) {
        if (isVideoCall) {
            // 视频通话需要相机和录音权限
            return ContextCompat.checkSelfPermission(context, android.Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED &&
                    ContextCompat.checkSelfPermission(context, android.Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED;
        } else {
            // 音频通话只需要录音权限
            return ContextCompat.checkSelfPermission(context, android.Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED;
        }
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        enableFullscreen();
        if (getSupportActionBar() != null) {
            getSupportActionBar().hide();
        }
        conversationId = getIntent().getStringExtra("conversationId");
        String callId = getIntent().getStringExtra("callId");
        direction = getIntent().getStringExtra("direction");
        // 获取用户信息和通话类型
        String inviter = getIntent().getStringExtra("inviter");
        targetUserIds = getIntent().getStringArrayListExtra("targetUserIds");
        inviterUserInfo = JIM.getInstance().getUserInfoManager().getUserInfo(inviter);
        isVideoCall = getIntent().getBooleanExtra("is_video_call", false);
        if (direction.equals("incoming") || callId != null) {
            callSession = JIM.getInstance().getCallManager().getCallSession(callId);
            callSession.addListener(this.getClass().getSimpleName(), listener);
        } else {
            onStartCall();
        }
    }

    private void enableFullscreen() {
        Window window = getWindow();
        // For Android R (API 30) and above, use WindowCompat + WindowInsetsControllerCompat
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Let content draw behind system bars
            WindowCompat.setDecorFitsSystemWindows(window, false);
            // Make status bar transparent so content is visible behind it
            window.setStatusBarColor(Color.TRANSPARENT);
            // Hide the status bar
            WindowInsetsControllerCompat insetsController = new WindowInsetsControllerCompat(window, window.getDecorView());
            insetsController.hide(WindowInsetsCompat.Type.statusBars());
            // Allow swipe to reveal
            insetsController.setSystemBarsBehavior(WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
        } else {
            // Older devices: use legacy system UI flags
            View decor = window.getDecorView();
            int flags = View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_FULLSCREEN; // hide status bar
            decor.setSystemUiVisibility(flags);
            // For very old devices, make status bar translucent as a fallback
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                window.addFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS);
            }
        }
        // 设置底部导航栏颜色
        window.setNavigationBarColor(getColor(R.color.call_gb));
    }

    protected abstract void onStartCall();

    public void onCallConnected() {
        connected = true;
    }

    public void onRemoteUserJoin(List<String> remoteUserIds) {

    }

    public void onRemoteUserLeave(List<String> remoteUserIds) {

    }

    public void onCallFinished(CallConst.CallFinishReason callFinishReason) {
        finish();
    }

    private ICallSession.ICallSessionListener listener = new ICallSession.ICallSessionListener() {
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
        public void onUsersInvite(String s, List<String> list) {
            Log.d("CallActivity", "onUsersInvite: " + s);
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
        public void onUserCameraEnable(String s, boolean b) {
            Log.d("CallActivity", "onUserCameraEnable: " + s + " " + b);
        }

        @Override
        public void onUserMicrophoneEnable(String s, boolean b) {
            Log.d("CallActivity", "onUserMicrophoneEnable: " + s + " " + b);
        }

        @Override
        public void onSoundLevelUpdate(HashMap<String, Float> hashMap) {
            Log.d("CallActivity", "onSoundLevelUpdate: " + hashMap);
        }

        @Override
        public void onVideoFirstFrameRender(String s) {
            Log.d("CallActivity", "onVideoFirstFrameRender: " + s);
        }
    };

    protected void startSingleCall(String userId, CallConst.CallMediaType mediaType) {
        // 检查权限
        if (!checkCurrentCallPermissions()) {
            requestCallPermissions();
            return;
        }
        
        callSession = JIM.getInstance().getCallManager().startSingleCall(userId, mediaType, listener);
    }

    protected void startMultiCall(List<String> userIdList, CallConst.CallMediaType mediaType) {
        // 检查权限
        if (!checkCurrentCallPermissions()) {
            requestCallPermissions();
            return;
        }
        
        callSession = JIM.getInstance().getCallManager().startMultiCall(userIdList, mediaType, listener);
    }

    protected void acceptCall() {
        // 检查权限
        if (!checkCurrentCallPermissions()) {
            requestCallPermissions();
            return;
        }
        
        if (callSession != null) callSession.accept();
    }

    // 检查当前通话所需权限
    private boolean checkCurrentCallPermissions() {
        return checkCallPermissions(this, isVideoCall);
    }

    // 请求通话所需权限
    private void requestCallPermissions() {
        if (isVideoCall) {
            // 视频通话需要相机和录音权限
            requestPermissions(new String[]{
                    android.Manifest.permission.CAMERA,
                    android.Manifest.permission.RECORD_AUDIO
            }, REQUEST_CODE_CALL_PERMISSION);
        } else {
            // 音频通话只需要录音权限
            requestPermissions(new String[]{
                    android.Manifest.permission.RECORD_AUDIO
            }, REQUEST_CODE_CALL_PERMISSION);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_CODE_CALL_PERMISSION) {
            boolean allGranted = true;
            for (int result : grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    allGranted = false;
                    break;
                }
            }
            
            if (allGranted) {
                // 权限已授予，继续执行原来的操作
                if (callSession == null && direction.equals("outgoing")) {
                    // 这是发起呼叫的情况
                    onStartCall();
                } else if (callSession != null && direction.equals("incoming")) {
                    // 这是接受呼叫的情况
                    callSession.accept();
                }
            } else {
                finish(); // 简单处理，直接结束Activity
            }
        }
    }

    protected void hangupCall() {
        if (callSession != null) callSession.hangup();
    }

    protected void enableCamera(boolean enable) {
        if (callSession != null) callSession.enableCamera(enable);
    }

    protected void muteMicrophone(boolean mute) {
        if (callSession != null) callSession.muteMicrophone(mute);
    }

    protected void muteSpeaker(boolean mute) {
        if (callSession != null) callSession.muteSpeaker(mute);
    }

    protected void setSpeakerEnable(boolean enable) {
        if (callSession != null) callSession.setSpeakerEnable(enable);
    }

    protected void useFrontCamera(boolean enable) {
        if (callSession != null) callSession.useFrontCamera(enable);
    }

    protected void inviteUsers(List<String> userIdList) {
        if (callSession != null) callSession.inviteUsers(userIdList);
    }

    protected void playCallRing() {
        mediaPlayer = MediaPlayer.create(this, R.raw.call_incoming);
        if (mediaPlayer != null) {
            mediaPlayer.setLooping(true);
            mediaPlayer.start();
        }
    }

    protected void stopAndRelease() {
        if (mediaPlayer != null) {
            if (mediaPlayer.isPlaying()) {
                mediaPlayer.stop();
            }
            mediaPlayer.release();
            mediaPlayer = null;
        }
    }

    protected void setupTimer(TextView tvCallTimer) {
        tvCallTimer.setVisibility(VISIBLE);
        startTime = System.currentTimeMillis();
        timerRunnable = new Runnable() {
            @Override
            public void run() {
                long elapsedMillis = System.currentTimeMillis() - startTime;
                int seconds = (int) (elapsedMillis / 1000);
                int minutes = seconds / 60;
                seconds = seconds % 60;
                String time = String.format("%02d:%02d", minutes, seconds);
                tvCallTimer.setText(time);
                handler.postDelayed(this, 1000);
            }
        };

        // 开始计时
        handler.post(timerRunnable);
    }

    /**
     * 通话结束时调用，停止计时器
     */
    private void stopTimer() {
        if (handler != null && timerRunnable != null) {
            handler.removeCallbacks(timerRunnable);
            handler = null;
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (callSession != null) {
            callSession.removeListener(this.getClass().getSimpleName());
        }
        stopAndRelease();
        stopTimer();
    }
}
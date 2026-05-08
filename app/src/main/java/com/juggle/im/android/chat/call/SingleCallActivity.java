package com.juggle.im.android.chat.call;

import static android.view.View.GONE;
import static android.view.View.VISIBLE;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.Nullable;

import com.juggle.im.JIM;
import com.juggle.im.android.R;
import com.juggle.im.android.utils.AvatarUtils;
import com.juggle.im.call.CallConst;
import com.juggle.im.model.UserInfo;

import java.util.List;

/**
 * 单聊音视频通话页面。
 */
public class SingleCallActivity extends BaseCallActivity {
    private SurfaceView localSurfaceView;
    private SurfaceView remoteSurfaceView;

    private ViewGroup userBar;
    private View secondaryActionContainer;
    private View audioConnectedActionContainer;
    private View btnAccept;
    private View btnHangup;
    private View btnMinimize;
    private View btnMic;
    private View btnSpeaker;
    private View btnCamera;
    private View btnAudioMic;
    private View btnAudioHangup;
    private View btnAudioSpeaker;
    private View btnSwitchCamera;
    private TextView tvTime;
    private TextView tvNickname;
    private TextView tvStatus;
    private TextView tvMicLabel;
    private TextView tvSpeakerLabel;
    private TextView tvCameraLabel;
    private TextView tvHangupLabel;
    private ImageView imgAvatar;
    private ImageView btnMicMute;
    private ImageView btnSpeakerMute;
    private ImageView ivCamera;

    private boolean isSpeakerMute;
    private boolean isMicMute;
    private boolean isCameraEnabled = true;
    private boolean isFrontCamera = true;
    private boolean timerStarted;
    private String remoteUserId = "";

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_single_call);

        bindViews();
        resolveRemoteUser();
        renderRemoteUserInfo();
        initClickActions();
        initVideoViewsForStartup();

        if (!connected && isIncoming(direction)) {
            playCallRing();
        }

        updateCallUiState();

        // 简要描述：
        // 来电浮窗点击“接听”后会以 autoAccept 进入页面，这里直接发起接听，保持交互一致。
        if (!connected && autoAccept && isIncoming(direction)) {
            acceptCurrentCall();
        }
    }

    /**
     * 发起单聊呼叫（主叫场景）。
     */
    @Override
    protected void onStartCall() {
        if (targetUserIds == null || targetUserIds.isEmpty()) {
            finish();
            return;
        }
        startSingleCall(targetUserIds.get(0), isVideoCall ? CallConst.CallMediaType.VIDEO : CallConst.CallMediaType.VOICE);
        if (isVideoCall) {
            startLocalPreviewIfNeed();
        }
    }

    /**
     * 通话建立后刷新UI并绑定远端视频流。
     */
    @Override
    public void onCallConnected() {
        super.onCallConnected();
        stopAndRelease();
        // tips: 单聊视频在接通后会从“等待态小窗预览”切到“已接通态主界面”。
        // 这里需要像多人通话一样重新绑定本地预览，避免首帧或 Surface 重建后本地画面丢失。
        startLocalPreviewIfNeed();
        updateCallUiState();
        bindRemoteVideoIfNeed();
    }

    /**
     * 远端加入时绑定远端视频流。
     *
     * @param remoteUserIds 新加入用户ID列表
     */
    @Override
    public void onRemoteUserJoin(List<String> remoteUserIds) {
        if (remoteUserIds == null || remoteUserIds.isEmpty()) {
            return;
        }
        if (TextUtils.isEmpty(remoteUserId)) {
            remoteUserId = remoteUserIds.get(0);
            renderRemoteUserInfo();
        }
        bindRemoteVideoIfNeed();
    }

    /**
     * 通话结束回调。
     *
     * @param callFinishReason 结束原因
     */
    @Override
    public void onCallFinished(CallConst.CallFinishReason callFinishReason) {
        super.onCallFinished(callFinishReason);
    }

    private void bindViews() {
        imgAvatar = findViewById(R.id.img_avatar);
        tvNickname = findViewById(R.id.tv_nickname);
        tvStatus = findViewById(R.id.tv_call_status);
        tvTime = findViewById(R.id.tv_call_duration);
        btnAccept = findViewById(R.id.btn_accept);
        btnHangup = findViewById(R.id.btn_hangup);
        btnMinimize = findViewById(R.id.btn_minimize);
        btnMicMute = findViewById(R.id.iv_mic);
        btnSpeakerMute = findViewById(R.id.iv_speaker);
        tvMicLabel = findViewById(R.id.tv_mic_label);
        tvSpeakerLabel = findViewById(R.id.tv_speaker_label);
        tvCameraLabel = findViewById(R.id.tv_camera_label);
        tvHangupLabel = findViewById(R.id.tv_hangup_label);
        secondaryActionContainer = findViewById(R.id.secondary_action_container);
        audioConnectedActionContainer = findViewById(R.id.audio_connected_action_container);
        userBar = findViewById(R.id.call_user_bar);
        btnMic = findViewById(R.id.btn_mic);
        btnSpeaker = findViewById(R.id.btn_speaker);
        btnCamera = findViewById(R.id.btn_camera);
        btnAudioMic = findViewById(R.id.btn_audio_mic);
        btnAudioHangup = findViewById(R.id.btn_audio_hangup);
        btnAudioSpeaker = findViewById(R.id.btn_audio_speaker);
        btnSwitchCamera = findViewById(R.id.btn_switch_camera);
        localSurfaceView = findViewById(R.id.local_surface_view);
        remoteSurfaceView = findViewById(R.id.remote_surface_view);
        ivCamera = findViewById(R.id.iv_camera);

        // tips: 单聊接通后远端全屏 SurfaceView 需要保持在底层，本地预览小窗提升为 overlay。
        // 这样远端大画面和本地悬浮小窗的层级才稳定，不会在接通态被底层视频平面覆盖。
        remoteSurfaceView.setZOrderMediaOverlay(false);
        remoteSurfaceView.setZOrderOnTop(false);
        localSurfaceView.setZOrderMediaOverlay(true);
        localSurfaceView.setZOrderOnTop(true);
    }

    private void initClickActions() {
        btnMinimize.setOnClickListener(v -> minimizeToFloating(false));

        btnMic.setOnClickListener(v -> toggleMic());
        btnSpeaker.setOnClickListener(v -> toggleSpeaker());
        btnCamera.setOnClickListener(v -> toggleCamera());
        btnAudioMic.setOnClickListener(v -> toggleMic());
        btnAudioHangup.setOnClickListener(v -> {
            hangupCall();
            finish();
        });
        btnAudioSpeaker.setOnClickListener(v -> toggleSpeaker());
        btnSwitchCamera.setOnClickListener(v -> switchCamera());

        btnAccept.setOnClickListener(v -> acceptCurrentCall());
        btnHangup.setOnClickListener(v -> {
            hangupCall();
            finish();
        });
    }

    private void acceptCurrentCall() {
        acceptCall();
        stopAndRelease();
        if (isVideoCall) {
            startLocalPreviewIfNeed();
        }
        updateCallUiState();
    }

    private void resolveRemoteUser() {
        String currentUserId = JIM.getInstance().getCurrentUserId();
        if (isIncoming(direction)) {
            remoteUserId = inviterUserInfo == null ? "" : inviterUserInfo.getUserId();
        }
        if (!TextUtils.isEmpty(remoteUserId)) {
            return;
        }
        if (targetUserIds != null) {
            for (String userId : targetUserIds) {
                if (!TextUtils.equals(userId, currentUserId)) {
                    remoteUserId = userId;
                    break;
                }
            }
        }
    }

    private void renderRemoteUserInfo() {
        UserInfo userInfo = TextUtils.isEmpty(remoteUserId)
                ? inviterUserInfo
                : JIM.getInstance().getUserInfoManager().getUserInfo(remoteUserId);
        if (userInfo == null) {
            tvNickname.setText(R.string.main_default_user_name);
            AvatarUtils.loadAvatar(imgAvatar, "", getString(R.string.main_default_user_name));
            return;
        }

        tvNickname.setText(TextUtils.isEmpty(userInfo.getUserName())
                ? getString(R.string.main_default_user_name)
                : userInfo.getUserName());
        AvatarUtils.loadAvatar(imgAvatar, userInfo.getPortrait(), userInfo.getUserName(), userInfo.getUserId());
    }

    /**
     * 初始化视频视图的可见性与预览。
     *
     * tips: 主叫（outgoing）场景下 BaseCallActivity.onCreate 提前调用 onStartCall，
     * 此时视图尚未绑定（localSurfaceView == null），预览未启动。
     * 这里在 bindViews 之后补调 startLocalPreviewIfNeed 确保预览正常启动。
     */
    private void initVideoViewsForStartup() {
        localSurfaceView.setVisibility(GONE);
        remoteSurfaceView.setVisibility(GONE);

        if (!isVideoCall) {
            return;
        }

        if (connected) {
            startLocalPreviewIfNeed();
            bindRemoteVideoIfNeed();
            return;
        }

        if ("outgoing".equals(direction)) {
            startLocalPreviewIfNeed();
        }
    }

    /**
     * 启动本地视频预览。
     *
     * tips: BaseCallActivity.onCreate 在子类 setContentView/bindViews 之前调用 onStartCall，
     * 此时 localSurfaceView 可能为 null，需要做空判断保护。
     */
    private void startLocalPreviewIfNeed() {
        if (!isVideoCall || callSession == null || localSurfaceView == null) {
            return;
        }
        localSurfaceView.setVisibility(VISIBLE);
        callSession.startPreview(localSurfaceView);
    }

    private void bindRemoteVideoIfNeed() {
        if (!isVideoCall || callSession == null || TextUtils.isEmpty(remoteUserId)) {
            return;
        }
        remoteSurfaceView.setVisibility(VISIBLE);
        callSession.setVideoView(remoteUserId, remoteSurfaceView);
    }

    /**
     * 根据通话状态刷新页面。
     *
     * 简要描述：
     * 所有UI状态都从 connected + direction + isVideoCall 推导，避免多个入口重复改控件导致状态错乱。
     */
    private void updateCallUiState() {
        boolean incomingWaiting = !connected && isIncoming(direction);
        boolean outgoingWaiting = !connected && !incomingWaiting;
        boolean videoConnected = connected && isVideoCall;
        boolean audioConnected = connected && !isVideoCall;

        btnAccept.setVisibility(incomingWaiting ? VISIBLE : GONE);
        tvTime.setVisibility(connected ? VISIBLE : GONE);
        secondaryActionContainer.setVisibility(videoConnected ? VISIBLE : GONE);
        audioConnectedActionContainer.setVisibility(audioConnected ? VISIBLE : GONE);
        btnHangup.setVisibility(audioConnected ? GONE : VISIBLE);
        btnCamera.setVisibility(videoConnected ? VISIBLE : GONE);
        btnSwitchCamera.setVisibility(videoConnected ? VISIBLE : GONE);
        userBar.setVisibility(videoConnected ? GONE : VISIBLE);
        imgAvatar.setVisibility(videoConnected ? GONE : VISIBLE);
        tvHangupLabel.setText(incomingWaiting ? R.string.call_action_reject : R.string.call_action_cancel);

        if (outgoingWaiting) {
            tvStatus.setText(isVideoCall ? R.string.call_status_video_outgoing : R.string.call_status_voice_outgoing);
            return;
        }

        if (incomingWaiting) {
            tvStatus.setText(isVideoCall ? R.string.call_status_incoming_video : R.string.call_status_incoming_voice);
            return;
        }

        stopAndRelease();
        tvStatus.setText(R.string.call_status_connected);
        ensureTimerStarted();
    }

    private void ensureTimerStarted() {
        if (timerStarted) {
            return;
        }
        timerStarted = true;
        setupTimer(tvTime);
    }

    private void toggleMic() {
        if (callSession == null) {
            return;
        }
        callSession.muteMicrophone(!isMicMute);
        isMicMute = !isMicMute;
        btnMicMute.setImageResource(isMicMute ? R.drawable.icon_mic_off : R.drawable.icon_mic_on);
        ImageView audioMicView = findViewById(R.id.iv_audio_mic);
        TextView audioMicLabel = findViewById(R.id.tv_audio_mic_label);
        if (audioMicView != null) {
            audioMicView.setImageResource(isMicMute ? R.drawable.icon_mic_off : R.drawable.icon_mic_on);
        }
        if (audioMicLabel != null) {
            audioMicLabel.setText(isMicMute ? R.string.call_action_mic_off : R.string.call_action_mic_on);
        }
        tvMicLabel.setText(isMicMute ? R.string.call_action_mic_off : R.string.call_action_mic_on);
    }

    private void toggleSpeaker() {
        if (callSession == null) {
            return;
        }
        callSession.muteSpeaker(!isSpeakerMute);
        isSpeakerMute = !isSpeakerMute;
        btnSpeakerMute.setImageResource(isSpeakerMute ? R.drawable.icon_speaker_off : R.drawable.icon_speaker_on);
        ImageView audioSpeakerView = findViewById(R.id.iv_audio_speaker);
        TextView audioSpeakerLabel = findViewById(R.id.tv_audio_speaker_label);
        if (audioSpeakerView != null) {
            audioSpeakerView.setImageResource(isSpeakerMute ? R.drawable.icon_speaker_off : R.drawable.icon_speaker_on);
        }
        if (audioSpeakerLabel != null) {
            audioSpeakerLabel.setText(isSpeakerMute ? R.string.call_action_speaker_off : R.string.call_action_speaker_on);
        }
        tvSpeakerLabel.setText(isSpeakerMute ? R.string.call_action_speaker_off : R.string.call_action_speaker_on);
    }

    /**
     * 切换前后摄像头。
     *
     * tips: 仅在视频接通态展示，状态只保留当前是否前置，避免引入额外的复杂摄像头状态机。
     */
    private void switchCamera() {
        if (callSession == null || !isVideoCall) {
            return;
        }
        isFrontCamera = !isFrontCamera;
        useFrontCamera(isFrontCamera);
    }

    /**
     * 切换摄像头启用状态。
     *
     * tips: 仅在视频通话接通后展示，沿用 BaseCallActivity 的摄像头控制能力，不额外引入新的状态源。
     */
    private void toggleCamera() {
        if (callSession == null || !isVideoCall) {
            return;
        }
        isCameraEnabled = !isCameraEnabled;
        enableCamera(isCameraEnabled);
        ivCamera.setAlpha(isCameraEnabled ? 1f : 0.55f);
        tvCameraLabel.setText(isCameraEnabled ? R.string.call_action_camera_on : R.string.call_action_camera_off);
    }
}

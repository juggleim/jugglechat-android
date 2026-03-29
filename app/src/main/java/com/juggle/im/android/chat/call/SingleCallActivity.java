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

    private ViewGroup connectedContainer;
    private ViewGroup userBar;
    private View connectingContainer;
    private View btnAccept;
    private View btnHangup;
    private View btnMinimize;
    private TextView tvTime;
    private TextView tvNickname;
    private TextView tvStatus;
    private ImageView imgAvatar;
    private ImageView btnMicMute;
    private ImageView btnSpeakerMute;

    private boolean isSpeakerMute;
    private boolean isMicMute;
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
        connectedContainer = findViewById(R.id.connected_container);
        userBar = findViewById(R.id.call_user_bar);
        connectingContainer = findViewById(R.id.connecting_container);
        localSurfaceView = findViewById(R.id.local_surface_view);
        remoteSurfaceView = findViewById(R.id.remote_surface_view);
    }

    private void initClickActions() {
        btnMinimize.setOnClickListener(v -> minimizeToFloating(false));

        btnMicMute.setOnClickListener(v -> toggleMic());
        btnSpeakerMute.setOnClickListener(v -> toggleSpeaker());

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

    private void startLocalPreviewIfNeed() {
        if (!isVideoCall || callSession == null) {
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
     * 所有UI状态都从 connected + direction 推导，避免多个入口重复改控件导致状态错乱。
     */
    private void updateCallUiState() {
        if (!connected) {
            connectedContainer.setVisibility(GONE);
            connectingContainer.setVisibility(VISIBLE);
            tvTime.setVisibility(GONE);
            userBar.setVisibility(VISIBLE);
            if ("outgoing".equals(direction)) {
                btnAccept.setVisibility(GONE);
                tvStatus.setText(R.string.call_status_waiting_answer);
            } else {
                btnAccept.setVisibility(VISIBLE);
                tvStatus.setText(isVideoCall ? R.string.call_status_incoming_video : R.string.call_status_incoming_voice);
            }
            return;
        }

        stopAndRelease();
        btnAccept.setVisibility(GONE);
        connectingContainer.setVisibility(VISIBLE);
        connectedContainer.setVisibility(VISIBLE);
        tvStatus.setText(R.string.call_status_connected);
        ensureTimerStarted();

        if (isVideoCall) {
            userBar.setVisibility(GONE);
        } else {
            userBar.setVisibility(VISIBLE);
        }
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
    }

    private void toggleSpeaker() {
        if (callSession == null) {
            return;
        }
        callSession.muteSpeaker(!isSpeakerMute);
        isSpeakerMute = !isSpeakerMute;
        btnSpeakerMute.setImageResource(isSpeakerMute ? R.drawable.icon_speaker_on : R.drawable.icon_speaker_off);
    }
}

package com.juggle.im.android.chat.call;

import static android.view.View.GONE;
import static android.view.View.VISIBLE;

import android.os.Bundle;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.Nullable;

import com.juggle.im.android.R;
import com.juggle.im.android.utils.AvatarUtils;
import com.juggle.im.call.CallConst;

import java.util.List;

public class SingleCallActivity extends BaseCallActivity {
    private SurfaceView localSurfaceView;
    private SurfaceView remoteSurfaceView;

    private ViewGroup connectedContainer, userBar;
    private View btnAccept, btnHangup;
    private TextView tvTime;
    private ImageView btnMicMute, btnSpeakerMute;
    private boolean isSpeakerMute, isMicMute;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_single_call);

        // 初始化 UI
        ImageView imgAvatar = findViewById(R.id.img_avatar);
        TextView tvNickname = findViewById(R.id.tv_nickname);
        btnAccept = findViewById(R.id.btn_accept);
        btnHangup = findViewById(R.id.btn_hangup);
        btnMicMute = findViewById(R.id.iv_mic);
        btnSpeakerMute = findViewById(R.id.iv_speaker);
        connectedContainer = findViewById(R.id.connected_container);
        tvTime = findViewById(R.id.tv_call_duration);
        userBar = findViewById(R.id.call_user_bar);
        btnMicMute.setOnClickListener(v -> toggleMic());
        btnSpeakerMute.setOnClickListener(v -> toggleSpeaker());
        if (inviterUserInfo != null) {
            tvNickname.setText(inviterUserInfo.getUserName());
            AvatarUtils.loadAvatar(imgAvatar, inviterUserInfo.getPortrait(), inviterUserInfo.getUserName());
        }

        if (!connected) {
            playCallRing();
        }
        setupView();
        tvTime.setVisibility(VISIBLE);
        btnHangup.setVisibility(VISIBLE);

        // 初始化视频视图（仅视频通话时显示）
        localSurfaceView = findViewById(R.id.local_surface_view);
        remoteSurfaceView = findViewById(R.id.remote_surface_view);
        if (isVideoCall && direction.equals("outgoing")) {
            localSurfaceView.setVisibility(VISIBLE);
            callSession.startPreview(localSurfaceView);
        }
        // 设置按钮事件
        btnAccept.setOnClickListener(v -> {
            acceptCall();
            if (isVideoCall) {
                localSurfaceView.setVisibility(VISIBLE);
                callSession.setVideoView(inviterUserInfo.getUserId(), localSurfaceView);
            }
            stopAndRelease();
            setupTimer(tvTime);
        });

        btnHangup.setOnClickListener(v -> {
            hangupCall();
            finish();
        });
    }

    @Override
    protected void onStartCall() {
        startSingleCall(targetUserIds.get(0), isVideoCall ? CallConst.CallMediaType.VIDEO : CallConst.CallMediaType.VOICE);
    }

    private void setupView() {
        if (!connected) {
            connectedContainer.setVisibility(GONE);
            if (direction.equals("outgoing")) {
                btnAccept.setVisibility(GONE);
                tvTime.setText("等待对方接听...");
            } else {
                btnAccept.setVisibility(VISIBLE);
                tvTime.setText("通话邀请中...");
            }
        } else {
            btnAccept.setVisibility(GONE);
            connectedContainer.setVisibility(VISIBLE);
        }
    }

    @Override
    public void onCallConnected() {
        super.onCallConnected();
        setupView();
    }

    @Override
    public void onRemoteUserJoin(List<String> remoteUserIds) {
        if (isVideoCall) {
            remoteSurfaceView.setVisibility(VISIBLE);
            callSession.setVideoView(remoteUserIds.get(0), remoteSurfaceView);
            userBar.setVisibility(GONE);
        }
    }

    @Override
    public void onCallFinished(CallConst.CallFinishReason callFinishReason) {
        super.onCallFinished(callFinishReason);
    }

    private void toggleMic() {
        callSession.muteMicrophone(!isMicMute);
        isMicMute = !isMicMute;
        btnMicMute.setImageResource(isMicMute ? R.drawable.ic_mic_off : R.drawable.ic_mic_on);
    }

    private void toggleSpeaker() {
        callSession.muteSpeaker(!isSpeakerMute);
        isSpeakerMute = !isSpeakerMute;
        btnSpeakerMute.setImageResource(isSpeakerMute ? R.drawable.ic_speaker_off : R.drawable.ic_speaker_on);
    }
}
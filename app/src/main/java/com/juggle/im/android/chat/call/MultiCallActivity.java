package com.juggle.im.android.chat.call;

import static android.view.View.GONE;
import static android.view.View.VISIBLE;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.SurfaceView;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.gridlayout.widget.GridLayout;

import com.juggle.im.JIM;
import com.juggle.im.android.R;
import com.juggle.im.android.utils.AvatarUtils;
import com.juggle.im.call.CallConst;
import com.juggle.im.model.UserInfo;

import java.util.ArrayList;
import java.util.List;

public class MultiCallActivity extends BaseCallActivity {
    private GridLayout gridParticipants;
    private TextView tvCallTime;
    private View btnHangup, btnInvite, btnAccept;
    private ImageView btnMicMute, btnSpeakerMute;
    private boolean isSpeakerMute, isMicMute;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_multi_call);

        gridParticipants = findViewById(R.id.grid_participants);
        tvCallTime = findViewById(R.id.tv_call_time);
        btnInvite = findViewById(R.id.btn_invite);
        btnHangup = findViewById(R.id.btn_hangup);
        btnMicMute = findViewById(R.id.iv_mic);
        btnSpeakerMute = findViewById(R.id.iv_speaker);
        btnAccept = findViewById(R.id.btn_accept);

        btnInvite.setOnClickListener(v -> inviteUsers(getUserIds()));
        btnHangup.setOnClickListener(v -> hangupCall());
        btnMicMute.setOnClickListener(v -> toggleMic());
        btnSpeakerMute.setOnClickListener(v -> toggleSpeaker());

        for (String userId : targetUserIds) {
            UserInfo userInfo = JIM.getInstance().getUserInfoManager().getUserInfo(userId);
            if (isVideoCall) {
                addVideoParticipant(userInfo);
            } else {
                addAudioParticipant(userInfo);
            }
        }
        btnAccept.setOnClickListener(v -> {
            acceptCall();
        });
        setupView();
    }
    @Override
    protected void onStartCall() {
        startMultiCall(targetUserIds, isVideoCall ? CallConst.CallMediaType.VIDEO : CallConst.CallMediaType.VOICE);
    }
    @Override
    public void onCallConnected() {
        super.onCallConnected();
        setupTimer(tvCallTime);
        setupView();
    }
    private void setupView() {
        if (!connected) {
            if (direction.equals("outgoing")) {
                btnAccept.setVisibility(GONE);
            } else {
                btnAccept.setVisibility(VISIBLE);
            }
        } else {
            btnAccept.setVisibility(GONE);
        }
    }

    @Override
    public void onRemoteUserJoin(List<String> remoteUserIds) {
        if (isVideoCall) {
            for (String userId : remoteUserIds) {
                SurfaceView surfaceView = gridParticipants.findViewWithTag(userId);
                callSession.setVideoView(userId, surfaceView);
            }
        }
    }

    @Override
    public void onRemoteUserLeave(List<String> remoteUserIds) {
        super.onRemoteUserLeave(remoteUserIds);
        for (String userId : remoteUserIds) {
            View u = gridParticipants.findViewWithTag(userId);
            gridParticipants.removeView(u);
        }
        if (gridParticipants.getChildCount() <= 1) {
            Toast.makeText(this, "通话结束", Toast.LENGTH_SHORT).show();
            finish();
        }
    }

    @Override
    public void onCallFinished(CallConst.CallFinishReason callFinishReason) {
        super.onCallFinished(callFinishReason);
    }

    private void addAudioParticipant(UserInfo userInfo) {
        View memberView = LayoutInflater.from(this).inflate(R.layout.item_voice_participant, gridParticipants, false);
        ImageView imgAvatar = memberView.findViewById(R.id.img_member_avatar);
        TextView tvName = memberView.findViewById(R.id.tv_member_name);
        memberView.setTag(userInfo.getUserId());
        tvName.setText(userInfo.getUserName());
        AvatarUtils.loadAvatar(imgAvatar, userInfo.getPortrait(), userInfo.getUserName());

        GridLayout.LayoutParams params = new GridLayout.LayoutParams();
        params.width = 0; // 平分宽度
        params.height = 0; // 平分高度
        params.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f);
        params.rowSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f);
        gridParticipants.addView(memberView, params);
    }

    private void addVideoParticipant(UserInfo userInfo) {
        View videoViewLayout = LayoutInflater.from(this).inflate(R.layout.item_video_participant, gridParticipants, false);
        SurfaceView surfaceView = videoViewLayout.findViewById(R.id.surface_view);
        surfaceView.setTag(userInfo.getUserId());
        TextView tvName = videoViewLayout.findViewById(R.id.tv_name);
        tvName.setText(userInfo.getUserName());

        GridLayout.LayoutParams params = new GridLayout.LayoutParams();
        params.width = 0; // 平分宽度
        params.height = 0; // 平分高度
        params.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f);
        params.rowSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f);

        gridParticipants.addView(videoViewLayout, params);
    }

    private List<String> getUserIds() {
        List<String> userIds = new ArrayList<>();
        return userIds;
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
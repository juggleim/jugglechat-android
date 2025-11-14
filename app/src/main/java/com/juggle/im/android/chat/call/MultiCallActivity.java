package com.juggle.im.android.chat.call;

import static android.view.View.GONE;
import static android.view.View.VISIBLE;

import static com.juggle.im.android.chat.SelectCallMemberActivity.DISABLE_MEMBERS;
import static com.juggle.im.android.chat.SelectCallMemberActivity.GROUP_ID;
import static com.juggle.im.android.chat.SelectCallMemberActivity.SELECTED_MEMBERS;

import android.content.Intent;
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
import com.juggle.im.android.chat.SelectCallMemberActivity;
import com.juggle.im.android.utils.AvatarUtils;
import com.juggle.im.call.CallConst;
import com.juggle.im.model.UserInfo;

import java.util.ArrayList;
import java.util.List;

public class MultiCallActivity extends BaseCallActivity {
    private static final int REQUEST_SELECT_MEMBERS = 1000;
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

        btnInvite.setOnClickListener(v -> {
            Intent it = new Intent(this, SelectCallMemberActivity.class);
            it.putExtra("is_video_call", isVideoCall);
            it.putExtra(GROUP_ID, conversationId);
            it.putStringArrayListExtra(DISABLE_MEMBERS, targetUserIds);
            startActivityForResult(it, REQUEST_SELECT_MEMBERS);
        });
        btnHangup.setOnClickListener(v -> hangupCall());
        btnMicMute.setOnClickListener(v -> toggleMic());
        btnSpeakerMute.setOnClickListener(v -> toggleSpeaker());
        updateParticipantView(targetUserIds);
        // preview self video
        View view = gridParticipants.findViewWithTag(JIM.getInstance().getCurrentUserId());
        SurfaceView surfaceView = view.findViewById(R.id.surface_view);
        callSession.startPreview(surfaceView);

        btnAccept.setOnClickListener(v -> {
            acceptCall();
        });
        setupView();
    }

    private void updateParticipantView(ArrayList<String> users) {
        if (users == null || users.isEmpty()) return;
        for (String userId : users) {
            UserInfo userInfo = JIM.getInstance().getUserInfoManager().getUserInfo(userId);
            if (isVideoCall) {
                addVideoParticipant(userInfo);
            } else {
                addAudioParticipant(userInfo);
            }
        }
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
        ArrayList<String> newUsers = new ArrayList<>();
        for (String userId : remoteUserIds) {
            if (!targetUserIds.contains(userId)) {
                targetUserIds.add(userId);
                newUsers.add(userId);
            }
            if (isVideoCall) {
                View view = gridParticipants.findViewWithTag(userId);
                SurfaceView surfaceView = view.findViewById(R.id.surface_view);
                if (surfaceView != null) {
                    callSession.setVideoView(userId, surfaceView);
                }
            }
        }
        updateParticipantView(newUsers);
    }

    @Override
    public void onRemoteUserLeave(List<String> remoteUserIds) {
        super.onRemoteUserLeave(remoteUserIds);
        for (String userId : remoteUserIds) {
            View u = gridParticipants.findViewWithTag(userId);
            gridParticipants.removeView(u);
            targetUserIds.remove(userId);
        }

        if (gridParticipants.getChildCount() <= 1) {
            Toast.makeText(this, "通话结束", Toast.LENGTH_SHORT).show();
            finish();
        } else {
            gridParticipants.requestLayout();
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
        videoViewLayout.setTag(userInfo.getUserId());
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

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_SELECT_MEMBERS && resultCode == RESULT_OK) {
            ArrayList<String> newIds = data.getStringArrayListExtra(SELECTED_MEMBERS);
            updateParticipantView(newIds);
            targetUserIds.addAll(newIds);
            callSession.inviteUsers(newIds);
        }
    }
}
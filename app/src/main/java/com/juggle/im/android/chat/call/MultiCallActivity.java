package com.juggle.im.android.chat.call;

import static android.view.View.GONE;
import static android.view.View.VISIBLE;
import static com.juggle.im.android.chat.SelectMemberActivity.DISABLE_MEMBERS;
import static com.juggle.im.android.chat.SelectMemberActivity.GROUP_ID;
import static com.juggle.im.android.chat.SelectMemberActivity.SELECTED_MEMBERS;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.TypedValue;
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
import com.juggle.im.android.chat.SelectMemberActivity;
import com.juggle.im.android.utils.AvatarUtils;
import com.juggle.im.call.CallConst;
import com.juggle.im.model.UserInfo;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 多人音视频通话页面。
 */
public class MultiCallActivity extends BaseCallActivity {
    private static final int REQUEST_SELECT_MEMBERS = 1000;

    private GridLayout gridParticipants;
    private TextView tvCallTime;
    private TextView tvCallStatus;
    private TextView tvMicLabel;
    private TextView tvSpeakerLabel;
    private TextView tvCameraLabel;
    private TextView tvHangupLabel;
    private View btnHangup;
    private View btnInvite;
    private View btnAccept;
    private View btnMinimize;
    private View btnSwitchCamera;
    private View secondaryActionContainer;
    private View btnMic;
    private View btnSpeaker;
    private View btnCamera;
    private ImageView btnMicMute;
    private ImageView btnSpeakerMute;
    private ImageView ivCamera;

    private boolean isSpeakerMute;
    private boolean isMicMute;
    private boolean isCameraEnabled = true;
    private boolean isFrontCamera = true;
    private boolean timerStarted;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_multi_call);

        bindViews();
        initActions();

        String currentUserId = JIM.getInstance().getCurrentUserId();
        if (targetUserIds == null) {
            targetUserIds = new ArrayList<>();
        }
        targetUserIds.remove(currentUserId);

        updateParticipantView(Arrays.asList(currentUserId));
        updateParticipantView(targetUserIds);

        if (!connected && isIncoming(direction)) {
            playCallRing();
        }

        updateCallUiState();

        if (connected) {
            startPreview();
            ensureTimerStarted();
        }

        if (!connected && autoAccept && isIncoming(direction)) {
            acceptCall();
        }
    }

    /**
     * 发起多人呼叫（主叫场景）。
     */
    @Override
    protected void onStartCall() {
        startMultiCall(targetUserIds, isVideoCall ? CallConst.CallMediaType.VIDEO : CallConst.CallMediaType.VOICE);
    }

    /**
     * 通话建立后刷新网格和计时。
     */
    @Override
    public void onCallConnected() {
        super.onCallConnected();
        stopAndRelease();
        startPreview();
        ensureTimerStarted();
        updateCallUiState();
    }

    /**
     * 远端用户加入回调。
     *
     * @param remoteUserIds 新加入用户ID列表
     */
    @Override
    public void onRemoteUserJoin(List<String> remoteUserIds) {
        if (remoteUserIds == null || remoteUserIds.isEmpty()) {
            return;
        }

        ArrayList<String> newUsers = new ArrayList<>();
        for (String userId : remoteUserIds) {
            if (!targetUserIds.contains(userId)) {
                targetUserIds.add(userId);
                newUsers.add(userId);
            }
        }

        updateParticipantView(newUsers);
        bindRemoteVideoViews(remoteUserIds);
        updateCallUiState();
    }

    /**
     * 远端用户离开回调。
     *
     * @param remoteUserIds 离开用户ID列表
     */
    @Override
    public void onRemoteUserLeave(List<String> remoteUserIds) {
        if (remoteUserIds == null || remoteUserIds.isEmpty()) {
            return;
        }

        for (String userId : remoteUserIds) {
            View participantView = gridParticipants.findViewWithTag(userId);
            if (participantView != null) {
                gridParticipants.removeView(participantView);
            }
            targetUserIds.remove(userId);
        }

        if (gridParticipants.getChildCount() <= 1) {
            Toast.makeText(this, R.string.call_status_finished, Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        gridParticipants.requestLayout();
        updateCallUiState();
    }

    private void bindViews() {
        gridParticipants = findViewById(R.id.grid_participants);
        tvCallTime = findViewById(R.id.tv_call_time);
        tvCallStatus = findViewById(R.id.tv_call_status);
        btnInvite = findViewById(R.id.btn_invite);
        btnHangup = findViewById(R.id.btn_hangup);
        btnMicMute = findViewById(R.id.iv_mic);
        btnSpeakerMute = findViewById(R.id.iv_speaker);
        ivCamera = findViewById(R.id.iv_camera);
        tvMicLabel = findViewById(R.id.tv_mic_label);
        tvSpeakerLabel = findViewById(R.id.tv_speaker_label);
        tvCameraLabel = findViewById(R.id.tv_camera_label);
        tvHangupLabel = findViewById(R.id.tv_hangup_label);
        btnAccept = findViewById(R.id.btn_accept);
        btnMinimize = findViewById(R.id.btn_minimize);
        btnSwitchCamera = findViewById(R.id.btn_switch_camera);
        secondaryActionContainer = findViewById(R.id.secondary_action_container);
        btnMic = findViewById(R.id.btn_mic);
        btnSpeaker = findViewById(R.id.btn_speaker);
        btnCamera = findViewById(R.id.btn_camera);
    }

    private void initActions() {
        btnMinimize.setOnClickListener(v -> minimizeToFloating(true));

        btnInvite.setOnClickListener(v -> openInvitePage());

        btnHangup.setOnClickListener(v -> {
            hangupCall();
            finish();
        });

        btnMic.setOnClickListener(v -> toggleMic());
        btnSpeaker.setOnClickListener(v -> toggleSpeaker());
        btnCamera.setOnClickListener(v -> toggleCamera());
        btnSwitchCamera.setOnClickListener(v -> switchCamera());
        btnAccept.setOnClickListener(v -> acceptCall());
    }

    /**
     * 打开拉人页面。
     */
    private void openInvitePage() {
        if (!isGroupCall) {
            Toast.makeText(this, R.string.call_invite_only_group, Toast.LENGTH_SHORT).show();
            return;
        }
        Intent it = new Intent(this, SelectMemberActivity.class);
        it.putExtra(EXTRA_IS_VIDEO_CALL, isVideoCall);
        it.putExtra(GROUP_ID, conversationId);
        it.putStringArrayListExtra(DISABLE_MEMBERS, targetUserIds);
        startActivityForResult(it, REQUEST_SELECT_MEMBERS);
    }

    /**
     * 刷新多人通话整体状态栏。
     *
     * 简要描述：
     * 通话状态仅由 connected + direction + isVideoCall + isGroupCall 决定，避免“接听按钮/计时/底部操作区”互相覆盖。
     */
    private void updateCallUiState() {
        boolean incomingWaiting = !connected && isIncoming(direction);
        boolean outgoingWaiting = !connected && !incomingWaiting;
        boolean showSecondaryActions = connected;
        boolean showCameraAction = connected && isVideoCall;

        tvCallTime.setVisibility(connected ? VISIBLE : GONE);
        btnAccept.setVisibility(incomingWaiting ? VISIBLE : GONE);
        secondaryActionContainer.setVisibility(showSecondaryActions ? VISIBLE : GONE);
        btnCamera.setVisibility(showCameraAction ? VISIBLE : GONE);
        btnSwitchCamera.setVisibility(showCameraAction ? VISIBLE : GONE);
        btnInvite.setVisibility(connected && isGroupCall ? VISIBLE : GONE);
        tvHangupLabel.setText(incomingWaiting ? R.string.call_action_reject : R.string.call_action_cancel);

        if (outgoingWaiting) {
            tvCallStatus.setText(isVideoCall ? R.string.call_status_video_outgoing : R.string.call_status_voice_outgoing);
            return;
        }

        if (incomingWaiting) {
            tvCallStatus.setText(isVideoCall ? R.string.call_status_incoming_video : R.string.call_status_incoming_voice);
            return;
        }

        stopAndRelease();
        tvCallStatus.setText(getString(R.string.call_status_member_count, gridParticipants.getChildCount()));
        ensureTimerStarted();
    }

    private void ensureTimerStarted() {
        if (timerStarted) {
            return;
        }
        timerStarted = true;
        setupTimer(tvCallTime);
    }

    private void startPreview() {
        if (!isVideoCall || callSession == null) {
            return;
        }

        String currentUserId = JIM.getInstance().getCurrentUserId();
        View selfView = gridParticipants.findViewWithTag(currentUserId);
        if (selfView != null) {
            SurfaceView surfaceView = selfView.findViewById(R.id.surface_view);
            if (surfaceView != null) {
                callSession.startPreview(surfaceView);
            }
        }

        bindRemoteVideoViews(targetUserIds);
    }

    private void bindRemoteVideoViews(List<String> userIds) {
        if (!isVideoCall || callSession == null || userIds == null) {
            return;
        }

        String currentUserId = JIM.getInstance().getCurrentUserId();
        for (String userId : userIds) {
            if (TextUtils.equals(userId, currentUserId)) {
                continue;
            }
            View participantView = gridParticipants.findViewWithTag(userId);
            if (participantView == null) {
                continue;
            }
            SurfaceView surfaceView = participantView.findViewById(R.id.surface_view);
            if (surfaceView != null) {
                callSession.setVideoView(userId, surfaceView);
            }
        }
    }

    private void updateParticipantView(List<String> users) {
        if (users == null || users.isEmpty()) {
            return;
        }
        for (String userId : users) {
            if (TextUtils.isEmpty(userId)) {
                continue;
            }
            if (gridParticipants.findViewWithTag(userId) != null) {
                continue;
            }
            UserInfo userInfo = JIM.getInstance().getUserInfoManager().getUserInfo(userId);
            if (isVideoCall) {
                addVideoParticipant(userId, userInfo);
            } else {
                addAudioParticipant(userId, userInfo);
            }
        }
    }

    private void addAudioParticipant(String userId, UserInfo userInfo) {
        View memberView = LayoutInflater.from(this).inflate(R.layout.item_voice_participant, gridParticipants, false);
        ImageView imgAvatar = memberView.findViewById(R.id.img_member_avatar);
        TextView tvName = memberView.findViewById(R.id.tv_member_name);
        memberView.setTag(userId);

        String userName = userInfo == null ? getString(R.string.main_default_user_name) : userInfo.getUserName();
        String portrait = userInfo == null ? "" : userInfo.getPortrait();
        tvName.setText(userName);
        AvatarUtils.loadAvatar(imgAvatar, portrait, userName, userId);

        gridParticipants.addView(memberView, buildParticipantLayoutParams());
    }

    private void addVideoParticipant(String userId, UserInfo userInfo) {
        View memberView = LayoutInflater.from(this).inflate(R.layout.item_video_participant, gridParticipants, false);
        memberView.setTag(userId);

        TextView tvName = memberView.findViewById(R.id.tv_name);
        String userName = userInfo == null ? getString(R.string.main_default_user_name) : userInfo.getUserName();
        tvName.setText(userName);

        gridParticipants.addView(memberView, buildParticipantLayoutParams());
    }

    private GridLayout.LayoutParams buildParticipantLayoutParams() {
        int heightPx = (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                190,
                getResources().getDisplayMetrics());

        GridLayout.LayoutParams params = new GridLayout.LayoutParams();
        params.width = 0;
        params.height = heightPx;
        params.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f);
        params.rowSpec = GridLayout.spec(GridLayout.UNDEFINED);
        return params;
    }

    private void toggleMic() {
        if (callSession == null) {
            return;
        }
        callSession.muteMicrophone(!isMicMute);
        isMicMute = !isMicMute;
        btnMicMute.setImageResource(isMicMute ? R.drawable.icon_mic_off : R.drawable.icon_mic_on);
        tvMicLabel.setText(isMicMute ? R.string.call_action_mic_off : R.string.call_action_mic_on);
    }

    private void toggleSpeaker() {
        if (callSession == null) {
            return;
        }
        callSession.muteSpeaker(!isSpeakerMute);
        isSpeakerMute = !isSpeakerMute;
        btnSpeakerMute.setImageResource(isSpeakerMute ? R.drawable.icon_speaker_off : R.drawable.icon_speaker_on);
        tvSpeakerLabel.setText(isSpeakerMute ? R.string.call_action_speaker_off : R.string.call_action_speaker_on);
    }

    /**
     * 切换前后摄像头。
     *
     * tips: 多人视频页只在视频接通态暴露切换前后摄入口，避免等待态出现无效点击。
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
     * tips: 多人视频场景仅在已接通时展示该操作，避免主叫/被叫等待态出现无效按钮。
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

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQUEST_SELECT_MEMBERS || resultCode != RESULT_OK || data == null) {
            return;
        }
        ArrayList<String> newIds = data.getStringArrayListExtra(SELECTED_MEMBERS);
        if (newIds == null || newIds.isEmpty()) {
            return;
        }

        ArrayList<String> inviteIds = new ArrayList<>();
        for (String userId : newIds) {
            if (!targetUserIds.contains(userId)) {
                targetUserIds.add(userId);
                inviteIds.add(userId);
            }
        }

        updateParticipantView(inviteIds);
        if (!inviteIds.isEmpty() && callSession != null) {
            callSession.inviteUsers(inviteIds);
        }
    }
}

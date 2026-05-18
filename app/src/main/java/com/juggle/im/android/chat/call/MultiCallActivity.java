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
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 多人音视频通话页面。
 */
public class MultiCallActivity extends BaseCallActivity {
    private static final int REQUEST_SELECT_MEMBERS = 1000;
    private static final long INVITE_TIMEOUT_MS = 60_000L;

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
    private final Set<String> pendingInviteUserIds = new HashSet<>();
    private final Map<String, Runnable> inviteTimeoutTasks = new HashMap<>();
    private final Map<String, Long> inviteDeadlineAtMap = new HashMap<>();

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

        ArrayList<String> initialInvitedUserIds = new ArrayList<>();
        if (!connected && !isIncoming(direction)) {
            initialInvitedUserIds.addAll(targetUserIds);
            pendingInviteUserIds.addAll(initialInvitedUserIds);
            targetUserIds.clear();
        }

        updateParticipantView(Arrays.asList(currentUserId), false);
        updateParticipantView(targetUserIds, false);
        updateParticipantView(initialInvitedUserIds, true);
        for (String userId : initialInvitedUserIds) {
            startInviteTimeout(userId);
        }

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
     * 远端用户收到邀请回调。
     *
     * @param inviterUserId 发起邀请的用户ID
     * @param invitedUserIds 被邀请用户ID列表
     */
    @Override
    public void onRemoteUsersInvite(String inviterUserId, List<String> invitedUserIds) {
        if (invitedUserIds == null || invitedUserIds.isEmpty()) {
            return;
        }

        // tips: 当前页面已在 onActivityResult 中为本端主动邀请建立 pending 状态。
        // 这里作为 SDK 回调兜底，避免其他邀请入口或事件乱序导致页面缺少“邀请中”占位。
        for (String userId : invitedUserIds) {
            if (TextUtils.isEmpty(userId)
                    || TextUtils.equals(userId, JIM.getInstance().getCurrentUserId())
                    || targetUserIds.contains(userId)
                    || pendingInviteUserIds.contains(userId)) {
                continue;
            }
            pendingInviteUserIds.add(userId);
            updateParticipantView(Arrays.asList(userId), true);
            startInviteTimeout(userId);
        }
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
            if (TextUtils.isEmpty(userId) || TextUtils.equals(userId, JIM.getInstance().getCurrentUserId())) {
                continue;
            }
            cancelInviteTimeout(userId);
            pendingInviteUserIds.remove(userId);
            if (!targetUserIds.contains(userId)) {
                targetUserIds.add(userId);
                newUsers.add(userId);
            }
            updateParticipantView(Arrays.asList(userId), false);
        }

        if (!newUsers.isEmpty()) {
            bindRemoteVideoViews(newUsers);
        }
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
            cancelInviteTimeout(userId);
            pendingInviteUserIds.remove(userId);
            targetUserIds.remove(userId);
            removeParticipantView(userId);
        }

        if (shouldFinishCallAfterMemberLeave()) {
            Toast.makeText(this, R.string.call_status_finished, Toast.LENGTH_SHORT).show();
            hangupCall();
            finish();
            return;
        }
        gridParticipants.requestLayout();
        updateCallUiState();
    }

    @Override
    public void onCallFinished(CallConst.CallFinishReason callFinishReason) {
        syncConnectedParticipantsFromSession();
        if (shouldFinishCallBecauseOnlySelfConnected()) {
            CallUiStateStore.clearFloatingCallInfo();
            finish();
            return;
        }
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
        it.putStringArrayListExtra(DISABLE_MEMBERS, buildDisabledMembers());
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
        tvCallStatus.setText(getString(R.string.call_status_member_count, getConnectedParticipantCount()));
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
            if (participantView == null || pendingInviteUserIds.contains(userId)) {
                continue;
            }
            SurfaceView surfaceView = participantView.findViewById(R.id.surface_view);
            if (surfaceView != null) {
                callSession.setVideoView(userId, surfaceView);
            }
        }
    }

    private void updateParticipantView(List<String> users, boolean pendingInvite) {
        if (users == null || users.isEmpty()) {
            return;
        }
        for (String userId : users) {
            if (TextUtils.isEmpty(userId)) {
                continue;
            }
            View existingView = gridParticipants.findViewWithTag(userId);
            if (existingView != null) {
                bindParticipantState(existingView, userId, pendingInvite);
                continue;
            }
            UserInfo userInfo = JIM.getInstance().getUserInfoManager().getUserInfo(userId);
            if (isVideoCall) {
                addVideoParticipant(userId, userInfo, pendingInvite);
            } else {
                addAudioParticipant(userId, userInfo, pendingInvite);
            }
        }
    }

    private void addAudioParticipant(String userId, UserInfo userInfo, boolean pendingInvite) {
        View memberView = LayoutInflater.from(this).inflate(R.layout.item_voice_participant, gridParticipants, false);
        memberView.setTag(userId);
        bindAudioParticipant(memberView, userId, userInfo, pendingInvite);
        gridParticipants.addView(memberView, buildParticipantLayoutParams());
    }

    private void addVideoParticipant(String userId, UserInfo userInfo, boolean pendingInvite) {
        View memberView = LayoutInflater.from(this).inflate(R.layout.item_video_participant, gridParticipants, false);
        memberView.setTag(userId);
        bindVideoParticipant(memberView, userId, userInfo, pendingInvite);
        gridParticipants.addView(memberView, buildParticipantLayoutParams());
    }

    /**
     * 绑定语音成员卡片。
     *
     * tips：邀请中的成员先占位展示，但不计入正式人数，也不参与音视频流绑定。
     */
    private void bindAudioParticipant(View memberView, String userId, UserInfo userInfo, boolean pendingInvite) {
        ImageView imgAvatar = memberView.findViewById(R.id.img_member_avatar);
        TextView tvName = memberView.findViewById(R.id.tv_member_name);
        TextView tvStatus = memberView.findViewById(R.id.tv_member_status);

        String userName = userInfo == null ? getString(R.string.main_default_user_name) : userInfo.getUserName();
        String portrait = userInfo == null ? "" : userInfo.getPortrait();
        tvName.setText(userName);
        AvatarUtils.loadAvatar(imgAvatar, portrait, userName, userId);
        bindInviteStatus(tvStatus, userId, pendingInvite);
        memberView.setAlpha(pendingInvite ? 0.88f : 1f);
    }

    /**
     * 绑定视频成员卡片。
     *
     * tips：邀请中状态下显示遮罩提示，不绑定视频渲染 view，避免把未接通成员误当成已入会成员。
     */
    private void bindVideoParticipant(View memberView, String userId, UserInfo userInfo, boolean pendingInvite) {
        TextView tvName = memberView.findViewById(R.id.tv_name);
        TextView tvStatus = memberView.findViewById(R.id.tv_member_status);
        View overlay = memberView.findViewById(R.id.v_member_overlay);
        SurfaceView surfaceView = memberView.findViewById(R.id.surface_view);

        String userName = userInfo == null ? getString(R.string.main_default_user_name) : userInfo.getUserName();
        tvName.setText(userName);
        bindInviteStatus(tvStatus, userId, pendingInvite);
        overlay.setVisibility(pendingInvite ? VISIBLE : GONE);
        surfaceView.setVisibility(pendingInvite ? GONE : VISIBLE);
        memberView.setAlpha(pendingInvite ? 0.9f : 1f);
    }

    private void bindParticipantState(View memberView, String userId, boolean pendingInvite) {
        UserInfo userInfo = JIM.getInstance().getUserInfoManager().getUserInfo(userId);
        if (isVideoCall) {
            bindVideoParticipant(memberView, userId, userInfo, pendingInvite);
        } else {
            bindAudioParticipant(memberView, userId, userInfo, pendingInvite);
        }
    }

    /**
     * 绑定邀请中提示文案。
     *
     * tips：邀请中状态直接展示剩余秒数，降低“已经发起但界面没反馈”的感知成本。
     */
    private void bindInviteStatus(TextView tvStatus, String userId, boolean pendingInvite) {
        if (!pendingInvite) {
            tvStatus.setVisibility(GONE);
            return;
        }
        tvStatus.setVisibility(VISIBLE);
        tvStatus.setText(getString(R.string.call_status_inviting_countdown, getInviteRemainSeconds(userId)));
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
            if (TextUtils.isEmpty(userId)
                    || TextUtils.equals(userId, JIM.getInstance().getCurrentUserId())
                    || targetUserIds.contains(userId)
                    || pendingInviteUserIds.contains(userId)) {
                continue;
            }
            pendingInviteUserIds.add(userId);
            inviteIds.add(userId);
            updateParticipantView(Arrays.asList(userId), true);
            startInviteTimeout(userId);
        }

        updateCallUiState();
        if (!inviteIds.isEmpty() && callSession != null) {
            inviteUsers(inviteIds);
        }
    }

    private ArrayList<String> buildDisabledMembers() {
        ArrayList<String> disabledMembers = new ArrayList<>();
        String currentUserId = JIM.getInstance().getCurrentUserId();
        if (!TextUtils.isEmpty(currentUserId)) {
            disabledMembers.add(currentUserId);
        }
        for (String userId : targetUserIds) {
            if (!disabledMembers.contains(userId)) {
                disabledMembers.add(userId);
            }
        }
        for (String userId : pendingInviteUserIds) {
            if (!disabledMembers.contains(userId)) {
                disabledMembers.add(userId);
            }
        }
        return disabledMembers;
    }

    private void startInviteTimeout(String userId) {
        cancelInviteTimeout(userId);
        long deadlineAt = System.currentTimeMillis() + INVITE_TIMEOUT_MS;
        inviteDeadlineAtMap.put(userId, deadlineAt);
        Runnable timeoutTask = new Runnable() {
            @Override
            public void run() {
                if (!pendingInviteUserIds.contains(userId)) {
                    return;
                }
                long remainSeconds = getInviteRemainSeconds(userId);
                if (remainSeconds > 0) {
                    updateParticipantView(Arrays.asList(userId), true);
                    handler.postDelayed(this, 1000L);
                    return;
                }
                pendingInviteUserIds.remove(userId);
                inviteTimeoutTasks.remove(userId);
                inviteDeadlineAtMap.remove(userId);
                removeParticipantView(userId);
                Toast.makeText(MultiCallActivity.this, R.string.call_status_invite_timeout, Toast.LENGTH_SHORT).show();
                updateCallUiState();
            }
        };
        inviteTimeoutTasks.put(userId, timeoutTask);
        handler.post(timeoutTask);
    }

    private void cancelInviteTimeout(String userId) {
        Runnable timeoutTask = inviteTimeoutTasks.remove(userId);
        inviteDeadlineAtMap.remove(userId);
        if (timeoutTask != null) {
            handler.removeCallbacks(timeoutTask);
        }
    }

    private long getInviteRemainSeconds(String userId) {
        Long deadlineAt = inviteDeadlineAtMap.get(userId);
        if (deadlineAt == null) {
            return INVITE_TIMEOUT_MS / 1000;
        }
        long remainMillis = Math.max(0L, deadlineAt - System.currentTimeMillis());
        return (remainMillis + 999L) / 1000L;
    }

    private void removeParticipantView(String userId) {
        View participantView = gridParticipants.findViewWithTag(userId);
        if (participantView != null) {
            gridParticipants.removeView(participantView);
        }
    }

    private int getConnectedParticipantCount() {
        return 1 + (targetUserIds == null ? 0 : targetUserIds.size());
    }

    /**
     * 计算当前页面仍应展示的已接通远端成员。
     *
     * tips：多人通话的真实成员以 SDK 当前会话 members 为准。
     * 当主叫直接挂断而 onUsersLeave 事件未完整覆盖时，这里可以兜底清掉已不在会话中的卡片，避免残留冻结画面。
     */
    private ArrayList<String> resolveActiveRemoteUserIds() {
        ArrayList<String> activeUserIds = new ArrayList<>();
        if (callSession == null || callSession.getMembers() == null) {
            return activeUserIds;
        }
        String currentUserId = JIM.getInstance().getCurrentUserId();
        activeUserIds.addAll(callSession.getMembers().stream()
                .filter(member -> member != null && member.getUserInfo() != null)
                .map(member -> member.getUserInfo().getUserId())
                .filter(userId -> !TextUtils.isEmpty(userId)
                        && !TextUtils.equals(userId, currentUserId)
                        && !pendingInviteUserIds.contains(userId))
                .collect(java.util.stream.Collectors.toList()));
        return activeUserIds;
    }

    /**
     * 以当前会话成员快照同步页面中的正式成员。
     *
     * tips：这里不动邀请中成员，只校正已接通成员列表，避免 onCallFinish/onUsersLeave 乱序时留下无效视频卡片。
     */
    private void syncConnectedParticipantsFromSession() {
        ArrayList<String> activeRemoteUserIds = resolveActiveRemoteUserIds();
        ArrayList<String> staleUserIds = new ArrayList<>();
        for (String userId : new ArrayList<>(targetUserIds)) {
            if (!activeRemoteUserIds.contains(userId)) {
                staleUserIds.add(userId);
            }
        }
        for (String userId : staleUserIds) {
            targetUserIds.remove(userId);
            removeParticipantView(userId);
        }
        for (String userId : activeRemoteUserIds) {
            if (!targetUserIds.contains(userId)) {
                targetUserIds.add(userId);
                updateParticipantView(Arrays.asList(userId), false);
                bindRemoteVideoViews(Arrays.asList(userId));
            }
        }
    }

    /**
     * 判断当前会话是否已经只剩自己。
     *
     * tips：多人通话远端离开后，若正式成员只剩当前用户自己，应直接结束页面，避免停留在无远端的假连接态。
     */
    private boolean shouldFinishCallBecauseOnlySelfConnected() {
        syncConnectedParticipantsFromSession();
        return getConnectedParticipantCount() < 2;
    }

    /**
     * 判断成员离开后是否应直接结束通话。
     *
     * tips：只有在"正式已接通成员"已经少于 2 人，且不存在任何邀请中成员时，
     * 才认为当前多人通话已经无法继续；否则主叫仍可能在等待其他被叫接听。
     */
    private boolean shouldFinishCallAfterMemberLeave() {
        return getConnectedParticipantCount() < 2 && pendingInviteUserIds.isEmpty();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        for (Runnable timeoutTask : inviteTimeoutTasks.values()) {
            handler.removeCallbacks(timeoutTask);
        }
        inviteTimeoutTasks.clear();
        pendingInviteUserIds.clear();
    }
}

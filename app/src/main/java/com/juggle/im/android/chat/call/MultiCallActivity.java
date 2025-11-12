package com.juggle.im.android.chat.call;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.SurfaceView;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.Nullable;

import com.juggle.im.android.R;
import com.juggle.im.android.utils.AvatarUtils;
import com.juggle.im.model.UserInfo;

import java.util.ArrayList;
import java.util.List;

public class MultiCallActivity extends BaseCallActivity {
    private LinearLayout layoutMembers;
    private FrameLayout videoContainer;
    private List<UserInfo> userInfos = new ArrayList<>();
    private boolean isVideoCall;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_multi_call);

        layoutMembers = findViewById(R.id.layout_members);
        videoContainer = findViewById(R.id.video_container);
        Button btnInvite = findViewById(R.id.btn_invite);
        Button btnHangup = findViewById(R.id.btn_hangup);

        // 获取用户信息列表和通话类型
        userInfos = (List<UserInfo>) getIntent().getSerializableExtra("user_infos");
        isVideoCall = getIntent().getBooleanExtra("is_video_call", false);

        if (userInfos != null) {
            for (UserInfo userInfo : userInfos) {
                addMember(userInfo);
                if (isVideoCall) {
                    addVideoView(userInfo);
                }
            }
        }

        btnInvite.setOnClickListener(v -> inviteUsers(getUserIds()));
        btnHangup.setOnClickListener(v -> hangupCall());
    }

    private void addMember(UserInfo userInfo) {
        View memberView = LayoutInflater.from(this).inflate(R.layout.item_member, layoutMembers, false);
        ImageView imgAvatar = memberView.findViewById(R.id.img_member_avatar);
        TextView tvName = memberView.findViewById(R.id.tv_member_name);
        tvName.setText(userInfo.getUserName());
        AvatarUtils.loadAvatar(imgAvatar, userInfo.getPortrait(), userInfo.getUserName());
        layoutMembers.addView(memberView);
    }

    private void addVideoView(UserInfo userInfo) {
        SurfaceView videoView = new SurfaceView(this);
        videoContainer.addView(videoView);
        callSession.setVideoView(userInfo.getUserId(), videoView);
    }

    private List<String> getUserIds() {
        List<String> userIds = new ArrayList<>();
        for (UserInfo userInfo : userInfos) {
            userIds.add(userInfo.getUserId());
        }
        return userIds;
    }
}
package com.juggle.im.android.chat;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.bumptech.glide.Glide;
import com.juggle.im.android.R;
import com.juggle.im.android.app.LoginActivity;
import com.juggle.im.android.model.ConfigUtils;
import com.juggle.im.android.server.beans.UserInfoBean;
import com.juggle.im.android.server.beans.UserInfoRequest;
import com.juggle.im.android.server.http.ApiCallback;
import com.juggle.im.android.server.http.ServiceManager;

public class MyProfileFragment extends Fragment {

    private ImageView ivAvatar;
    private TextView tvNickname;
    private TextView tvUserId;
    private View llAvatarContainer;
    private View llNicknameContainer;
    private View btnLogout;

    private UserInfoBean currentUserInfo;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_my_profile, container, false);
        
        initViews(view);
        loadUserInfo();
        
        return view;
    }

    private void initViews(View view) {
        ivAvatar = view.findViewById(R.id.iv_avatar);
        tvNickname = view.findViewById(R.id.tv_nickname);
        tvUserId = view.findViewById(R.id.tv_user_id);
        llAvatarContainer = view.findViewById(R.id.ll_avatar_container);
        llNicknameContainer = view.findViewById(R.id.ll_nickname_container);
        btnLogout = view.findViewById(R.id.btn_logout);

        llAvatarContainer.setOnClickListener(v -> updateAvatar());
        llNicknameContainer.setOnClickListener(v -> updateNickname());
        btnLogout.setOnClickListener(v -> logout());
    }

    private void loadUserInfo() {
        if (TextUtils.isEmpty(ConfigUtils.myUserId)) {
            Toast.makeText(getContext(), "用户未登录", Toast.LENGTH_SHORT).show();
            return;
        }

        ServiceManager.getUserService().getUserInfo(ConfigUtils.myUserId, new ApiCallback<UserInfoBean>() {
            @Override
            public void onSuccess(UserInfoBean data) {
                if (getActivity() == null) return;
                
                getActivity().runOnUiThread(() -> {
                    currentUserInfo = data;
                    updateUI();
                });
            }

            @Override
            public void onError(int errorCode, String errorMsg) {
                if (getActivity() == null) return;
                
                getActivity().runOnUiThread(() -> 
                    Toast.makeText(getContext(), "获取用户信息失败: " + errorMsg, Toast.LENGTH_SHORT).show()
                );
            }
        });
    }

    private void updateUI() {
        if (currentUserInfo == null) return;

        // 加载头像
        if (!TextUtils.isEmpty(currentUserInfo.getAvatar())) {
            Glide.with(this)
                    .load(currentUserInfo.getAvatar())
                    .placeholder(R.drawable.default_avatar)
                    .into(ivAvatar);
        } else {
            ivAvatar.setImageResource(R.drawable.default_avatar);
        }

        // 显示昵称
        if (!TextUtils.isEmpty(currentUserInfo.getNickname())) {
            tvNickname.setText(currentUserInfo.getNickname());
        } else {
            tvNickname.setText("未设置");
        }

        // 显示用户ID
        tvUserId.setText(currentUserInfo.getUserId());
    }

    private void updateAvatar() {
        // 这里可以实现更换头像的功能，暂时显示提示
        Toast.makeText(getContext(), "更换头像功能待实现", Toast.LENGTH_SHORT).show();
    }

    private void updateNickname() {
        if (currentUserInfo == null) return;
        
        // 跳转到编辑昵称页面
        Intent intent = new Intent(getActivity(), EditNicknameActivity.class);
        intent.putExtra("current_nickname", currentUserInfo.getNickname());
        startActivityForResult(intent, 1001);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == 1001 && resultCode == getActivity().RESULT_OK && data != null) {
            String newNickname = data.getStringExtra("new_nickname");
            if (!TextUtils.isEmpty(newNickname)) {
                updateUserInfo(newNickname, null);
            }
        }
    }

    private void updateUserInfo(String nickname, String avatar) {
        UserInfoRequest request = new UserInfoRequest();
        request.setUserId(ConfigUtils.myUserId);
        if (nickname != null) {
            request.setNickname(nickname);
        }
        if (avatar != null) {
            request.setAvatar(avatar);
        }

        ServiceManager.getUserService().updateUserInfo(request, new ApiCallback<Void>() {
            @Override
            public void onSuccess(Void data) {
                if (getActivity() == null) return;
                
                getActivity().runOnUiThread(() -> {
                    Toast.makeText(getContext(), "更新成功", Toast.LENGTH_SHORT).show();
                    // 更新本地缓存
                    if (nickname != null) {
                        ConfigUtils.myName = nickname;
                        currentUserInfo.setNickname(nickname);
                    }
                    if (avatar != null) {
                        ConfigUtils.myAvatarUrl = avatar;
                        currentUserInfo.setAvatar(avatar);
                    }
                    updateUI();
                });
            }

            @Override
            public void onError(int errorCode, String errorMsg) {
                if (getActivity() == null) return;
                
                getActivity().runOnUiThread(() -> 
                    Toast.makeText(getContext(), "更新失败: " + errorMsg, Toast.LENGTH_SHORT).show()
                );
            }
        });
    }

    private void logout() {
        // 清除用户信息
        ConfigUtils.appToken = null;
        ConfigUtils.imToken = null;
        ConfigUtils.myUserId = null;
        ConfigUtils.myName = null;
        ConfigUtils.myAvatarUrl = null;

        // 跳转到登录页面
        Intent intent = new Intent(getActivity(), LoginActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
        if (getActivity() != null) {
            getActivity().finish();
        }
    }
}
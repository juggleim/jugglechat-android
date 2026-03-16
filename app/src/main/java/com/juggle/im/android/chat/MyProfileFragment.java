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
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;

import com.bumptech.glide.Glide;
import com.juggle.im.JIM;
import com.juggle.im.android.R;
import com.juggle.im.android.app.LoginActivity;
import com.juggle.im.android.auth.SessionRepository;
import com.juggle.im.android.model.ConfigUtils;
import com.juggle.im.android.server.beans.UserInfoBean;
import com.juggle.im.android.server.beans.UserInfoRequest;
import com.juggle.im.android.server.http.ApiCallback;
import com.juggle.im.android.server.http.ServiceManager;
import com.juggle.im.android.utils.ToastUtils;

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
        ServiceManager.getUserService().getUserInfo(JIM.getInstance().getCurrentUserId(), new ApiCallback<UserInfoBean>() {
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
                    Toast.makeText(getContext(),
                            getString(R.string.profile_error_load_failed, normalizeErrorMessage(errorMsg)),
                            Toast.LENGTH_SHORT).show()
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
            tvNickname.setText(R.string.profile_field_not_set);
        }

        // 显示用户ID
        tvUserId.setText(currentUserInfo.getUserId());
    }

    private void updateAvatar() {
        if (getContext() == null) {
            return;
        }

        final android.widget.EditText avatarInput = new android.widget.EditText(requireContext());
        avatarInput.setSingleLine(true);
        avatarInput.setHint(R.string.profile_avatar_dialog_hint);
        if (currentUserInfo != null && !TextUtils.isEmpty(currentUserInfo.getAvatar())) {
            avatarInput.setText(currentUserInfo.getAvatar());
        }

        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.profile_avatar_dialog_title)
                .setView(avatarInput)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                    String avatarUrl = safeTrim(avatarInput.getText().toString());
                    if (avatarUrl.isEmpty()) {
                        ToastUtils.show(requireContext(), R.string.profile_avatar_empty);
                        return;
                    }
                    updateUserInfo(null, avatarUrl);
                })
                .show();
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
        ProfileSnapshot snapshot = captureSnapshot();
        applyLocalProfilePatch(nickname, avatar);

        UserInfoRequest request = new UserInfoRequest();
        request.setUserId(JIM.getInstance().getCurrentUserId());
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
                
                getActivity().runOnUiThread(() ->
                        Toast.makeText(getContext(), R.string.profile_toast_update_success, Toast.LENGTH_SHORT).show());
            }

            @Override
            public void onError(int errorCode, String errorMsg) {
                if (getActivity() == null) return;
                
                getActivity().runOnUiThread(() -> {
                    rollbackProfilePatch(snapshot);
                    Toast.makeText(getContext(),
                            getString(R.string.profile_error_update_failed, normalizeErrorMessage(errorMsg)),
                            Toast.LENGTH_SHORT).show();
                });
            }
        });
    }

    private void applyLocalProfilePatch(String nickname, String avatar) {
        if (nickname != null) {
            ConfigUtils.myName = nickname;
            if (currentUserInfo != null) {
                currentUserInfo.setNickname(nickname);
            }
        }
        if (avatar != null) {
            ConfigUtils.myAvatarUrl = avatar;
            if (currentUserInfo != null) {
                currentUserInfo.setAvatar(avatar);
            }
        }
        updateUI();
    }

    private ProfileSnapshot captureSnapshot() {
        String nickname = currentUserInfo == null ? null : currentUserInfo.getNickname();
        String avatar = currentUserInfo == null ? null : currentUserInfo.getAvatar();
        return new ProfileSnapshot(nickname, avatar, ConfigUtils.myName, ConfigUtils.myAvatarUrl);
    }

    private void rollbackProfilePatch(ProfileSnapshot snapshot) {
        ConfigUtils.myName = snapshot.myName;
        ConfigUtils.myAvatarUrl = snapshot.myAvatarUrl;
        if (currentUserInfo != null) {
            currentUserInfo.setNickname(snapshot.nickname);
            currentUserInfo.setAvatar(snapshot.avatar);
        }
        updateUI();
    }

    private void logout() {
        // 清除用户信息
        ConfigUtils.appToken = null;
        ConfigUtils.imToken = null;
        ConfigUtils.myName = null;
        ConfigUtils.myAvatarUrl = null;
        SessionRepository.create(requireContext()).clearSession();
        JIM.getInstance().getConnectionManager().disconnect(false);

        // 跳转到登录页面
        Intent intent = new Intent(getActivity(), LoginActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
        if (getActivity() != null) {
            getActivity().finish();
        }
    }

    private String safeTrim(String value) {
        return value == null ? "" : value.trim();
    }

    private String normalizeErrorMessage(String value) {
        String trimmed = safeTrim(value);
        return trimmed.isEmpty() ? getString(R.string.operation_failed) : trimmed;
    }

    private static final class ProfileSnapshot {
        private final String nickname;
        private final String avatar;
        private final String myName;
        private final String myAvatarUrl;

        private ProfileSnapshot(String nickname, String avatar, String myName, String myAvatarUrl) {
            this.nickname = nickname;
            this.avatar = avatar;
            this.myName = myName;
            this.myAvatarUrl = myAvatarUrl;
        }
    }
}

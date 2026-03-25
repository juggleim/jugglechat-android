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
import com.juggle.im.android.app.MyQRCodeActivity;
import com.juggle.im.android.app.PersonalSettingsActivity;
import com.juggle.im.android.app.GeneralSettingsActivity;
import com.juggle.im.android.app.FavoritesActivity;
import com.juggle.im.android.app.UserAgreementActivity;
import com.juggle.im.android.app.PrivacyPolicyActivity;
import com.juggle.im.android.app.FeedbackActivity;
import com.juggle.im.android.auth.SessionRepository;
import com.juggle.im.android.auth.UserProfileStore;
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
    private ImageView ivQrcode;
    private View rowPersonalSettings;
    private View rowGeneralSettings;
    private View rowFavorites;
    private View rowUserAgreement;
    private View rowPrivacyPolicy;
    private View rowFeedback;
    private View rowVersion;

    private UserInfoBean currentUserInfo;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_my_profile, container, false);

        initViews(view);
        loadUserInfo();

        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
        loadUserInfo();
    }

    private void initViews(View view) {
        ivAvatar = view.findViewById(R.id.iv_avatar);
        tvNickname = view.findViewById(R.id.tv_nickname);
        tvUserId = view.findViewById(R.id.tv_user_id);
        ivQrcode = view.findViewById(R.id.iv_qrcode);
        rowPersonalSettings = view.findViewById(R.id.row_personal_settings);
        rowGeneralSettings = view.findViewById(R.id.row_general_settings);
        rowFavorites = view.findViewById(R.id.row_favorites);
        rowUserAgreement = view.findViewById(R.id.row_user_agreement);
        rowPrivacyPolicy = view.findViewById(R.id.row_privacy_policy);
        rowFeedback = view.findViewById(R.id.row_feedback);
        rowVersion = view.findViewById(R.id.row_version);

        // 设置各个行项的标题和图标
        setupSettingRow(rowPersonalSettings, R.drawable.ic_setting_profile, "个人设置");
        setupSettingRow(rowGeneralSettings, R.drawable.ic_setting_general, "通用设置");
        setupSettingRow(rowFavorites, R.drawable.ic_setting_favorites, "我的收藏");
        setupSettingRow(rowUserAgreement, R.drawable.ic_setting_user_agreement, "用户协议");
        setupSettingRow(rowPrivacyPolicy, R.drawable.ic_setting_privacy, "隐私协议");
        setupSettingRow(rowFeedback, R.drawable.ic_setting_feedback, "意见反馈");
        setupSettingRow(rowVersion, R.drawable.ic_setting_about, "版本信息", "2.5.1");

        // 设置点击监听
        ivAvatar.setOnClickListener(v -> navigateToPersonalSettings());
        ivQrcode.setOnClickListener(v -> showMyQRCode());
        rowPersonalSettings.setOnClickListener(v -> navigateToPersonalSettings());
        rowGeneralSettings.setOnClickListener(v -> navigateToGeneralSettings());
        rowFavorites.setOnClickListener(v -> navigateToFavorites());
        rowUserAgreement.setOnClickListener(v -> navigateToUserAgreement());
        rowPrivacyPolicy.setOnClickListener(v -> navigateToPrivacyPolicy());
        rowFeedback.setOnClickListener(v -> navigateToFeedback());
        rowVersion.setOnClickListener(v -> Toast.makeText(getContext(), "版本信息功能开发中", Toast.LENGTH_SHORT).show());
    }

    private void setupSettingRow(View row, int iconRes, String title) {
        setupSettingRow(row, iconRes, title, null, true);
    }

    private void setupSettingRow(View row, int iconRes, String title, String subtitle) {
        setupSettingRow(row, iconRes, title, subtitle, true);
    }

    private void setupSettingRow(View row, int iconRes, String title, String subtitle, boolean showArrow) {
        ImageView icon = row.findViewById(R.id.iv_row_icon);
        TextView titleView = row.findViewById(R.id.tv_row_title);
        TextView subtitleView = row.findViewById(R.id.tv_row_subtitle);
        ImageView arrowView = row.findViewById(R.id.iv_row_arrow);

        if (icon != null) {
            icon.setImageResource(iconRes);
        }
        if (titleView != null) {
            titleView.setText(title);
        }
        if (subtitleView != null) {
            if (!TextUtils.isEmpty(subtitle)) {
                subtitleView.setText(subtitle);
                subtitleView.setVisibility(View.VISIBLE);
            } else {
                subtitleView.setVisibility(View.GONE);
            }
        }
        if (arrowView != null) {
            arrowView.setVisibility(showArrow ? View.VISIBLE : View.GONE);
        }
    }

    private void loadUserInfo() {
        ServiceManager.getUserService().getUserInfo(JIM.getInstance().getCurrentUserId(), new ApiCallback<UserInfoBean>() {
            @Override
            public void onSuccess(UserInfoBean data) {
                if (getActivity() == null) return;

                getActivity().runOnUiThread(() -> {
                    currentUserInfo = data;
                    if (data != null) {
                        ConfigUtils.myName = data.getNickname();
                        ConfigUtils.myAvatarUrl = data.getAvatar();
                        if (getContext() != null) {
                            UserProfileStore.save(getContext(),
                                    data.getUserId(),
                                    data.getNickname(),
                                    data.getAvatar());
                        }
                    }
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
                    .placeholder(R.drawable.icon_default_avatar)
                    .into(ivAvatar);
        } else {
            ivAvatar.setImageResource(R.drawable.icon_default_avatar);
        }

        // 显示昵称
        if (!TextUtils.isEmpty(currentUserInfo.getNickname())) {
            tvNickname.setText(currentUserInfo.getNickname());
        } else {
            tvNickname.setText(R.string.profile_field_not_set);
        }

        // 显示用户ID
        tvUserId.setText("账号：@" + currentUserInfo.getUserId());
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
        String userId = currentUserInfo == null ? JIM.getInstance().getCurrentUserId() : currentUserInfo.getUserId();
        String cachedName = currentUserInfo == null ? ConfigUtils.myName : currentUserInfo.getNickname();
        String cachedAvatar = currentUserInfo == null ? ConfigUtils.myAvatarUrl : currentUserInfo.getAvatar();
        UserProfileStore.save(requireContext(), userId, cachedName, cachedAvatar);
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
        new AlertDialog.Builder(requireContext())
                .setTitle("退出登录")
                .setMessage("确定要退出登录吗？")
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                    // 清除用户信息
                    ConfigUtils.appToken = null;
                    ConfigUtils.imToken = null;
                    ConfigUtils.myName = null;
                    ConfigUtils.myAvatarUrl = null;
                    SessionRepository.create(requireContext()).clearSession();
                    UserProfileStore.clear(requireContext());
                    JIM.getInstance().getConnectionManager().disconnect(false);

                    // 跳转到登录页面
                    Intent intent = new Intent(getActivity(), LoginActivity.class);
                    intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(intent);
                    if (getActivity() != null) {
                        getActivity().finish();
                    }
                })
                .show();
    }

    private String safeTrim(String value) {
        return value == null ? "" : value.trim();
    }

    private String normalizeErrorMessage(String value) {
        String trimmed = safeTrim(value);
        return trimmed.isEmpty() ? getString(R.string.operation_failed) : trimmed;
    }

    // Navigation methods for menu items
    private void showMyQRCode() {
        Intent intent = new Intent(getActivity(), MyQRCodeActivity.class);
        startActivity(intent);
    }

    private void navigateToPersonalSettings() {
        Intent intent = new Intent(getActivity(), PersonalSettingsActivity.class);
        startActivity(intent);
    }

    private void navigateToGeneralSettings() {
        Intent intent = new Intent(getActivity(), GeneralSettingsActivity.class);
        startActivity(intent);
    }

    private void navigateToFavorites() {
        Intent intent = new Intent(getActivity(), FavoritesActivity.class);
        startActivity(intent);
    }

    private void navigateToUserAgreement() {
        Intent intent = new Intent(getActivity(), UserAgreementActivity.class);
        startActivity(intent);
    }

    private void navigateToPrivacyPolicy() {
        Intent intent = new Intent(getActivity(), PrivacyPolicyActivity.class);
        startActivity(intent);
    }

    private void navigateToFeedback() {
        Intent intent = new Intent(getActivity(), FeedbackActivity.class);
        startActivity(intent);
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

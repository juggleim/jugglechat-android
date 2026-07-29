package com.juggle.im.android.chat;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Build;
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
import com.juggle.im.android.utils.AvatarUtils;
import com.juggle.im.android.utils.ToastUtils;
import com.juggle.im.android.widget.AppConfirmDialog;
import com.juggle.im.android.utils.LogUtils;

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
        bindCachedUserInfo();

        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
        bindCachedUserInfo();
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
        setupSettingRow(rowPersonalSettings, R.drawable.ic_setting_profile, getString(R.string.me_personal_settings));
        setupSettingRow(rowGeneralSettings, R.drawable.ic_setting_general, getString(R.string.me_general_settings));
        setupSettingRow(rowFavorites, R.drawable.ic_setting_favorites, getString(R.string.me_favorites));
        setupSettingRow(rowUserAgreement, R.drawable.ic_setting_user_agreement, getString(R.string.me_user_agreement));
        setupSettingRow(rowPrivacyPolicy, R.drawable.ic_setting_privacy, getString(R.string.me_privacy_policy));
        setupSettingRow(rowFeedback, R.drawable.ic_setting_feedback, getString(R.string.me_feedback));
        setupSettingRow(rowVersion, R.drawable.ic_setting_about, getString(R.string.me_version),
                getAppVersionName(), false);

        // 设置点击监听
        ivAvatar.setOnClickListener(v -> navigateToPersonalSettings());
        ivQrcode.setOnClickListener(v -> showMyQRCode());
        rowPersonalSettings.setOnClickListener(v -> navigateToPersonalSettings());
        rowGeneralSettings.setOnClickListener(v -> navigateToGeneralSettings());
        rowFavorites.setOnClickListener(v -> navigateToFavorites());
        rowUserAgreement.setOnClickListener(v -> navigateToUserAgreement());
        rowPrivacyPolicy.setOnClickListener(v -> navigateToPrivacyPolicy());
        rowFeedback.setOnClickListener(v -> navigateToFeedback());
    }

    private String getAppVersionName() {
        Context context = requireContext();
        PackageManager packageManager = context.getPackageManager();
        try {
            // TIPS：读取已安装包元数据，避免 Gradle 升版后界面仍显示历史硬编码版本。
            PackageInfo packageInfo;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                packageInfo = packageManager.getPackageInfo(
                        context.getPackageName(),
                        PackageManager.PackageInfoFlags.of(0));
            } else {
                packageInfo = packageManager.getPackageInfo(context.getPackageName(), 0);
            }
            return TextUtils.isEmpty(packageInfo.versionName)
                    ? getString(R.string.me_version_unknown)
                    : packageInfo.versionName;
        } catch (PackageManager.NameNotFoundException e) {
            LogUtils.e("MyProfileFragment", "-", "profile", "readVersion",
                    "fail", e.getMessage());
            return getString(R.string.me_version_unknown);
        }
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
                if (getActivity() == null || data == null) return;

                getActivity().runOnUiThread(() -> {
                    ConfigUtils.myName = data.getNickname();
                    ConfigUtils.myAvatarUrl = data.getAvatar();
                    if (isSameProfile(data)) {
                        return;
                    }
                    currentUserInfo = data;
                    if (getContext() != null) {
                        UserProfileStore.save(getContext(),
                                data.getUserId(),
                                data.getNickname(),
                                data.getAvatar());
                    }
                    updateUI();
                });
            }

            @Override
            public void onError(int errorCode, String errorMsg) {
                LogUtils.serverError("profile", "loadUserInfo", errorCode, errorMsg);
                if (getActivity() == null || currentUserInfo != null) return;

                getActivity().runOnUiThread(() ->
                    Toast.makeText(getContext(),
                            R.string.profile_error_load_failed,
                            Toast.LENGTH_SHORT).show()
                );
            }
        });
    }

    private void bindCachedUserInfo() {
        Context context = getContext();
        if (context == null) {
            return;
        }
        String currentUserId = JIM.getInstance().getCurrentUserId();
        UserProfileStore.UserProfile cachedProfile = UserProfileStore.read(context);
        if (cachedProfile.isEmpty()
                || !TextUtils.equals(currentUserId, cachedProfile.getUserId())) {
            return;
        }

        UserInfoBean cachedUserInfo = new UserInfoBean();
        cachedUserInfo.setUserId(cachedProfile.getUserId());
        cachedUserInfo.setNickname(cachedProfile.getNickname());
        cachedUserInfo.setAvatar(cachedProfile.getAvatar());
        if (isSameProfile(cachedUserInfo)) {
            return;
        }
        currentUserInfo = cachedUserInfo;
        ConfigUtils.myName = cachedUserInfo.getNickname();
        ConfigUtils.myAvatarUrl = cachedUserInfo.getAvatar();
        updateUI();
    }

    private boolean isSameProfile(UserInfoBean userInfo) {
        return currentUserInfo != null
                && TextUtils.equals(currentUserInfo.getUserId(), userInfo.getUserId())
                && TextUtils.equals(currentUserInfo.getNickname(), userInfo.getNickname())
                && TextUtils.equals(currentUserInfo.getAvatar(), userInfo.getAvatar());
    }

    private void updateUI() {
        if (currentUserInfo == null) return;

        // 加载头像
        AvatarUtils.loadAvatar(
                ivAvatar,
                currentUserInfo.getAvatar(),
                currentUserInfo.getNickname(),
                currentUserInfo.getUserId());

        // 显示昵称
        if (!TextUtils.isEmpty(currentUserInfo.getNickname())) {
            tvNickname.setText(currentUserInfo.getNickname());
        } else {
            tvNickname.setText(R.string.profile_field_not_set);
        }

        // 显示用户ID
        tvUserId.setText(getString(R.string.me_user_id, currentUserInfo.getUserId()));
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
                LogUtils.serverError("profile", "updateUserInfo", errorCode, errorMsg);
                if (getActivity() == null) return;

                getActivity().runOnUiThread(() -> {
                    rollbackProfilePatch(snapshot);
                    Toast.makeText(getContext(),
                            R.string.profile_error_update_failed,
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
        AppConfirmDialog.builder(requireContext())
                .setTitle(getString(R.string.me_logout))
                .setMessage(getString(R.string.me_logout_confirm))
                .setNegativeText(getString(R.string.txt_cancel))
                .setPositiveText(getString(R.string.create_group_confirm))
                .setOnPositiveClick(() -> {
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
        Intent intent = FeedbackActivity.intentForFeedback(requireContext());
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

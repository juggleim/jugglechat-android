package com.juggle.im.android.app;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.view.Window;
import android.view.LayoutInflater;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import com.bumptech.glide.Glide;
import com.bumptech.glide.request.RequestOptions;
import com.juggle.im.JIM;
import com.juggle.im.JIMConst;
import com.juggle.im.android.R;
import com.juggle.im.android.auth.AccountStore;
import com.juggle.im.android.auth.SessionRepository;
import com.juggle.im.android.auth.UserProfileStore;
import com.juggle.im.android.component.AbsAppActivity;
import com.juggle.im.android.model.ConfigUtils;
import com.juggle.im.android.server.beans.UserInfoBean;
import com.juggle.im.android.server.beans.UserInfoRequest;
import com.juggle.im.android.server.http.ApiCallback;
import com.juggle.im.android.server.http.ServiceManager;
import com.juggle.im.android.utils.AvatarUtils;
import com.juggle.im.android.widget.AppConfirmDialog;

import java.io.File;
import com.juggle.im.android.utils.LogUtils;

/**
 * 个人设置页面
 */
public class PersonalSettingsActivity extends AbsAppActivity {
    private static final int REQ_PICK_AVATAR = 2201;
    private static final int REQ_ADD_ACCOUNT = 2202;

    private ImageView avatarView;
    private ProgressBar avatarProgress;
    private EditText etName;
    private EditText etAccount;
    private TextView saveView;

    private View rowBindEmail;
    private View rowUpdatePwd;
    private View rowCurrentUser;
    private View rowAddAccount;
    private LinearLayout accountRowsContainer;
    private AccountStore accountStore;

    private String currentUserId;
    private String originalName;
    private String originalAccount;
    private String avatarUrl;
    private String originalAvatarUrl;

    private boolean isSaving;
    private boolean isUploadingAvatar;
    private boolean hasCachedProfile;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_personal_settings);

        avatarView = findViewById(R.id.iv_avatar);
        avatarProgress = findViewById(R.id.progress_avatar);
        etName = findViewById(R.id.et_name);
        etAccount = findViewById(R.id.et_account);
        saveView = findViewById(R.id.tv_save);

        rowBindEmail = findViewById(R.id.row_bind_email);
        rowUpdatePwd = findViewById(R.id.row_update_pwd);
        rowCurrentUser = findViewById(R.id.row_current_user);
        rowAddAccount = findViewById(R.id.row_add_account);
        accountRowsContainer = findViewById(R.id.account_rows_container);
        accountStore = new AccountStore(this);

        ((TextView) findViewById(R.id.tv_title)).setText(R.string.personal_settings_title);
        findViewById(R.id.iv_back).setOnClickListener(v -> finish());

        saveView.setOnClickListener(v -> onSave());
        findViewById(R.id.tv_set_avatar).setOnClickListener(v -> openAvatarPicker());
        avatarView.setOnClickListener(v -> openAvatarPicker());

        setupRow(rowBindEmail, R.drawable.ic_display_name, getString(R.string.personal_bind_email), getString(R.string.personal_not_set), true);
        setupRow(rowUpdatePwd, R.drawable.ic_setting_privacy, getString(R.string.personal_update_password), "", true);
        setupRow(rowAddAccount, R.drawable.ic_add, getString(R.string.personal_add_account), "", true);

        rowBindEmail.setOnClickListener(v -> Toast.makeText(this, R.string.personal_bind_email_todo, Toast.LENGTH_SHORT).show());
        rowUpdatePwd.setOnClickListener(v -> startActivity(new Intent(this, UpdatePasswordActivity.class)));
        rowAddAccount.setOnClickListener(v ->
                startActivityForResult(LoginActivity.createAddAccountIntent(this), REQ_ADD_ACCOUNT));

        findViewById(R.id.btn_logout).setOnClickListener(v -> confirmLogout());

        currentUserId = safeText(JIM.getInstance().getCurrentUserId(), "");
        bindCachedUserInfo();
        loadUserInfo();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshAccountRows();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_ADD_ACCOUNT && resultCode == Activity.RESULT_OK) {
            refreshAccountRows();
            Toast.makeText(this, R.string.personal_add_account_success, Toast.LENGTH_SHORT).show();
            return;
        }
        if (requestCode != REQ_PICK_AVATAR || resultCode != Activity.RESULT_OK || data == null) {
            return;
        }
        String selectedPath = data.getStringExtra(AvatarPickerActivity.EXTRA_SELECTED_PATH);
        if (TextUtils.isEmpty(selectedPath)) {
            return;
        }
        uploadAvatar(selectedPath);
    }

    private void loadUserInfo() {
        ServiceManager.getUserService().getUserInfo(currentUserId, new ApiCallback<UserInfoBean>() {
            @Override
            public void onSuccess(UserInfoBean data) {
                if (data == null) {
                    return;
                }
                applyRemoteUserInfo(data);
            }

            @Override
            public void onError(int code, String message) {
                LogUtils.serverError("profile", "loadUserInfo", code, message);
                if (!hasCachedProfile) {
                    Toast.makeText(PersonalSettingsActivity.this,
                            R.string.personal_load_failed,
                            Toast.LENGTH_SHORT).show();
                }
            }
        });
    }

    private void bindCachedUserInfo() {
        UserProfileStore.UserProfile cachedProfile = UserProfileStore.read(this);
        if (cachedProfile.isEmpty()
                || TextUtils.isEmpty(currentUserId)
                || !TextUtils.equals(currentUserId, cachedProfile.getUserId())) {
            return;
        }

        originalName = safeText(cachedProfile.getNickname(), cachedProfile.getUserId());
        originalAccount = safeText(cachedProfile.getUserId(), currentUserId);
        originalAvatarUrl = safeText(cachedProfile.getAvatar(), "");
        avatarUrl = originalAvatarUrl;
        ConfigUtils.myName = originalName;
        ConfigUtils.myAvatarUrl = originalAvatarUrl;
        bindUserData(originalName, originalAccount, avatarUrl);
        hasCachedProfile = true;
        saveCurrentAccountIfPossible(originalName, originalAvatarUrl);
    }

    private void applyRemoteUserInfo(UserInfoBean data) {
        String remoteAccount = safeText(data.getUserId(), currentUserId);
        String remoteName = safeText(data.getNickname(), remoteAccount);
        String remoteAvatar = safeText(data.getAvatar(), "");
        boolean profileChanged = !TextUtils.equals(remoteName, originalName)
                || !TextUtils.equals(remoteAccount, originalAccount)
                || !TextUtils.equals(remoteAvatar, originalAvatarUrl);
        if (!profileChanged) {
            return;
        }

        // TIPS：网络响应可能晚于用户编辑，只同步未被用户修改的字段，避免覆盖尚未保存的输入。
        boolean nameEdited = originalName != null
                && !TextUtils.equals(etName.getText().toString().trim(), originalName);
        boolean accountEdited = originalAccount != null
                && !TextUtils.equals(etAccount.getText().toString().trim(), originalAccount);
        boolean avatarEdited = !TextUtils.equals(
                safeText(avatarUrl, ""),
                safeText(originalAvatarUrl, ""));

        originalName = remoteName;
        originalAccount = remoteAccount;
        originalAvatarUrl = remoteAvatar;
        ConfigUtils.myName = remoteName;
        ConfigUtils.myAvatarUrl = remoteAvatar;
        UserProfileStore.save(this, remoteAccount, remoteName, remoteAvatar);
        hasCachedProfile = true;
        saveCurrentAccountIfPossible(remoteName, remoteAvatar);

        if (!nameEdited) {
            etName.setText(remoteName);
        }
        if (!accountEdited) {
            etAccount.setText(remoteAccount);
        }
        if (!avatarEdited) {
            avatarUrl = remoteAvatar;
            bindAvatar(etName.getText().toString().trim(), avatarUrl);
        }
        refreshCurrentUserRow(
                safeText(etName.getText().toString(), remoteName),
                safeText(avatarUrl, ""));
    }

    private void bindUserData(String name, String account, String avatar) {
        etName.setText(name);
        etAccount.setText(account);
        bindAvatar(name, avatar);
        refreshCurrentUserRow(name, avatar);
        refreshAccountRows();
    }

    private void bindAvatar(String name, String avatar) {
        // TIPS：无网络头像时也必须走 AvatarUtils，保证与“我的”页面使用同一首字母和底色规则。
        AvatarUtils.loadAvatar(avatarView, avatar, name, currentUserId);
    }

    /**
     * 刷新“当前用户”条目，统一处理头像、标题与副标题显示。
     *
     * @param name   用户名称
     * @param avatar 用户头像地址
     */
    private void refreshCurrentUserRow(String name, String avatar) {
        ImageView icon = rowCurrentUser.findViewById(R.id.iv_row_icon);
        TextView title = rowCurrentUser.findViewById(R.id.tv_row_title);
        TextView subtitle = rowCurrentUser.findViewById(R.id.tv_row_subtitle);
        ImageView arrow = rowCurrentUser.findViewById(R.id.iv_row_arrow);

        title.setText(safeText(name, currentUserId));
        subtitle.setVisibility(View.VISIBLE);
        subtitle.setText(R.string.personal_current_user);
        subtitle.setTextColor(ContextCompat.getColor(this, R.color.design_color_success));
        arrow.setVisibility(View.GONE);
        AvatarUtils.loadAvatar(icon, avatar, name, currentUserId);
    }

    private void refreshAccountRows() {
        if (accountRowsContainer == null || accountStore == null) {
            return;
        }
        while (accountRowsContainer.getChildCount() > 1) {
            accountRowsContainer.removeViewAt(accountRowsContainer.getChildCount() - 1);
        }

        java.util.List<AccountStore.AccountRecord> accounts =
                accountStore.getAccounts(ConfigUtils.organizationId);
        for (AccountStore.AccountRecord account : accounts) {
            if (TextUtils.equals(currentUserId, account.getUserId())) {
                continue;
            }
            View row = LayoutInflater.from(this)
                    .inflate(R.layout.item_group_setting_row, accountRowsContainer, false);
            bindAccountRow(row, account);
            accountRowsContainer.addView(row);
        }
        rowAddAccount.setVisibility(
                accounts.size() < AccountStore.MAX_ACCOUNT_COUNT ? View.VISIBLE : View.GONE);
    }

    private void bindAccountRow(@NonNull View row,
                                @NonNull AccountStore.AccountRecord account) {
        ImageView icon = row.findViewById(R.id.iv_row_icon);
        TextView title = row.findViewById(R.id.tv_row_title);
        TextView subtitle = row.findViewById(R.id.tv_row_subtitle);
        ImageView arrow = row.findViewById(R.id.iv_row_arrow);

        title.setText(safeText(account.getNickname(), account.getUserId()));
        subtitle.setVisibility(View.VISIBLE);
        subtitle.setText(account.getUserId());
        arrow.setVisibility(View.VISIBLE);
        AvatarUtils.loadAvatar(
                icon,
                account.getAvatar(),
                account.getNickname(),
                account.getUserId());
        row.setOnClickListener(v -> switchAccount(account));
    }

    private void switchAccount(@NonNull AccountStore.AccountRecord account) {
        if (TextUtils.equals(currentUserId, account.getUserId())) {
            return;
        }
        if (!account.isSessionValid(System.currentTimeMillis())) {
            Toast.makeText(this, R.string.personal_account_expired, Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            // TIPS：先断开旧连接，再替换全部会话与资料，避免界面进入新用户后旧连接仍回调数据。
            JIM.getInstance().getConnectionManager().disconnect(false);
            SessionRepository.create(this).saveSession(
                    account.getAppToken(),
                    account.getImToken(),
                    account.getExpireAtMillis());
            UserProfileStore.save(
                    this,
                    account.getUserId(),
                    account.getNickname(),
                    account.getAvatar());
            ConfigUtils.appToken = account.getAppToken();
            ConfigUtils.imToken = account.getImToken();
            ConfigUtils.myName = account.getNickname();
            ConfigUtils.myAvatarUrl = account.getAvatar();

            Intent intent = new Intent(this, MainActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
            finish();
        } catch (RuntimeException exception) {
            LogUtils.e("PersonalSettingsActivity", "-", "auth", "switchAccount",
                    "fail", exception.getMessage());
            Toast.makeText(this, R.string.personal_account_switch_failed, Toast.LENGTH_SHORT).show();
        }
    }

    private void saveCurrentAccountIfPossible(String name, String avatar) {
        SessionRepository.SessionState session =
                SessionRepository.create(this).getValidSession();
        if (session == null || TextUtils.isEmpty(currentUserId)) {
            return;
        }
        accountStore.upsert(new AccountStore.AccountRecord(
                ConfigUtils.organizationId,
                currentUserId,
                name,
                avatar,
                session.getAppToken(),
                session.getImToken(),
                session.getExpireAtMillis()));
        refreshAccountRows();
    }

    private void openAvatarPicker() {
        Intent intent = new Intent(this, AvatarPickerActivity.class);
        startActivityForResult(intent, REQ_PICK_AVATAR);
    }

    private void uploadAvatar(String path) {
        isUploadingAvatar = true;
        avatarProgress.setVisibility(View.VISIBLE);
        Glide.with(this)
                .load(new File(path))
                .apply(RequestOptions.circleCropTransform())
                .into(avatarView);

        JIM.getInstance().getMessageManager().uploadImage(path, new JIMConst.IResultCallback<String>() {
            @Override
            public void onSuccess(String url) {
                runOnUiThread(() -> {
                    isUploadingAvatar = false;
                    avatarProgress.setVisibility(View.GONE);
                    if (TextUtils.isEmpty(url)) {
                        Toast.makeText(PersonalSettingsActivity.this, R.string.personal_avatar_upload_failed, Toast.LENGTH_SHORT).show();
                        bindUserData(etName.getText().toString().trim(), etAccount.getText().toString().trim(), originalAvatarUrl);
                        return;
                    }
                    avatarUrl = url;
                    AvatarUtils.loadAvatar(avatarView, avatarUrl, etName.getText().toString().trim(), currentUserId);
                    refreshCurrentUserRow(etName.getText().toString().trim(), avatarUrl);
                });
            }

            @Override
            public void onError(int code) {
                runOnUiThread(() -> {
                    isUploadingAvatar = false;
                    avatarProgress.setVisibility(View.GONE);
                    Toast.makeText(PersonalSettingsActivity.this, R.string.personal_avatar_upload_failed, Toast.LENGTH_SHORT).show();
                    bindUserData(etName.getText().toString().trim(), etAccount.getText().toString().trim(), originalAvatarUrl);
                });
            }
        });
    }

    private void onSave() {
        if (isSaving) {
            return;
        }
        if (isUploadingAvatar) {
            Toast.makeText(this, R.string.personal_avatar_uploading, Toast.LENGTH_SHORT).show();
            return;
        }

        String name = safeText(etName.getText().toString(), "").trim();
        String account = safeText(etAccount.getText().toString(), "").trim();
        if (TextUtils.isEmpty(name)) {
            Toast.makeText(this, R.string.personal_name_empty, Toast.LENGTH_SHORT).show();
            return;
        }
        if (TextUtils.isEmpty(account)) {
            Toast.makeText(this, R.string.personal_account_empty, Toast.LENGTH_SHORT).show();
            return;
        }

        boolean needUpdateProfile = !TextUtils.equals(name, originalName) || !TextUtils.equals(safeText(avatarUrl, ""), safeText(originalAvatarUrl, ""));
        boolean needUpdateAccount = !TextUtils.equals(account, originalAccount);

        if (!needUpdateProfile && !needUpdateAccount) {
            Toast.makeText(this, R.string.personal_no_change, Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        setSaving(true);

        Runnable accountStep = () -> {
            if (!needUpdateAccount) {
                onSaveSuccess(name, account, safeText(avatarUrl, ""));
                return;
            }
            ServiceManager.getUserService().setAccount(account, new ApiCallback<Void>() {
                @Override
                public void onSuccess(Void data) {
                    onSaveSuccess(name, account, safeText(avatarUrl, ""));
                }

                @Override
                public void onError(int code, String message) {
                    LogUtils.serverError("profile", "setAccount", code, message);
                    onSaveFailed(getString(R.string.personal_account_save_failed));
                }
            });
        };

        if (!needUpdateProfile) {
            accountStep.run();
            return;
        }

        UserInfoRequest request = new UserInfoRequest();
        request.setUserId(currentUserId);
        request.setNickname(name);
        request.setAvatar(safeText(avatarUrl, ""));
        ServiceManager.getUserService().updateUserInfo(request, new ApiCallback<Void>() {
            @Override
            public void onSuccess(Void data) {
                accountStep.run();
            }

            @Override
            public void onError(int code, String message) {
                LogUtils.serverError("profile", "updateUserInfo", code, message);
                onSaveFailed(getString(R.string.personal_profile_save_failed));
            }
        });
    }

    private void onSaveSuccess(String name, String account, String avatar) {
        setSaving(false);
        originalName = name;
        originalAccount = account;
        originalAvatarUrl = avatar;
        avatarUrl = avatar;
        ConfigUtils.myName = name;
        ConfigUtils.myAvatarUrl = avatar;
        UserProfileStore.save(this, currentUserId, name, avatar);
        accountStore.updateProfile(
                ConfigUtils.organizationId,
                currentUserId,
                name,
                avatar);
        Toast.makeText(this, R.string.personal_save_success, Toast.LENGTH_SHORT).show();
        finish();
    }

    private void onSaveFailed(String message) {
        setSaving(false);
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    private void setSaving(boolean saving) {
        isSaving = saving;
        saveView.setEnabled(!saving);
        saveView.setAlpha(saving ? 0.5f : 1f);
    }

    private void confirmLogout() {
        AppConfirmDialog.builder(this)
                .setTitle(getString(R.string.me_logout))
                .setMessage(getString(R.string.me_logout_confirm))
                .setNegativeText(getString(R.string.txt_cancel))
                .setPositiveText(getString(R.string.me_logout_confirm_ok))
                .setOnPositiveClick(() -> {
                    ConfigUtils.appToken = null;
                    ConfigUtils.imToken = null;
                    ConfigUtils.myName = null;
                    ConfigUtils.myAvatarUrl = null;
                    SessionRepository.create(this).clearSession();
                    UserProfileStore.clear(this);
                    JIM.getInstance().getConnectionManager().disconnect(false);

                    Intent intent = new Intent(this, LoginActivity.class);
                    intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(intent);
                    finish();
                })
                .show();
    }

    private void setupRow(View row, int iconRes, String title, String subtitle, boolean showArrow) {
        ImageView icon = row.findViewById(R.id.iv_row_icon);
        TextView titleView = row.findViewById(R.id.tv_row_title);
        TextView subtitleView = row.findViewById(R.id.tv_row_subtitle);
        ImageView arrowView = row.findViewById(R.id.iv_row_arrow);

        if (iconRes > 0) {
            icon.setImageResource(iconRes);
        }
        titleView.setText(title);

        if (TextUtils.isEmpty(subtitle)) {
            subtitleView.setVisibility(View.GONE);
        } else {
            subtitleView.setVisibility(View.VISIBLE);
            subtitleView.setText(subtitle);
        }

        arrowView.setVisibility(showArrow ? View.VISIBLE : View.GONE);
    }

    private String safeText(String value, String fallback) {
        if (value == null) {
            return fallback;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? fallback : trimmed;
    }
}

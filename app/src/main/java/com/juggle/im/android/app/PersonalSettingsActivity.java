package com.juggle.im.android.app;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.view.Window;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.view.WindowInsetsControllerCompat;

import com.bumptech.glide.Glide;
import com.bumptech.glide.request.RequestOptions;
import com.juggle.im.JIM;
import com.juggle.im.JIMConst;
import com.juggle.im.android.R;
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

/**
 * 个人设置页面
 */
public class PersonalSettingsActivity extends AbsAppActivity {
    private static final int REQ_PICK_AVATAR = 2201;

    private ImageView avatarView;
    private ProgressBar avatarProgress;
    private EditText etName;
    private EditText etAccount;
    private TextView saveView;

    private View rowBindEmail;
    private View rowUpdatePwd;
    private View rowCurrentUser;
    private View rowAddAccount;

    private String currentUserId;
    private String originalName;
    private String originalAccount;
    private String avatarUrl;
    private String originalAvatarUrl;

    private boolean isSaving;
    private boolean isUploadingAvatar;

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

        ((TextView) findViewById(R.id.tv_title)).setText("个人设置");
        findViewById(R.id.iv_back).setOnClickListener(v -> finish());

        saveView.setOnClickListener(v -> onSave());
        findViewById(R.id.tv_set_avatar).setOnClickListener(v -> openAvatarPicker());
        avatarView.setOnClickListener(v -> openAvatarPicker());

        setupRow(rowBindEmail, R.drawable.ic_display_name, "绑定邮箱", "未设置", true);
        setupRow(rowUpdatePwd, R.drawable.ic_setting_privacy, "修改密码", "", true);
//        setupRow(rowCurrentUser, R.drawable.ic_display_name, "当前用户", "xxxxx", true);
        setupRow(rowAddAccount, R.drawable.ic_add, "添加账号", "", true);

        rowBindEmail.setOnClickListener(v -> Toast.makeText(this, "绑定邮箱功能开发中", Toast.LENGTH_SHORT).show());
        rowUpdatePwd.setOnClickListener(v -> startActivity(new Intent(this, UpdatePasswordActivity.class)));
        rowAddAccount.setOnClickListener(v -> Toast.makeText(this, "添加账号功能开发中", Toast.LENGTH_SHORT).show());

        findViewById(R.id.btn_logout).setOnClickListener(v -> confirmLogout());

        currentUserId = JIM.getInstance().getCurrentUserId();
        loadUserInfo();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
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
                originalName = safeText(data.getNickname(), data.getUserId());
                originalAccount = safeText(data.getUserId(), "");
                originalAvatarUrl = safeText(data.getAvatar(), "");
                avatarUrl = originalAvatarUrl;
                UserProfileStore.save(PersonalSettingsActivity.this, originalAccount, originalName, originalAvatarUrl);
                bindUserData(originalName, originalAccount, avatarUrl);
            }

            @Override
            public void onError(int code, String message) {
                Toast.makeText(PersonalSettingsActivity.this, "获取用户信息失败：" + safeText(message, "未知错误"), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void bindUserData(String name, String account, String avatar) {
        etName.setText(name);
        etAccount.setText(account);
        if (!TextUtils.isEmpty(avatar)) {
            AvatarUtils.loadAvatar(avatarView, avatar, name, currentUserId);
        } else {
            avatarView.setImageResource(R.drawable.icon_default_avatar);
        }
        // 同步“当前用户”行，确保页面首次加载时头像与文案都与当前资料一致。
        refreshCurrentUserRow(name, avatar);
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

        // “当前用户”固定为标题，名称展示在副标题，避免语义混淆。
        title.setText("当前用户");
        subtitle.setVisibility(View.VISIBLE);
        subtitle.setText(safeText(name, currentUserId));
        arrow.setVisibility(View.GONE);
        AvatarUtils.loadAvatar(icon, avatar, name, currentUserId);
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
                        Toast.makeText(PersonalSettingsActivity.this, "头像上传失败", Toast.LENGTH_SHORT).show();
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
                    Toast.makeText(PersonalSettingsActivity.this, "头像上传失败", Toast.LENGTH_SHORT).show();
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
            Toast.makeText(this, "头像上传中，请稍候", Toast.LENGTH_SHORT).show();
            return;
        }

        String name = safeText(etName.getText().toString(), "").trim();
        String account = safeText(etAccount.getText().toString(), "").trim();
        if (TextUtils.isEmpty(name)) {
            Toast.makeText(this, "名称不能为空", Toast.LENGTH_SHORT).show();
            return;
        }
        if (TextUtils.isEmpty(account)) {
            Toast.makeText(this, "账号不能为空", Toast.LENGTH_SHORT).show();
            return;
        }

        boolean needUpdateProfile = !TextUtils.equals(name, originalName) || !TextUtils.equals(safeText(avatarUrl, ""), safeText(originalAvatarUrl, ""));
        boolean needUpdateAccount = !TextUtils.equals(account, originalAccount);

        if (!needUpdateProfile && !needUpdateAccount) {
            Toast.makeText(this, "资料未变化", Toast.LENGTH_SHORT).show();
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
                    onSaveFailed("账号保存失败：" + safeText(message, "未知错误"));
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
                onSaveFailed("资料保存失败：" + safeText(message, "未知错误"));
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
        UserProfileStore.save(this, account, name, avatar);
        Toast.makeText(this, "保存成功", Toast.LENGTH_SHORT).show();
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
                .setTitle("退出登录")
                .setMessage("确定要退出登录吗？")
                .setNegativeText(getString(R.string.txt_cancel))
                .setPositiveText(getString(R.string.create_group_confirm))
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

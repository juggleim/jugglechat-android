package com.juggle.im.android.app;

import android.os.Bundle;
import android.text.Editable;
import android.text.InputType;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.content.ContextCompat;

import com.juggle.im.JIM;
import com.juggle.im.android.R;
import com.juggle.im.android.auth.HashUtils;
import com.juggle.im.android.component.AbsAppActivity;
import com.juggle.im.android.server.http.ApiCallback;
import com.juggle.im.android.server.http.ServiceManager;

/**
 * 修改密码页面。
 */
public class UpdatePasswordActivity extends AbsAppActivity {

    private EditText oldPasswordInput;
    private EditText newPasswordInput;
    private EditText confirmPasswordInput;
    private Button confirmButton;
    private ProgressBar confirmProgress;
    private ImageView oldPasswordToggle;
    private ImageView newPasswordToggle;
    private ImageView confirmPasswordToggle;

    private boolean isSubmitting;
    private boolean oldPasswordVisible;
    private boolean newPasswordVisible;
    private boolean confirmPasswordVisible;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_update_password);
        initViews();
        setupListeners();
        updateSubmitState();
    }

    private void initViews() {
        ((TextView) findViewById(R.id.tv_title)).setText("修改密码");
        findViewById(R.id.iv_back).setOnClickListener(v -> finish());

        oldPasswordInput = findViewById(R.id.oldPasswordInput);
        newPasswordInput = findViewById(R.id.newPasswordInput);
        confirmPasswordInput = findViewById(R.id.confirmPasswordInput);
        confirmButton = findViewById(R.id.confirmButton);
        confirmProgress = findViewById(R.id.confirmProgress);
        oldPasswordToggle = findViewById(R.id.oldPasswordToggle);
        newPasswordToggle = findViewById(R.id.newPasswordToggle);
        confirmPasswordToggle = findViewById(R.id.confirmPasswordToggle);
    }

    private void setupListeners() {
        TextWatcher watcher = new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                updateSubmitState();
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        };

        oldPasswordInput.addTextChangedListener(watcher);
        newPasswordInput.addTextChangedListener(watcher);
        confirmPasswordInput.addTextChangedListener(watcher);

        oldPasswordToggle.setOnClickListener(v -> {
            oldPasswordVisible = !oldPasswordVisible;
            applyPasswordVisibility(oldPasswordInput, oldPasswordToggle, oldPasswordVisible);
        });
        newPasswordToggle.setOnClickListener(v -> {
            newPasswordVisible = !newPasswordVisible;
            applyPasswordVisibility(newPasswordInput, newPasswordToggle, newPasswordVisible);
        });
        confirmPasswordToggle.setOnClickListener(v -> {
            confirmPasswordVisible = !confirmPasswordVisible;
            applyPasswordVisibility(confirmPasswordInput, confirmPasswordToggle, confirmPasswordVisible);
        });
        confirmButton.setOnClickListener(v -> submit());
    }

    /**
     * 提交修改密码请求。
     */
    private void submit() {
        if (isSubmitting) {
            return;
        }

        String oldPassword = safeTrim(oldPasswordInput.getText().toString());
        String newPassword = safeTrim(newPasswordInput.getText().toString());
        String confirmPassword = safeTrim(confirmPasswordInput.getText().toString());

        if (TextUtils.isEmpty(oldPassword) || TextUtils.isEmpty(newPassword) || TextUtils.isEmpty(confirmPassword)) {
            Toast.makeText(this, "请填写完整密码信息", Toast.LENGTH_SHORT).show();
            return;
        }
        if (newPassword.length() < 6) {
            Toast.makeText(this, "密码长度最少 6 位", Toast.LENGTH_SHORT).show();
            return;
        }
        if (!TextUtils.equals(newPassword, confirmPassword)) {
            Toast.makeText(this, "两次输入的密码不一致", Toast.LENGTH_SHORT).show();
            return;
        }
        if (TextUtils.equals(oldPassword, newPassword)) {
            Toast.makeText(this, "新密码不能与原密码一致", Toast.LENGTH_SHORT).show();
            return;
        }

        setSubmitting(true);
        ServiceManager.getUserService().updatePassword(
                JIM.getInstance().getCurrentUserId(),
                HashUtils.md5(oldPassword),
                HashUtils.md5(newPassword),
                new ApiCallback<Void>() {
                    @Override
                    public void onSuccess(Void data) {
                        setSubmitting(false);
                        Toast.makeText(UpdatePasswordActivity.this, "密码修改成功", Toast.LENGTH_SHORT).show();
                        finish();
                    }

                    @Override
                    public void onError(int code, String message) {
                        setSubmitting(false);
                        Toast.makeText(UpdatePasswordActivity.this,
                                "密码修改失败：" + normalizeErrorMessage(message),
                                Toast.LENGTH_SHORT).show();
                    }
                });
    }

    /**
     * 更新提交按钮与加载态。
     */
    private void updateSubmitState() {
        boolean canSubmit = !TextUtils.isEmpty(safeTrim(oldPasswordInput.getText().toString()))
                && !TextUtils.isEmpty(safeTrim(newPasswordInput.getText().toString()))
                && !TextUtils.isEmpty(safeTrim(confirmPasswordInput.getText().toString()));

        if (isSubmitting) {
            confirmButton.setEnabled(false);
            confirmButton.setText("提交中");
            confirmButton.setBackgroundResource(R.drawable.bg_auth_button_loading);
            confirmButton.setTextColor(ContextCompat.getColor(this, R.color.white));
            confirmProgress.setVisibility(View.VISIBLE);
            return;
        }

        confirmProgress.setVisibility(View.GONE);
        confirmButton.setText("确定");
        if (canSubmit) {
            confirmButton.setEnabled(true);
            confirmButton.setBackgroundResource(R.drawable.bg_auth_button_enabled);
            confirmButton.setTextColor(ContextCompat.getColor(this, R.color.white));
        } else {
            confirmButton.setEnabled(false);
            confirmButton.setBackgroundResource(R.drawable.bg_auth_button_disabled);
            confirmButton.setTextColor(ContextCompat.getColor(this, R.color.auth_button_disabled_text));
        }
    }

    /**
     * 设置提交中状态。
     *
     * @param submitting 是否提交中
     */
    private void setSubmitting(boolean submitting) {
        isSubmitting = submitting;
        updateSubmitState();
    }

    /**
     * 切换密码可见性。
     *
     * @param editText    密码输入框
     * @param toggleView  切换按钮
     * @param visible     是否明文显示
     */
    private void applyPasswordVisibility(EditText editText, ImageView toggleView, boolean visible) {
        int selection = editText.getSelectionEnd();
        if (visible) {
            editText.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
            toggleView.setImageResource(R.drawable.ic_auth_password_show);
            toggleView.setContentDescription(getString(R.string.auth_cd_hide_password));
        } else {
            editText.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
            toggleView.setImageResource(R.drawable.ic_auth_password_hide);
            toggleView.setContentDescription(getString(R.string.auth_cd_show_password));
        }
        editText.setSelection(Math.max(selection, 0));
    }

    private String safeTrim(String value) {
        return value == null ? "" : value.trim();
    }

    private String normalizeErrorMessage(String message) {
        String trimmed = safeTrim(message);
        return trimmed.isEmpty() ? getString(R.string.operation_failed) : trimmed;
    }
}

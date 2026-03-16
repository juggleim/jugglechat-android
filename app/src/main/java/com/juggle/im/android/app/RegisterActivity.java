package com.juggle.im.android.app;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.text.Editable;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextPaint;
import android.text.TextWatcher;
import android.text.method.LinkMovementMethod;
import android.text.style.ClickableSpan;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import com.juggle.im.android.R;
import com.juggle.im.android.auth.AuthInputValidator;
import com.juggle.im.android.auth.AuthRequestFactory;
import com.juggle.im.android.server.beans.LoginResult;
import com.juggle.im.android.server.http.ApiCallback;
import com.juggle.im.android.server.http.ServiceManager;
import com.juggle.im.android.utils.ToastUtils;

public class RegisterActivity extends AppCompatActivity {
    private EditText registerAccountInput;
    private EditText registerPasswordInput;
    private EditText registerConfirmPasswordInput;

    private Button registerButton;
    private ProgressBar registerProgress;

    private TextView registerPrivacyText;

    private boolean isRegistering = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setupSystemBars();
        setContentView(R.layout.activity_register);

        initViews();
        setupListeners();
        setupAgreementLinks();
        updateRegisterButtonState();
    }

    private void setupSystemBars() {
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        getWindow().setStatusBarColor(Color.TRANSPARENT);
        WindowInsetsControllerCompat controller =
                new WindowInsetsControllerCompat(getWindow(), getWindow().getDecorView());
        controller.setAppearanceLightStatusBars(true);
    }

    private void initViews() {
        registerAccountInput = findViewById(R.id.registerAccountInput);
        registerPasswordInput = findViewById(R.id.registerPasswordInput);
        registerConfirmPasswordInput = findViewById(R.id.registerConfirmPasswordInput);
        registerButton = findViewById(R.id.registerButton);
        registerProgress = findViewById(R.id.registerProgress);
        registerPrivacyText = findViewById(R.id.registerPrivacyText);
    }

    private void setupListeners() {
        View backContainer = findViewById(R.id.backContainer);
        backContainer.setOnClickListener(v -> finish());

        registerButton.setOnClickListener(v -> handleRegister());

        TextWatcher watcher = new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                updateRegisterButtonState();
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        };

        registerAccountInput.addTextChangedListener(watcher);
        registerPasswordInput.addTextChangedListener(watcher);
        registerConfirmPasswordInput.addTextChangedListener(watcher);
    }

    private void setupAgreementLinks() {
        String agreementText = getString(R.string.auth_user_agreement);
        String privacyPolicyText = getString(R.string.auth_privacy_policy);
        String raw = getString(
                R.string.auth_privacy_text_full,
                getString(R.string.auth_privacy_prefix),
                agreementText,
                getString(R.string.auth_privacy_and),
                privacyPolicyText);

        SpannableString spannable = new SpannableString(raw);
        int agreementStart = raw.indexOf(agreementText);
        int agreementEnd = agreementStart + agreementText.length();
        int privacyStart = raw.indexOf(privacyPolicyText);
        int privacyEnd = privacyStart + privacyPolicyText.length();

        if (agreementStart >= 0) {
            spannable.setSpan(new LinkSpan(() -> WebViewPageActivity.navToUseragreement(RegisterActivity.this)),
                    agreementStart, agreementEnd,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        if (privacyStart >= 0) {
            spannable.setSpan(new LinkSpan(() -> WebViewPageActivity.navToPrivace(RegisterActivity.this)),
                    privacyStart, privacyEnd,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }

        registerPrivacyText.setText(spannable);
        registerPrivacyText.setMovementMethod(LinkMovementMethod.getInstance());
        registerPrivacyText.setHighlightColor(Color.TRANSPARENT);
    }

    private void handleRegister() {
        if (isRegistering) {
            return;
        }

        String account = safeTrim(registerAccountInput.getText().toString());
        String password = safeTrim(registerPasswordInput.getText().toString());
        String confirmPassword = safeTrim(registerConfirmPasswordInput.getText().toString());

        int errorResId = AuthInputValidator.validateRegisterErrorResId(account, password, confirmPassword);
        if (errorResId != 0) {
            ToastUtils.show(this, errorResId);
            return;
        }

        setRegistering(true);
        ServiceManager.getUserService().register(
                AuthRequestFactory.buildRegisterRequest(account, password),
                new ApiCallback<LoginResult>() {
                    @Override
                    public void onSuccess(LoginResult data) {
                        setRegistering(false);
                        ToastUtils.show(RegisterActivity.this, R.string.auth_toast_register_success);
                        finish();
                    }

                    @Override
                    public void onError(int code, String message) {
                        setRegistering(false);
                        ToastUtils.show(RegisterActivity.this,
                                getString(R.string.auth_error_register_failed, normalizeErrorMessage(message)));
                    }
                });
    }

    private void setRegistering(boolean registering) {
        isRegistering = registering;
        updateRegisterButtonState();
    }

    private void updateRegisterButtonState() {
        int errorResId = AuthInputValidator.validateRegisterErrorResId(
                registerAccountInput.getText().toString(),
                registerPasswordInput.getText().toString(),
                registerConfirmPasswordInput.getText().toString());
        boolean canSubmit = errorResId == 0;

        if (isRegistering) {
            registerButton.setEnabled(false);
            registerButton.setText(R.string.auth_button_register_loading);
            registerButton.setBackgroundResource(R.drawable.bg_auth_button_loading);
            registerButton.setTextColor(ContextCompat.getColor(this, R.color.white));
            registerProgress.setVisibility(View.VISIBLE);
            return;
        }

        registerProgress.setVisibility(View.GONE);
        registerButton.setText(R.string.auth_button_register);
        if (canSubmit) {
            registerButton.setEnabled(true);
            registerButton.setBackgroundResource(R.drawable.bg_auth_button_enabled);
            registerButton.setTextColor(ContextCompat.getColor(this, R.color.white));
        } else {
            registerButton.setEnabled(false);
            registerButton.setBackgroundResource(R.drawable.bg_auth_button_disabled);
            registerButton.setTextColor(ContextCompat.getColor(this, R.color.auth_button_disabled_text));
        }
    }

    private String safeTrim(String value) {
        return value == null ? "" : value.trim();
    }

    private String normalizeErrorMessage(String message) {
        String trimmed = safeTrim(message);
        return trimmed.isEmpty() ? getString(R.string.operation_failed) : trimmed;
    }

    private final class LinkSpan extends ClickableSpan {
        private final Runnable clickAction;

        private LinkSpan(Runnable clickAction) {
            this.clickAction = clickAction;
        }

        @Override
        public void onClick(@NonNull View widget) {
            clickAction.run();
        }

        @Override
        public void updateDrawState(@NonNull TextPaint ds) {
            super.updateDrawState(ds);
            ds.setUnderlineText(false);
            ds.setColor(ContextCompat.getColor(RegisterActivity.this, R.color.auth_primary));
        }
    }
}

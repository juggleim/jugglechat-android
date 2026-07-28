package com.juggle.im.android.app;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputType;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextPaint;
import android.text.TextWatcher;
import android.text.method.LinkMovementMethod;
import android.text.style.ClickableSpan;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import com.juggle.im.android.utils.LogUtils;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import com.juggle.im.android.R;
import com.juggle.im.android.auth.AuthInputValidator;
import com.juggle.im.android.auth.AuthRequestFactory;
import com.juggle.im.android.auth.SessionRepository;
import com.juggle.im.android.auth.UserProfileStore;
import com.juggle.im.android.component.AbsAppActivity;
import com.juggle.im.android.core.JIMChatCore;
import com.juggle.im.android.model.ConfigUtils;
import com.juggle.im.android.server.beans.CodeRequest;
import com.juggle.im.android.server.beans.LoginResult;
import com.juggle.im.android.server.http.ApiCallback;
import com.juggle.im.android.server.http.ServiceManager;
import com.juggle.im.android.utils.ToastUtils;

public class LoginActivity extends AbsAppActivity {
    private static final String TAG = "LoginActivity";
    private static final long TOKEN_VALIDITY_DURATION = 2L * 24 * 60 * 60 * 1000;

    private TextView accountTab;
    private TextView emailTab;
    private View accountIndicator;
    private View emailIndicator;
    private LinearLayout accountForm;
    private LinearLayout emailForm;

    private EditText accountInput;
    private EditText passwordInput;
    private EditText emailInput;
    private EditText emailCodeInput;

    private TextView getCodeText;
    private TextView registerText;
    private TextView privacyText;
    private View passwordToggle;

    private Button loginButton;
    private ProgressBar loginProgress;

    private boolean isEmailMode = false;
    private boolean isLoading = false;
    private boolean isSendingCode = false;
    private boolean isPasswordVisible = false;
    private SessionRepository sessionRepository;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setupSystemBars();
        setContentView(R.layout.activity_login);
        sessionRepository = SessionRepository.create(this);

        initViews();
        setupListeners();
        setupAgreementLinks();
        applyMode(false);
        updateLoginButtonState();
    }

    private void setupSystemBars() {
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        getWindow().setStatusBarColor(Color.TRANSPARENT);
        WindowInsetsControllerCompat controller =
                new WindowInsetsControllerCompat(getWindow(), getWindow().getDecorView());
        controller.setAppearanceLightStatusBars(true);
    }

    private void initViews() {
        accountTab = findViewById(R.id.accountTab);
        emailTab = findViewById(R.id.emailTab);
        accountIndicator = findViewById(R.id.accountIndicator);
        emailIndicator = findViewById(R.id.emailIndicator);
        accountForm = findViewById(R.id.accountForm);
        emailForm = findViewById(R.id.emailForm);

        accountInput = findViewById(R.id.accountInput);
        passwordInput = findViewById(R.id.passwordInput);
        emailInput = findViewById(R.id.emailInput);
        emailCodeInput = findViewById(R.id.emailCodeInput);

        getCodeText = findViewById(R.id.getCodeText);
        registerText = findViewById(R.id.registerText);
        privacyText = findViewById(R.id.privacyText);
        passwordToggle = findViewById(R.id.passwordToggle);

        loginButton = findViewById(R.id.loginButton);
        loginProgress = findViewById(R.id.loginProgress);
    }

    private void setupListeners() {
        accountTab.setOnClickListener(v -> applyMode(false));
        emailTab.setOnClickListener(v -> applyMode(true));

        loginButton.setOnClickListener(v -> handleLogin());
        registerText.setOnClickListener(v -> switchToRegister());
        getCodeText.setOnClickListener(v -> requestEmailCode());
        passwordToggle.setOnClickListener(v -> togglePasswordVisibility());

        View orgIdContainer = findViewById(R.id.orgIdContainer);
        orgIdContainer.setOnClickListener(v -> ToastUtils.show(this, R.string.auth_org_id_not_available));

        TextWatcher watcher = new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                updateLoginButtonState();
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        };

        accountInput.addTextChangedListener(watcher);
        passwordInput.addTextChangedListener(watcher);
        emailInput.addTextChangedListener(watcher);
        emailCodeInput.addTextChangedListener(watcher);
    }

    private void togglePasswordVisibility() {
        isPasswordVisible = !isPasswordVisible;
        int selection = passwordInput.getSelectionEnd();
        android.graphics.Typeface currentTypeface = passwordInput.getTypeface();
        int inputType = isPasswordVisible
                ? InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
                : InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD;
        passwordInput.setInputType(inputType);
        passwordInput.setTypeface(currentTypeface);
        int targetSelection = Math.min(Math.max(selection, 0), passwordInput.length());
        passwordInput.setSelection(targetSelection);

        if (passwordToggle instanceof android.widget.ImageView) {
            android.widget.ImageView toggleIcon = (android.widget.ImageView) passwordToggle;
            if (isPasswordVisible) {
                toggleIcon.setImageResource(R.drawable.ic_auth_password_show);
                toggleIcon.setContentDescription(getString(R.string.auth_cd_hide_password));
            } else {
                toggleIcon.setImageResource(R.drawable.ic_auth_password_hide);
                toggleIcon.setContentDescription(getString(R.string.auth_cd_show_password));
            }
        }
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
            spannable.setSpan(new LinkSpan(() -> WebViewPageActivity.navToUseragreement(LoginActivity.this)),
                    agreementStart, agreementEnd,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        if (privacyStart >= 0) {
            spannable.setSpan(new LinkSpan(() -> WebViewPageActivity.navToPrivace(LoginActivity.this)),
                    privacyStart, privacyEnd,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }

        privacyText.setText(spannable);
        privacyText.setMovementMethod(LinkMovementMethod.getInstance());
        privacyText.setHighlightColor(Color.TRANSPARENT);
    }

    private void applyMode(boolean emailMode) {
        isEmailMode = emailMode;

        accountForm.setVisibility(emailMode ? View.GONE : View.VISIBLE);
        emailForm.setVisibility(emailMode ? View.VISIBLE : View.GONE);
        accountIndicator.setVisibility(emailMode ? View.INVISIBLE : View.VISIBLE);
        emailIndicator.setVisibility(emailMode ? View.VISIBLE : View.INVISIBLE);

        if (emailMode) {
            accountTab.setTextColor(ContextCompat.getColor(this, R.color.auth_text_secondary));
            accountTab.setTypeface(accountTab.getTypeface(), android.graphics.Typeface.NORMAL);
            emailTab.setTextColor(ContextCompat.getColor(this, R.color.auth_primary));
            emailTab.setTypeface(emailTab.getTypeface(), android.graphics.Typeface.BOLD);
        } else {
            accountTab.setTextColor(ContextCompat.getColor(this, R.color.auth_primary));
            accountTab.setTypeface(accountTab.getTypeface(), android.graphics.Typeface.BOLD);
            emailTab.setTextColor(ContextCompat.getColor(this, R.color.auth_text_secondary));
            emailTab.setTypeface(emailTab.getTypeface(), android.graphics.Typeface.NORMAL);
        }

        updateLoginButtonState();
    }

    private void handleLogin() {
        if (isLoading) {
            return;
        }

        if (isEmailMode) {
            String email = safeTrim(emailInput.getText().toString());
            String code = safeTrim(emailCodeInput.getText().toString());
            int errorResId = AuthInputValidator.validateLoginErrorResId(true, email, code);
            if (errorResId != 0) {
                ToastUtils.show(this, errorResId);
                return;
            }
            showLoading(true);
            ServiceManager.getUserService().login(
                    AuthRequestFactory.buildEmailLoginRequest(email, code),
                    new ApiCallback<LoginResult>() {
                        @Override
                        public void onSuccess(LoginResult data) {
                            onLoginSuccess(data);
                        }

                        @Override
                        public void onError(int code, String message) {
                            showLoading(false);
                            LogUtils.serverError("auth", "loginByCode", code, message);
                            ToastUtils.show(LoginActivity.this,
                                    R.string.auth_error_login_failed);
                        }
                    });
            return;
        }

        String account = safeTrim(accountInput.getText().toString());
        String password = safeTrim(passwordInput.getText().toString());
        int errorResId = AuthInputValidator.validateLoginErrorResId(false, account, password);
        if (errorResId != 0) {
            ToastUtils.show(this, errorResId);
            return;
        }

        showLoading(true);
        ServiceManager.getUserService().login(
                AuthRequestFactory.buildAccountLoginRequest(account, password),
                new ApiCallback<LoginResult>() {
                    @Override
                    public void onSuccess(LoginResult data) {
                        onLoginSuccess(data);
                    }

                    @Override
                    public void onError(int code, String message) {
                        showLoading(false);
                        LogUtils.serverError("auth", "loginByPassword", code, message);
                        ToastUtils.show(LoginActivity.this,
                                R.string.auth_error_login_failed);
                    }
                });
    }

    private void onLoginSuccess(LoginResult data) {
        if (data == null) {
            showLoading(false);
            ToastUtils.show(this, R.string.auth_error_login_empty_response);
            return;
        }

        Log.i(TAG, "Login success");
        ConfigUtils.imToken = data.getIm_token();
        ConfigUtils.appToken = data.getAuthorization();
        ConfigUtils.myName = data.getNickname();
        ConfigUtils.myAvatarUrl = data.getAvatar();
        UserProfileStore.save(this, data.getUser_id(), data.getNickname(), data.getAvatar());

        if (!persistSession(data.getAuthorization(), data.getIm_token())) {
            showLoading(false);
            return;
        }
        JIMChatCore.getInstance().connect(ConfigUtils.imToken);
        showLoading(false);
        switchToConversationList();
    }

    private void requestEmailCode() {
        if (isSendingCode || isLoading) {
            return;
        }

        String email = safeTrim(emailInput.getText().toString());
        if (email.isEmpty()) {
            ToastUtils.show(this, R.string.auth_error_email_required);
            return;
        }

        isSendingCode = true;
        getCodeText.setEnabled(false);
        getCodeText.setText(R.string.auth_send_code_loading);

        CodeRequest request = new CodeRequest();
        request.setEmail(email);

        ServiceManager.getUserService().getVerificationCode(request, new ApiCallback<Void>() {
            @Override
            public void onSuccess(Void data) {
                isSendingCode = false;
                getCodeText.setEnabled(true);
                getCodeText.setText(R.string.auth_send_code);
                ToastUtils.show(LoginActivity.this, R.string.auth_toast_code_sent);
            }

            @Override
            public void onError(int code, String message) {
                isSendingCode = false;
                getCodeText.setEnabled(true);
                getCodeText.setText(R.string.auth_send_code);
                LogUtils.serverError("auth", "sendVerifyCode", code, message);
                ToastUtils.show(LoginActivity.this,
                        R.string.auth_error_send_code_failed);
            }
        });
    }

    private boolean persistSession(String appToken, String imToken) {
        try {
            long expireAtMillis = System.currentTimeMillis() + TOKEN_VALIDITY_DURATION;
            sessionRepository.saveSession(appToken, imToken, expireAtMillis);
            return true;
        } catch (RuntimeException e) {
            Log.e(TAG, "Failed to persist session", e);
            ToastUtils.show(this, R.string.operation_failed);
            return false;
        }
    }

    private void showLoading(boolean loading) {
        isLoading = loading;
        updateLoginButtonState();
    }

    private void updateLoginButtonState() {
        String principal = isEmailMode ? emailInput.getText().toString() : accountInput.getText().toString();
        String credential = isEmailMode ? emailCodeInput.getText().toString() : passwordInput.getText().toString();
        boolean canSubmit = AuthInputValidator.validateLoginErrorResId(isEmailMode, principal, credential) == 0;

        if (isLoading) {
            loginButton.setEnabled(false);
            loginButton.setText(R.string.auth_button_login_loading);
            loginButton.setBackgroundResource(R.drawable.bg_auth_button_loading);
            loginButton.setTextColor(ContextCompat.getColor(this, R.color.white));
            loginProgress.setVisibility(View.VISIBLE);
            return;
        }

        loginProgress.setVisibility(View.GONE);
        loginButton.setText(R.string.auth_button_login);
        if (canSubmit) {
            loginButton.setEnabled(true);
            loginButton.setBackgroundResource(R.drawable.bg_auth_button_enabled);
            loginButton.setTextColor(ContextCompat.getColor(this, R.color.white));
        } else {
            loginButton.setEnabled(false);
            loginButton.setBackgroundResource(R.drawable.bg_auth_button_disabled);
            loginButton.setTextColor(ContextCompat.getColor(this, R.color.auth_button_disabled_text));
        }
    }

    private void switchToRegister() {
        Intent intent = new Intent(this, RegisterActivity.class);
        startActivity(intent);
    }

    private void switchToConversationList() {
        Intent intent = new Intent(this, MainActivity.class);
        startActivity(intent);
        finish();
    }

    private String safeTrim(String value) {
        return value == null ? "" : value.trim();
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
            ds.setColor(ContextCompat.getColor(LoginActivity.this, R.color.auth_primary));
        }
    }
}

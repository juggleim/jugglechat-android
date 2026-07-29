package com.juggle.im.android.app;

import android.graphics.Color;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import com.juggle.im.android.R;
import com.juggle.im.android.auth.OrganizationStore;
import com.juggle.im.android.auth.SessionRepository;
import com.juggle.im.android.auth.UserProfileStore;
import com.juggle.im.android.component.AbsAppActivity;
import com.juggle.im.android.core.JIMChatCore;
import com.juggle.im.android.model.ConfigUtils;
import com.juggle.im.android.server.beans.OrganizationLookupBean;
import com.juggle.im.android.server.http.ApiCallback;
import com.juggle.im.android.server.http.ServiceManager;
import com.juggle.im.android.utils.LogUtils;
import com.juggle.im.android.utils.ToastUtils;

/**
 * 登录流程中的组织信息页面，用于加载并应用组织对应的服务器配置。
 */
public class OrganizationLoginActivity extends AbsAppActivity {
    private static final String TAG = "OrganizationLogin";
    private static final int MIN_ORGANIZATION_ID_LENGTH = 4;

    private EditText organizationInput;
    private Button enterOrganizationButton;
    private ProgressBar enterOrganizationProgress;
    private View organizationTopBar;

    private OrganizationStore organizationStore;
    private SessionRepository sessionRepository;
    private boolean isSubmitting;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setupSystemBars();
        setContentView(R.layout.activity_organization_login);

        organizationStore = new OrganizationStore(this);
        sessionRepository = SessionRepository.create(this);
        initViews();
        applySystemBarInsets();
        setupListeners();
        updateSubmitState();
    }

    private void setupSystemBars() {
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        getWindow().setStatusBarColor(Color.TRANSPARENT);
        WindowInsetsControllerCompat controller =
                new WindowInsetsControllerCompat(getWindow(), getWindow().getDecorView());
        controller.setAppearanceLightStatusBars(true);
    }

    private void initViews() {
        organizationInput = findViewById(R.id.organizationInput);
        enterOrganizationButton = findViewById(R.id.enterOrganizationButton);
        enterOrganizationProgress = findViewById(R.id.enterOrganizationProgress);
        organizationTopBar = findViewById(R.id.organizationTopBar);

        organizationInput.setText(ConfigUtils.organizationId);
        organizationInput.setSelection(organizationInput.length());
    }

    private void applySystemBarInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(organizationTopBar, (view, insets) -> {
            int statusBarTop = insets.getInsets(
                    WindowInsetsCompat.Type.statusBars()).top;
            android.view.ViewGroup.MarginLayoutParams layoutParams =
                    (android.view.ViewGroup.MarginLayoutParams) view.getLayoutParams();
            if (layoutParams.topMargin != statusBarTop) {
                // TIPS：认证页采用沉浸式头图，顶部操作栏必须按设备状态栏真实高度下移。
                layoutParams.topMargin = statusBarTop;
                view.setLayoutParams(layoutParams);
            }
            return insets;
        });
        ViewCompat.requestApplyInsets(organizationTopBar);
    }

    private void setupListeners() {
        findViewById(R.id.organizationBack).setOnClickListener(v -> finish());
        enterOrganizationButton.setOnClickListener(v -> submitOrganization());
        organizationInput.setOnEditorActionListener((view, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE && canSubmit()) {
                submitOrganization();
                return true;
            }
            return false;
        });
        organizationInput.addTextChangedListener(new TextWatcher() {
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
        });
    }

    private void submitOrganization() {
        if (isSubmitting) {
            return;
        }
        String organizationId = safeTrim(organizationInput.getText().toString());
        if (organizationId.length() < MIN_ORGANIZATION_ID_LENGTH) {
            organizationInput.setError(getString(R.string.auth_enterprise_id_invalid));
            return;
        }

        setSubmitting(true);
        ServiceManager.getOrganizationService().getOrganization(
                organizationId,
                new ApiCallback<OrganizationLookupBean>() {
                    @Override
                    public void onSuccess(OrganizationLookupBean data) {
                        if (!isPageActive()) {
                            return;
                        }
                        OrganizationStore.OrganizationConfig config =
                                OrganizationStore.parseServerInfo(
                                        organizationId,
                                        data == null ? null : data.getServerInfoPlain());
                        if (config == null) {
                            setSubmitting(false);
                            ToastUtils.show(OrganizationLoginActivity.this,
                                    R.string.auth_enterprise_id_invalid);
                            return;
                        }
                        applyOrganization(config);
                    }

                    @Override
                    public void onError(int code, String message) {
                        if (!isPageActive()) {
                            return;
                        }
                        setSubmitting(false);
                        LogUtils.serverError("auth", "loadOrganization", code, message);
                        ToastUtils.show(OrganizationLoginActivity.this,
                                R.string.auth_enterprise_id_load_failed);
                    }
                });
    }

    private void applyOrganization(
            @NonNull OrganizationStore.OrganizationConfig config) {
        OrganizationStore.OrganizationConfig previousConfig = organizationStore.read();
        try {
            // TIPS：组织运行配置和认证会话必须作为一次切换处理，避免旧组织令牌请求新服务器。
            applyRuntimeOrganization(config);
            organizationStore.save(config);
            sessionRepository.clearSession();
            UserProfileStore.clear(this);
            ConfigUtils.appToken = null;
            ConfigUtils.imToken = null;
            ConfigUtils.myName = null;
            ConfigUtils.myAvatarUrl = null;
            ToastUtils.show(this, R.string.auth_enterprise_id_switched);
            setResult(RESULT_OK);
            finish();
        } catch (RuntimeException exception) {
            LogUtils.e(TAG, "-", "auth", "applyOrganization",
                    "fail", exception.getMessage());
            rollbackOrganization(previousConfig);
            setSubmitting(false);
            ToastUtils.show(this, R.string.auth_enterprise_id_load_failed);
        }
    }

    private void applyRuntimeOrganization(
            @NonNull OrganizationStore.OrganizationConfig config) {
        ConfigUtils.applyOrganization(
                config.getOrganizationId(),
                config.getAppKey(),
                config.getAppServerUrl(),
                config.getImServers());
        ServiceManager.reconfigureBusinessServices();
        JIMChatCore.getInstance().switchOrganization(
                this,
                ConfigUtils.imServers,
                ConfigUtils.appKey);
    }

    private void rollbackOrganization(
            @NonNull OrganizationStore.OrganizationConfig previousConfig) {
        try {
            applyRuntimeOrganization(previousConfig);
            organizationStore.save(previousConfig);
        } catch (RuntimeException rollbackException) {
            LogUtils.e(TAG, "-", "auth", "rollbackOrganization",
                    "fail", rollbackException.getMessage());
        }
    }

    private void setSubmitting(boolean submitting) {
        isSubmitting = submitting;
        updateSubmitState();
    }

    private void updateSubmitState() {
        if (isSubmitting) {
            enterOrganizationButton.setEnabled(false);
            enterOrganizationButton.setText(R.string.auth_button_enter_organization_loading);
            enterOrganizationButton.setBackgroundResource(R.drawable.bg_auth_button_loading);
            enterOrganizationButton.setTextColor(
                    ContextCompat.getColor(this, R.color.white));
            enterOrganizationProgress.setVisibility(View.VISIBLE);
            return;
        }

        enterOrganizationProgress.setVisibility(View.GONE);
        enterOrganizationButton.setText(R.string.auth_button_enter_organization);
        if (canSubmit()) {
            enterOrganizationButton.setEnabled(true);
            enterOrganizationButton.setBackgroundResource(R.drawable.bg_auth_button_enabled);
            enterOrganizationButton.setTextColor(
                    ContextCompat.getColor(this, R.color.white));
        } else {
            enterOrganizationButton.setEnabled(false);
            enterOrganizationButton.setBackgroundResource(R.drawable.bg_auth_button_disabled);
            enterOrganizationButton.setTextColor(
                    ContextCompat.getColor(this, R.color.auth_button_disabled_text));
        }
    }

    private boolean canSubmit() {
        return organizationInput != null
                && safeTrim(organizationInput.getText().toString()).length()
                >= MIN_ORGANIZATION_ID_LENGTH;
    }

    private boolean isPageActive() {
        return !isFinishing() && !isDestroyed();
    }

    private String safeTrim(String value) {
        return value == null ? "" : value.trim();
    }
}

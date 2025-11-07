package com.juggle.im.android.app;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;

import com.juggle.im.android.R;
import com.juggle.im.android.core.JIMChatCore;
import com.juggle.im.android.model.ConfigUtils;
import com.juggle.im.android.server.beans.LoginRequest;
import com.juggle.im.android.server.beans.LoginResult;
import com.juggle.im.android.server.http.ApiCallback;
import com.juggle.im.android.server.http.ServiceManager;

public class LoginActivity extends AppCompatActivity {
    private EditText phoneInput;
    private EditText passwordInput;
    private Button loginButton;
    private TextView verificationLoginText;
    private TextView registerText;
    private CardView loginFormContainer;
    private ProgressBar loginProgress;

    private static final String PREFS_NAME = "login_prefs";
    public static final String KEY_APP_TOKEN = "app_token";
    public static final String KEY_IM_TOKEN = "im_token";
    private static final String KEY_EXPIRE_TIME = "expire_time";
    private static final long TOKEN_VALIDITY_DURATION = 1 * 24 * 60 * 60 * 1000; // 2天

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);
        
        initViews();
        setupListeners();
    }

    private void initViews() {
        phoneInput = findViewById(R.id.phoneInput);
        passwordInput = findViewById(R.id.passwordInput);
        loginButton = findViewById(R.id.loginButton);
        registerText = findViewById(R.id.registerText);
        loginFormContainer = findViewById(R.id.login_form_container);
        loginProgress = findViewById(R.id.loginProgress);
    }

    private void setupListeners() {
        loginButton.setOnClickListener(v -> handleLogin());
        registerText.setOnClickListener(v -> switchToRegister());
    }

    private void handleLogin() {
        String phone = phoneInput.getText().toString().trim();
        String password = passwordInput.getText().toString().trim();
        
        // 简单验证输入
        if (phone.isEmpty() || password.isEmpty()) {
            Toast.makeText(this, "请输入账号和密码", Toast.LENGTH_SHORT).show();
            return;
        }
        
        // 显示loading状态
        showLoading(true);
        
        ServiceManager.getUserService().login(new LoginRequest(phone, password), new ApiCallback<LoginResult>() {
            @Override
            public void onSuccess(LoginResult data) {
                Log.i("login", "登录成功");
                ConfigUtils.imToken = data.getIm_token();
                ConfigUtils.appToken = data.getAuthorization();
                ConfigUtils.myName = data.getNickname();
                ConfigUtils.myAvatarUrl = data.getAvatar();
                
                // 保存token和过期时间
                saveToken(data.getAuthorization(), data.getIm_token());
                
                JIMChatCore.getInstance().connect(ConfigUtils.imToken);
                // 隐藏loading状态
                showLoading(false);
                switchToConversationList();
            }

            @Override
            public void onError(int code, String message) {
                // 隐藏loading状态
                showLoading(false);
                Toast.makeText(LoginActivity.this, "登录失败: " + message, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void saveToken(String token, String imToken) {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        editor.putString(KEY_APP_TOKEN, token);
        editor.putString(KEY_IM_TOKEN, imToken);
        editor.putLong(KEY_EXPIRE_TIME, System.currentTimeMillis() + TOKEN_VALIDITY_DURATION);
        editor.apply();
    }

    private void showLoading(boolean show) {
        if (show) {
            loginButton.setText("登录中...");
            loginButton.setEnabled(false);
            loginProgress.setVisibility(View.VISIBLE);
        } else {
            loginButton.setText("Log In");
            loginButton.setEnabled(true);
            loginProgress.setVisibility(View.GONE);
        }
    }

    private void switchToVerificationLogin() {
        // TODO: 实现验证码登录切换逻辑
    }

    // 跳转到注册页面
    private void switchToRegister() {
        Intent intent = new Intent(this, RegisterActivity.class);
        startActivity(intent);
    }

    // 跳转到会话列表页面
    private void switchToConversationList() {
        Intent intent = new Intent(this, MainActivity.class);
        startActivity(intent);
        finish();
    }
}
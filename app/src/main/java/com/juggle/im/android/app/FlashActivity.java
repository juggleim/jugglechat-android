package com.juggle.im.android.app;

import static com.juggle.im.android.app.LoginActivity.KEY_APP_TOKEN;
import static com.juggle.im.android.app.LoginActivity.KEY_IM_TOKEN;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;

import androidx.appcompat.app.AppCompatActivity;

import com.juggle.im.android.R;
import com.juggle.im.android.core.JIMChatCore;
import com.juggle.im.android.model.ConfigUtils;
import com.juggle.im.android.server.http.ServiceManager;

import java.util.Date;

public class FlashActivity extends AppCompatActivity {
    private static final String TAG = "FlashActivity";
    private static final String PREFS_NAME = "login_prefs";
    private static final String KEY_EXPIRE_TIME = "expire_time";
    private static final long TOKEN_VALIDITY_DURATION = 2 * 24 * 60 * 60 * 1000; // 2天

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_flash);

        // 检查是否存在有效的登录token
        if (hasValidToken()) {
            // 自动登录
            autoLogin();
        } else {
            // 延迟跳转到登录页面
            new Handler().postDelayed(this::goToLogin, 1500);
        }
    }

    private boolean hasValidToken() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String token = prefs.getString(KEY_APP_TOKEN, null);
        long expireTime = prefs.getLong(KEY_EXPIRE_TIME, 0);
        String imToken = prefs.getString(KEY_IM_TOKEN, null);

        if (token != null && imToken != null && expireTime > System.currentTimeMillis()) {
            ConfigUtils.appToken = token;
            ConfigUtils.imToken = imToken;
            return true;
        }
        return false;
    }

    private void autoLogin() {
        // 尝试连接
        new Handler().postDelayed(this::goToMain, 1200);
    }

    private void goToMain() {
        Intent intent = new Intent(this, MainActivity.class);
        startActivity(intent);
        finish();
    }

    private void goToLogin() {
        Intent intent = new Intent(this, LoginActivity.class);
        startActivity(intent);
        finish();
    }
}
package com.juggle.im.android.app;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.view.Window;

import androidx.appcompat.app.AppCompatActivity;

import com.juggle.im.android.R;
import com.juggle.im.android.auth.SessionRepository;
import com.juggle.im.android.auth.StartupRouteUseCase;
import com.juggle.im.android.auth.UserProfileStore;
import com.juggle.im.android.model.ConfigUtils;

public class FlashActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_flash);

        StartupRouteUseCase.RouteDecision routeDecision = StartupRouteUseCase.create(this).decide();
        if (routeDecision.getTargetRoute() == StartupRouteUseCase.TargetRoute.MAIN) {
            applySession(routeDecision);
            // 自动登录
            autoLogin();
        } else {
            // 延迟跳转到登录页面
            new Handler().postDelayed(this::goToLogin, 1500);
        }
        Window window = getWindow();
        window.setNavigationBarColor(getColor(R.color.white));
    }

    private void applySession(StartupRouteUseCase.RouteDecision routeDecision) {
        SessionRepository.SessionState sessionState = routeDecision.getSessionState();
        if (sessionState == null) {
            return;
        }
        ConfigUtils.appToken = sessionState.getAppToken();
        ConfigUtils.imToken = sessionState.getImToken();
        UserProfileStore.UserProfile userProfile = UserProfileStore.read(this);
        if (!userProfile.isEmpty()) {
            ConfigUtils.myName = userProfile.getNickname();
            ConfigUtils.myAvatarUrl = userProfile.getAvatar();
        }
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

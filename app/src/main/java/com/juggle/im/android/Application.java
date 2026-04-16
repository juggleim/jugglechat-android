package com.juggle.im.android;
import androidx.multidex.MultiDexApplication;

import com.juggle.im.android.auth.SessionRepository;
import com.juggle.im.android.auth.StartupRouteUseCase;
import com.juggle.im.android.auth.UserProfileStore;
import com.juggle.im.android.chat.call.CallIncomingFloatingManager;
import com.juggle.im.android.core.JIMChatCore;
import com.juggle.im.android.model.ConfigUtils;

import java.util.Collections;

public class Application extends MultiDexApplication {

    private StartupRouteUseCase.RouteDecision startupRouteDecision;

    @Override
    public void onCreate() {
        super.onCreate();
        JIMChatCore.getInstance().init(this, Collections.singletonList(ConfigUtils.imServer), ConfigUtils.appKey);
        CallIncomingFloatingManager.getInstance().init(this);

        // tips: 原 FlashActivity 逻辑前置，启动时立即恢复 session 并做路由决策
        startupRouteDecision = StartupRouteUseCase.create(this).decide();
        if (startupRouteDecision.getTargetRoute() == StartupRouteUseCase.TargetRoute.MAIN) {
            applySession(startupRouteDecision);
        }
    }

    /**
     * 获取启动路由决策结果，供 MainActivity 使用
     */
    public StartupRouteUseCase.RouteDecision getStartupRouteDecision() {
        return startupRouteDecision;
    }

    /**
     * 恢复会话状态到 ConfigUtils，包括 token 和用户资料
     * @param routeDecision 路由决策结果
     */
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
}

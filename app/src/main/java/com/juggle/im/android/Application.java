package com.juggle.im.android;
import android.content.res.Configuration;

import androidx.annotation.NonNull;
import androidx.multidex.MultiDexApplication;

import com.juggle.im.android.auth.SessionRepository;
import com.juggle.im.android.auth.StartupRouteUseCase;
import com.juggle.im.android.auth.UserProfileStore;
import com.juggle.im.android.auth.OrganizationStore;
import com.juggle.im.android.chat.call.CallIncomingFloatingManager;
import com.juggle.im.android.core.JIMChatCore;
import com.juggle.im.android.i18n.LanguageManager;
import com.juggle.im.android.model.ConfigUtils;

public class Application extends MultiDexApplication {

    private StartupRouteUseCase.RouteDecision startupRouteDecision;

    @Override
    public void onCreate() {
        super.onCreate();
        // tips: 语言必须在任何界面创建前生效，否则首屏会用系统语言
        LanguageManager.init(this);
        // TIPS：企业配置必须早于 HTTP 服务和 IM SDK 初始化恢复，避免冷启动连接到历史默认环境。
        OrganizationStore.OrganizationConfig organizationConfig =
                new OrganizationStore(this).read();
        ConfigUtils.applyOrganization(
                organizationConfig.getOrganizationId(),
                organizationConfig.getAppKey(),
                organizationConfig.getAppServerUrl(),
                organizationConfig.getImServers());
        JIMChatCore.getInstance().init(this, ConfigUtils.imServers, ConfigUtils.appKey);
        CallIncomingFloatingManager.getInstance().init(this);

        // tips: 原 FlashActivity 逻辑前置，启动时立即恢复 session 并做路由决策
        startupRouteDecision = StartupRouteUseCase.create(this).decide();
        if (startupRouteDecision.getTargetRoute() == StartupRouteUseCase.TargetRoute.MAIN) {
            applySession(startupRouteDecision);
        }
    }

    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        // tips: 跟随系统语言时，系统语言变更后需要丢弃本地化 Context 缓存
        LanguageManager.invalidateLocalizedContext();
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

package com.juggle.im.android.model;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ConfigUtils {
    public static final String DEFAULT_ORGANIZATION_ID = "100000";
    public static final String DEFAULT_APP_KEY = "nwm6fxqt2aeebhb7";
    public static final String DEFAULT_APP_SERVER_URL = "https://ws.snailchat.im";
    public static final String DEFAULT_IM_SERVER = "wss://ws.snailchat.im";

    /**
     * 申请新的key进行替换
     */
    public static String organizationId = DEFAULT_ORGANIZATION_ID;
    public static String appKey = DEFAULT_APP_KEY;
    public static String appServerUrl = DEFAULT_APP_SERVER_URL;
    public static List<String> imServers =
            Collections.singletonList(DEFAULT_IM_SERVER);

    /**
     * 音视频ID
     * 需要申请即构音视频 ID，这个仅仅用来测试
     */
    public static Integer zegoId = 1881186044;

    // app token
    public static String appToken = null;

    // im Token
    public static String imToken = null;

    public static String myAvatarUrl = null;
    public static String myName = null;

    /**
     * 仅调试构建可用的 TLS 放宽开关。
     * <p>
     * 默认关闭，避免联调配置误入发布环境。
     */
    public static boolean allowInsecureTlsForDebug = false;

    /**
     * 将企业配置应用到当前进程。
     *
     * @param newOrganizationId 企业 ID
     * @param newAppKey 应用密钥
     * @param newAppServerUrl 业务服务器地址
     * @param newImServers IM 服务器列表
     */
    public static void applyOrganization(@NonNull String newOrganizationId,
                                         @NonNull String newAppKey,
                                         @NonNull String newAppServerUrl,
                                         @NonNull List<String> newImServers) {
        organizationId = newOrganizationId;
        appKey = newAppKey;
        appServerUrl = newAppServerUrl;
        imServers = Collections.unmodifiableList(new ArrayList<>(newImServers));
    }
}

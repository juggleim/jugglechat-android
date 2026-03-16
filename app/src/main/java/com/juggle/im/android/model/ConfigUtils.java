package com.juggle.im.android.model;

public class ConfigUtils {
    /**
     * 申请新的key进行替换
     */
    public static String appKey = "nsw3sue72begyv7y";
    public static String appServerUrl = "https://ws.juggleim.com";
    public static String imServer = "wss://ws.juggleim.com";

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
}

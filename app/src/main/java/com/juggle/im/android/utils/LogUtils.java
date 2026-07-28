package com.juggle.im.android.utils;

import android.util.Log;

/**
 * 结构化日志工具。
 * <p>
 * 统一字段：traceId、feature、event、result、detail，便于后续接入日志平台与检索。
 */
public final class LogUtils {

    private static final String TAG_SERVER_ERROR = "ServerError";

    private LogUtils() {
    }

    public static void i(String tag, String traceId, String feature, String event, String result, String detail) {
        Log.i(tag, build(traceId, feature, event, result, detail));
    }

    public static void e(String tag, String traceId, String feature, String event, String result, String detail) {
        Log.e(tag, build(traceId, feature, event, result, detail));
    }

    /**
     * 记录被界面丢弃的服务端错误详情。
     * <p>
     * TIPS：服务端返回的 message 不做本地化，界面统一展示本地文案，原始错误只落日志，避免中英混排且不丢排查线索。
     *
     * @param feature 业务模块
     * @param event   事件名
     * @param code    服务端错误码
     * @param message 服务端错误文案
     */
    public static void serverError(String feature, String event, int code, String message) {
        e(TAG_SERVER_ERROR, "-", feature, event, "fail", "code=" + code + ",msg=" + message);
    }

    private static String build(String traceId, String feature, String event, String result, String detail) {
        return "traceId=" + traceId
                + ",feature=" + feature
                + ",event=" + event
                + ",result=" + result
                + ",detail=" + detail;
    }
}

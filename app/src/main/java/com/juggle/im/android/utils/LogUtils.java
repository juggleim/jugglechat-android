package com.juggle.im.android.utils;

import android.util.Log;

/**
 * 结构化日志工具。
 * <p>
 * 统一字段：traceId、feature、event、result、detail，便于后续接入日志平台与检索。
 */
public final class LogUtils {

    private LogUtils() {
    }

    public static void i(String tag, String traceId, String feature, String event, String result, String detail) {
        Log.i(tag, build(traceId, feature, event, result, detail));
    }

    public static void e(String tag, String traceId, String feature, String event, String result, String detail) {
        Log.e(tag, build(traceId, feature, event, result, detail));
    }

    private static String build(String traceId, String feature, String event, String result, String detail) {
        return "traceId=" + traceId
                + ",feature=" + feature
                + ",event=" + event
                + ",result=" + result
                + ",detail=" + detail;
    }
}

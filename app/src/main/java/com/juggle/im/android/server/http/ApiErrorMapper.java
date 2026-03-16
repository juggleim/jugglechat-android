package com.juggle.im.android.server.http;

import android.text.TextUtils;

/**
 * API 错误分层映射器。
 * <p>
 * 约定：
 * - 100x：客户端网络/解析层错误
 * - 业务错误：沿用服务端 code，保持可追踪
 */
public final class ApiErrorMapper {

    public static final int CODE_NETWORK = 1001;
    public static final int CODE_EMPTY_BODY = 1002;
    public static final int CODE_PARSE = 1003;
    public static final int CODE_IO = 1004;

    private ApiErrorMapper() {
    }

    public static int fromHttpStatus(int httpCode) {
        return CODE_NETWORK;
    }

    public static int emptyBody() {
        return CODE_EMPTY_BODY;
    }

    public static int parseFailure() {
        return CODE_PARSE;
    }

    public static int ioFailure() {
        return CODE_IO;
    }

    public static String toUserMessage(int code, String rawMessage) {
        switch (code) {
            case CODE_NETWORK:
            case CODE_IO:
                return "网络开小差，请稍后重试";
            case CODE_EMPTY_BODY:
            case CODE_PARSE:
                return "服务响应异常，请稍后重试";
            default:
                if (!TextUtils.isEmpty(rawMessage)) {
                    return rawMessage;
                }
                return "请求失败，请稍后再试";
        }
    }
}

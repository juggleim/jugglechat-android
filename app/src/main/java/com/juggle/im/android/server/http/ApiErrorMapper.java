package com.juggle.im.android.server.http;

import android.text.TextUtils;

import com.juggle.im.android.R;
import com.juggle.im.android.i18n.AppRes;
import com.juggle.im.android.utils.LogUtils;

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
                return AppRes.string(R.string.api_error_network);
            case CODE_EMPTY_BODY:
            case CODE_PARSE:
                return AppRes.string(R.string.api_error_server);
            default:
                // TIPS: 服务端 rawMessage 不做展示（不可本地化），仅落日志，界面统一给通用文案
                if (!TextUtils.isEmpty(rawMessage)) {
                    LogUtils.serverError("http", "toUserMessage", code, rawMessage);
                }
                return AppRes.string(R.string.api_error_default);
        }
    }
}

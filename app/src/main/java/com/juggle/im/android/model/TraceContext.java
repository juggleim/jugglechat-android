package com.juggle.im.android.model;

import java.util.UUID;

/**
 * 请求级 Trace 上下文。
 * <p>
 * 使用 ThreadLocal 维护同一请求链路的 traceId，便于日志和请求头统一透传。
 */
public final class TraceContext {

    private static final ThreadLocal<String> TRACE_ID_LOCAL = new ThreadLocal<>();

    private TraceContext() {
    }

    public static String currentOrNew() {
        String traceId = TRACE_ID_LOCAL.get();
        if (traceId == null || traceId.trim().isEmpty()) {
            traceId = newTraceId();
            TRACE_ID_LOCAL.set(traceId);
        }
        return traceId;
    }

    public static void set(String traceId) {
        if (traceId == null || traceId.trim().isEmpty()) {
            return;
        }
        TRACE_ID_LOCAL.set(traceId);
    }

    public static void clear() {
        TRACE_ID_LOCAL.remove();
    }

    private static String newTraceId() {
        return UUID.randomUUID().toString().replace("-", "");
    }
}

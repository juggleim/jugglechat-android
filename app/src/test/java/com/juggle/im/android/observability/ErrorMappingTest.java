package com.juggle.im.android.observability;

import com.juggle.im.android.model.TraceContext;
import com.juggle.im.android.server.http.ApiErrorMapper;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

/**
 * 可观测性与错误模型统一测试。
 */
public class ErrorMappingTest {

    @Test
    public void shouldMapTransportErrorsToStableErrorCodes() {
        assertEquals(ApiErrorMapper.CODE_NETWORK, ApiErrorMapper.fromHttpStatus(503));
        assertEquals(ApiErrorMapper.CODE_EMPTY_BODY, ApiErrorMapper.emptyBody());
        assertEquals(ApiErrorMapper.CODE_PARSE, ApiErrorMapper.parseFailure());
    }

    @Test
    public void shouldProvideUserFriendlyMessageByErrorLayer() {
        assertEquals("网络开小差，请稍后重试", ApiErrorMapper.toUserMessage(ApiErrorMapper.CODE_NETWORK, "Network error"));
        assertEquals("服务响应异常，请稍后重试", ApiErrorMapper.toUserMessage(ApiErrorMapper.CODE_PARSE, "Parse error"));
    }

    @Test
    public void shouldCreateTraceIdForRequestScope() {
        TraceContext.clear();
        String traceId = TraceContext.currentOrNew();
        assertFalse(traceId.trim().isEmpty());
    }
}

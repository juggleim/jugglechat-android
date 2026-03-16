package com.juggle.im.core.ui.message;

import java.util.Collections;
import java.util.Map;

/**
 * 消息渲染上下文。
 * <p>
 * 通过 context 传入会话维度信息，避免渲染器直接依赖业务状态容器。
 */
public final class MessageRenderContext {

    private final String conversationType;
    private final Map<String, Object> attributes;

    public MessageRenderContext(String conversationType, Map<String, Object> attributes) {
        this.conversationType = conversationType;
        this.attributes = attributes == null ? Collections.emptyMap() : Collections.unmodifiableMap(attributes);
    }

    public String conversationType() {
        return conversationType;
    }

    public Map<String, Object> attributes() {
        return attributes;
    }
}

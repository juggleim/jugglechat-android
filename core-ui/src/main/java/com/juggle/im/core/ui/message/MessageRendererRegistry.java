package com.juggle.im.core.ui.message;

import com.juggle.im.core.domain.message.MessagePayload;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 渲染器注册中心。
 * <p>
 * 复杂逻辑说明：
 * - 统一维护 contentType -> renderer 映射，新增消息类型只需注册一个渲染器。
 * - 未命中时自动回退到兜底渲染器，避免未知类型导致崩溃。
 */
public final class MessageRendererRegistry {

    private final Map<String, MessageRenderer<?>> renderers = new ConcurrentHashMap<>();
    private final MessageRenderer<MessagePayload> fallbackRenderer = new UnsupportedMessageRenderer();

    public <T extends MessagePayload> void register(MessageRenderer<T> renderer) {
        MessageRenderer<?> previous = renderers.putIfAbsent(renderer.contentType(), renderer);
        if (previous != null) {
            throw new IllegalStateException("消息渲染器重复注册: " + renderer.contentType());
        }
    }

    public MessageRenderer<?> find(String contentType) {
        MessageRenderer<?> renderer = renderers.get(contentType);
        return renderer != null ? renderer : fallbackRenderer;
    }
}

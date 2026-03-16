package com.juggle.im.core.domain.message;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 默认消息类型注册中心实现。
 * <p>
 * 复杂逻辑说明：
 * - 使用 ConcurrentHashMap 保证并发注册安全。
 * - 对 contentType 做唯一性保护，避免渲染链路出现歧义映射。
 */
public final class MutableMessageTypeRegistry implements MessageTypeRegistry {

    private final Map<String, MessageTypeSpec<?>> specs = new ConcurrentHashMap<>();

    @Override
    public <T extends MessagePayload> void register(MessageTypeSpec<T> spec) {
        MessageTypeSpec<?> previous = specs.putIfAbsent(spec.contentType(), spec);
        if (previous != null) {
            throw new IllegalStateException("消息类型重复注册: " + spec.contentType());
        }
    }

    @Override
    public MessageTypeSpec<?> find(String contentType) {
        return specs.get(contentType);
    }
}

package com.juggle.im.core.domain.message;

/**
 * 消息类型注册中心对外接口。
 * <p>
 * 对外暴露为接口，便于业务自定义实现或在测试中替换。
 */
public interface MessageTypeRegistry {

    /**
     * 注册消息类型定义。
     *
     * @throws IllegalStateException 当 contentType 重复时抛出
     */
    <T extends MessagePayload> void register(MessageTypeSpec<T> spec);

    /**
     * 查找消息类型定义。
     *
     * @return 命中时返回 spec；未命中返回 null
     */
    MessageTypeSpec<?> find(String contentType);
}

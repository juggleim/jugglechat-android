package com.juggle.im.core.domain.message;

/**
 * 消息原始载荷解析器。
 *
 * @param <T> 解析后的消息载荷类型
 */
public interface MessagePayloadParser<T extends MessagePayload> {

    /**
     * 将 SDK/网络层原始数据解析为领域载荷。
     */
    T parse(Object rawPayload);
}

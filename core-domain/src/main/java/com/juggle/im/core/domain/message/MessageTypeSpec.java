package com.juggle.im.core.domain.message;

/**
 * 消息类型元信息定义。
 *
 * @param <T> 消息载荷类型
 */
public final class MessageTypeSpec<T extends MessagePayload> {

    private final String contentType;
    private final Class<T> payloadClass;
    private final MessagePayloadParser<T> parser;

    private MessageTypeSpec(String contentType, Class<T> payloadClass, MessagePayloadParser<T> parser) {
        this.contentType = contentType;
        this.payloadClass = payloadClass;
        this.parser = parser;
    }

    public static <T extends MessagePayload> MessageTypeSpec<T> of(
            String contentType,
            Class<T> payloadClass,
            MessagePayloadParser<T> parser
    ) {
        if (contentType == null || contentType.trim().isEmpty()) {
            throw new IllegalArgumentException("contentType 不能为空");
        }
        if (payloadClass == null) {
            throw new IllegalArgumentException("payloadClass 不能为空");
        }
        if (parser == null) {
            throw new IllegalArgumentException("parser 不能为空");
        }
        return new MessageTypeSpec<>(contentType, payloadClass, parser);
    }

    public String contentType() {
        return contentType;
    }

    public Class<T> payloadClass() {
        return payloadClass;
    }

    public MessagePayloadParser<T> parser() {
        return parser;
    }
}

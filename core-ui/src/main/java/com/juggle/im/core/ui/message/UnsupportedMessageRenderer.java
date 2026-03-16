package com.juggle.im.core.ui.message;

import com.juggle.im.core.domain.message.MessagePayload;

/**
 * 未知消息类型兜底渲染器，确保不会因扩展缺失导致崩溃。
 */
public final class UnsupportedMessageRenderer implements MessageRenderer<MessagePayload> {

    @Override
    public String contentType() {
        return "__unsupported__";
    }

    @Override
    public RenderedMessage render(MessagePayload payload, MessageRenderContext context) {
        String type = payload == null ? "unknown" : payload.contentType();
        return new RenderedMessage("暂不支持展示该消息类型: " + type);
    }
}

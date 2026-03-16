package com.juggle.im.feature.chat.message;

import com.juggle.im.core.ui.message.MessageRenderContext;
import com.juggle.im.core.ui.message.MessageRenderer;
import com.juggle.im.core.ui.message.RenderedMessage;

/**
 * 默认文本消息渲染器。
 */
public final class TextMessageRenderer implements MessageRenderer<TextMessagePayload> {

    @Override
    public String contentType() {
        return TextMessagePayload.CONTENT_TYPE;
    }

    @Override
    public RenderedMessage render(TextMessagePayload payload, MessageRenderContext context) {
        return new RenderedMessage(payload.text());
    }
}

package com.juggle.im.feature.chat.message;

import com.juggle.im.core.domain.message.MessagePayload;

/**
 * 默认文本消息载荷。
 */
public final class TextMessagePayload implements MessagePayload {

    public static final String CONTENT_TYPE = "text";

    private final String text;

    public TextMessagePayload(String text) {
        this.text = text == null ? "" : text;
    }

    @Override
    public String contentType() {
        return CONTENT_TYPE;
    }

    public String text() {
        return text;
    }
}

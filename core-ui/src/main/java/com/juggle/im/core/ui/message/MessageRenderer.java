package com.juggle.im.core.ui.message;

import com.juggle.im.core.domain.message.MessagePayload;

/**
 * 消息渲染器扩展接口。
 *
 * @param <T> 渲染器支持的消息载荷类型
 */
public interface MessageRenderer<T extends MessagePayload> {

    /**
     * @return 渲染器对应的 contentType。
     */
    String contentType();

    /**
     * 将消息载荷渲染为 UI 所需中间模型。
     */
    RenderedMessage render(T payload, MessageRenderContext context);
}

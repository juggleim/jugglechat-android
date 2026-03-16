package com.juggle.im.feature.chat.message;

import com.juggle.im.core.domain.message.MessagePayload;
import com.juggle.im.core.domain.message.MessageTypeRegistry;
import com.juggle.im.core.domain.message.MessageTypeSpec;
import com.juggle.im.core.domain.message.MutableMessageTypeRegistry;
import com.juggle.im.core.ui.message.MessageRenderer;
import com.juggle.im.core.ui.message.MessageRendererRegistry;

/**
 * IM 消息默认注册中心。
 * <p>
 * 对外暴露统一扩展入口，业务只需注册 {@link MessageTypeSpec} 与 {@link MessageRenderer}
 * 即可新增自定义消息类型并完成渲染接入。
 */
public final class DefaultMessageRegistry {

    private DefaultMessageRegistry() {
    }

    /**
     * 创建包含默认文本消息能力的消息类型注册中心。
     */
    public static MessageTypeRegistry createDefaultTypeRegistry() {
        MutableMessageTypeRegistry registry = new MutableMessageTypeRegistry();
        registry.register(MessageTypeSpec.of(
                TextMessagePayload.CONTENT_TYPE,
                TextMessagePayload.class,
                rawPayload -> new TextMessagePayload(String.valueOf(rawPayload))
        ));
        return registry;
    }

    /**
     * 创建包含默认文本渲染能力的渲染器注册中心。
     */
    public static MessageRendererRegistry createDefaultRendererRegistry() {
        MessageRendererRegistry registry = new MessageRendererRegistry();
        registry.register(new TextMessageRenderer());
        return registry;
    }

    /**
     * 注册自定义消息类型与渲染器。
     * <p>
     * 复杂逻辑说明：
     * - 类型与渲染器必须成对注册，避免出现“可解析不可渲染”或“可渲染不可解析”的半状态。
     */
    public static <T extends MessagePayload> void registerCustomMessage(
            MessageTypeRegistry typeRegistry,
            MessageRendererRegistry rendererRegistry,
            MessageTypeSpec<T> spec,
            MessageRenderer<T> renderer
    ) {
        typeRegistry.register(spec);
        rendererRegistry.register(renderer);
    }
}

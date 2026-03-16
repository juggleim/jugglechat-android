package com.juggle.im.core.domain.message;

/**
 * 消息内容的统一领域抽象。
 * <p>
 * 任意新增消息类型都应实现该接口，这样消息流水线可以用统一模型处理。
 */
public interface MessagePayload {

    /**
     * @return 业务稳定的消息类型标识，例如 text/image/custom:red_packet。
     */
    String contentType();
}

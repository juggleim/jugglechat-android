package com.juggle.im.android.event;


import com.juggle.im.model.Message;

/**
 * 已存在消息的内容更新事件。
 *
 * <p>典型场景为 AI 流式文本消息：SDK 通过 onMessageUpdate 持续回调同一条消息的最新内容，
 * 需要原地刷新对应气泡，而不是当作新消息追加。</p>
 */
public class MessageContentUpdatedEvent {
    private final Message message;

    public MessageContentUpdatedEvent(Message message) {
        this.message = message;
    }

    public Message getMessage() {
        return message;
    }
}

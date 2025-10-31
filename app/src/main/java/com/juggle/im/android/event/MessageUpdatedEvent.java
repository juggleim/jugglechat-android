package com.juggle.im.android.event;


import com.juggle.im.model.Message;

public class MessageUpdatedEvent {
    private final Message message;

    public MessageUpdatedEvent(Message message) {
        this.message = message;
    }

    public Message getMessage() {
        return message;
    }
}

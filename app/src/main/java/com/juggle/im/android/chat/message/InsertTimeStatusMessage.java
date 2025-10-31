package com.juggle.im.android.chat.message;

import com.juggle.im.model.MessageContent;

public class InsertTimeStatusMessage extends MessageContent {
    private String description;
    private String mMessageType;
    private String mContent;

    @Override
    public byte[] encode() {
        return new byte[0];
    }

    @Override
    public void decode(byte[] bytes) {

    }

    public InsertTimeStatusMessage() {
    }

    public InsertTimeStatusMessage(String text) {
        this.description = text;
        this.mContent = text;
    }

    public void setDescription(String desc) {
        this.description = desc;
        this.mContent = desc;
    }

    public String description() {
        return description;
    }
}

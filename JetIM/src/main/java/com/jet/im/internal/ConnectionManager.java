package com.jet.im.internal;

import android.util.Log;

import com.jet.im.core.JetIMCore;
import com.jet.im.interfaces.IConnectionManager;
import com.jet.im.utils.LoggerUtils;

public class ConnectionManager implements IConnectionManager {
    @Override
    public void connect(String token) {
        LoggerUtils.i("connect, token is " + token);
    }

    @Override
    public void disconnect(boolean receivePush) {

    }

    public ConnectionManager(JetIMCore core, ConversationManager conversationManager, MessageManager messageManager) {
        this.mCore = core;
        this.mConversationManager = conversationManager;
        this.mMessageManager = messageManager;
    }

    private JetIMCore mCore;
    private ConversationManager mConversationManager;
    private MessageManager mMessageManager;
}

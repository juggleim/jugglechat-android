package com.juggle.im.android.event;

import com.juggle.im.JIMConst;

public class ConnectStatusEvent {
    private JIMConst.ConnectionStatus connectionStatus;
    private String message;
    private int code;

    public ConnectStatusEvent(JIMConst.ConnectionStatus connectionStatus, int code, String message) {
        this.connectionStatus = connectionStatus;
        this.message = message;
        this.code = code;
    }

    public int getCode() {
        return code;
    }

    public JIMConst.ConnectionStatus getConnectionStatus() {
        return connectionStatus;
    }

    public String getMessage() {
        return message;
    }
}

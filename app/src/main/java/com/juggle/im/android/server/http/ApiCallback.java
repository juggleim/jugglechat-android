package com.juggle.im.android.server.http;

public interface ApiCallback<T> {
    void onSuccess(T data);
    void onError(int code, String message);
}
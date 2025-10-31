package com.juggle.im.android.server.beans;

import com.juggle.im.android.server.http.ApiException;

public class HttpResult<T> {
    private int code;
    private String msg;
    private T data;

    public int getCode() {
        return code;
    }

    public void setCode(int code) {
        this.code = code;
    }

    public String getMsg() {
        return msg;
    }

    public void setMsg(String msg) {
        this.msg = msg;
    }

    public T getData() {
        return data;
    }

    public void setData(T data) {
        this.data = data;
    }

    public boolean isSuccess() {
        return code == 0;
    }

    public T getDataOrThrow() throws ApiException {
        if (!isSuccess()) {
            throw new ApiException(code, msg);
        }
        return data;
    }
}

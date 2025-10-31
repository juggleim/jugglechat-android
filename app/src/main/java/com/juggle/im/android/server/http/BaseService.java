package com.juggle.im.android.server.http;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import com.juggle.im.android.server.beans.HttpResult;

import java.io.IOException;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public abstract class BaseService {
    private Gson gson = new Gson();
    private Handler mainHandler = new Handler(Looper.getMainLooper());
    private OkHttpClient client;
    private String baseUrl;

    public BaseService(OkHttpClient client, String baseUrl) {
        this.client = client;
        this.baseUrl = baseUrl;
    }

    protected <T> void enqueueJson(String path, Object bodyObj, Class<T> dataClass, ApiCallback<T> callback) {
        Runnable r = () -> {
            try {
                String url = baseUrl + path;
                String json = gson.toJson(bodyObj == null ? new Object() : bodyObj);
                RequestBody body = RequestBody.create(ServiceManager.MEDIA_TYPE_JSON, json);
                Request request = new Request.Builder().url(url).post(body).build();
                Response resp = client.newCall(request).execute();
                if (!resp.isSuccessful()) {
                    postError(callback, -1, "Network error: " + resp.code());
                    return;
                }
                String respBody = resp.body() != null ? resp.body().string() : null;
                if (respBody == null) {
                    postError(callback, -1, "Empty response");
                    return;
                }
                HttpResult<T> result = parseHttpResult(respBody, dataClass);
                if (result == null) {
                    postError(callback, -1, "Parse error");
                    return;
                }
                if (result.isSuccess()) {
                    postSuccess(callback, result.getData());
                } else {
                    postError(callback, result.getCode(), result.getMsg());
                }
            } catch (IOException e) {
                postError(callback, -1, e.getMessage());
            }
        };
        new Thread(r, "UserService-network").start();
    }

    protected <T> void enqueueGet(String path, Class<T> dataClass, ApiCallback<T> callback) {
        Runnable r = () -> {
            try {
                String url = baseUrl + path;
                Request request = new Request.Builder().url(url).get().build();
                Response resp = client.newCall(request).execute();
                if (!resp.isSuccessful()) {
                    postError(callback, -1, "Network error: " + resp.code());
                    return;
                }
                String respBody = resp.body() != null ? resp.body().string() : null;
                if (respBody == null) {
                    postError(callback, -1, "Empty response");
                    return;
                }
                HttpResult<T> result = parseHttpResult(respBody, dataClass);
                if (result == null) {
                    postError(callback, -1, "Parse error");
                    return;
                }
                if (result.isSuccess()) {
                    postSuccess(callback, result.getData());
                } else {
                    postError(callback, result.getCode(), result.getMsg());
                }
            } catch (IOException e) {
                postError(callback, -1, e.getMessage());
            }
        };
        new Thread(r, "UserService-network").start();
    }

    protected <T> HttpResult<T> parseHttpResult(String json, Class<T> dataClass) {
        try {
            // parse outer HttpResult while parsing data field into dataClass
            // First parse generic map, then replace data
            HttpResult raw = gson.fromJson(json, HttpResult.class);
            // now parse data field separately
            com.google.gson.JsonObject jo = gson.fromJson(json, com.google.gson.JsonObject.class);
            if (jo.has("data") && !jo.get("data").isJsonNull()) {
                try {
                    T data = gson.fromJson(jo.get("data"), dataClass);
                    raw.setData(data);
                } catch (JsonSyntaxException e) {
                    // can't parse data into expected class
                    return null;
                }
            }
            return raw;
        } catch (JsonSyntaxException e) {
            return null;
        }
    }

    protected <T> void postSuccess(ApiCallback<T> callback, T data) {
        if (callback == null) return;
        if (Looper.myLooper() == Looper.getMainLooper()) {
            callback.onSuccess(data);
        } else {
            mainHandler.post(() -> callback.onSuccess(data));
        }
    }

    protected void postError(ApiCallback<?> callback, int code, String msg) {
        Log.e("BaseService", "postError: " + code + " - " + msg);
        if (callback == null) return;
        if (Looper.myLooper() == Looper.getMainLooper()) {
            callback.onError(code, msg);
        } else {
            mainHandler.post(() -> callback.onError(code, msg));
        }
    }

}

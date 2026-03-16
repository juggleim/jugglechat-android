package com.juggle.im.android.server.http;

import android.os.Handler;
import android.os.Looper;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import com.juggle.im.android.model.TraceContext;
import com.juggle.im.android.server.beans.HttpResult;
import com.juggle.im.android.utils.LogUtils;

import java.io.IOException;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public abstract class BaseService {
    private static final String TAG = "BaseService";
    private static final String FEATURE = "network";
    private final Gson gson = new Gson();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final OkHttpClient client;
    private final String baseUrl;

    public BaseService(OkHttpClient client, String baseUrl) {
        this.client = client;
        this.baseUrl = baseUrl;
    }

    protected <T> void enqueueJson(String path, Object bodyObj, Class<T> dataClass, ApiCallback<T> callback) {
        Runnable r = () -> {
            String traceId = TraceContext.currentOrNew();
            try {
                String url = baseUrl + path;
                String json = gson.toJson(bodyObj == null ? new Object() : bodyObj);
                RequestBody body = RequestBody.create(ServiceManager.MEDIA_TYPE_JSON, json);
                Request request = new Request.Builder().url(url).post(body).build();
                Response resp = client.newCall(request).execute();
                if (!resp.isSuccessful()) {
                    int code = ApiErrorMapper.fromHttpStatus(resp.code());
                    postError(callback, code, ApiErrorMapper.toUserMessage(code, "http=" + resp.code()), traceId, path);
                    return;
                }
                String respBody = resp.body() != null ? resp.body().string() : null;
                if (respBody == null) {
                    int code = ApiErrorMapper.emptyBody();
                    postError(callback, code, ApiErrorMapper.toUserMessage(code, "Empty response"), traceId, path);
                    return;
                }
                HttpResult<T> result = parseHttpResult(respBody, dataClass);
                if (result == null) {
                    int code = ApiErrorMapper.parseFailure();
                    postError(callback, code, ApiErrorMapper.toUserMessage(code, "Parse error"), traceId, path);
                    return;
                }
                if (result.isSuccess()) {
                    postSuccess(callback, result.getData(), traceId, path);
                } else {
                    int code = result.getCode();
                    postError(callback, code, ApiErrorMapper.toUserMessage(code, result.getMsg()), traceId, path);
                }
            } catch (IOException e) {
                int code = ApiErrorMapper.ioFailure();
                postError(callback, code, ApiErrorMapper.toUserMessage(code, e.getMessage()), traceId, path);
            } finally {
                TraceContext.clear();
            }
        };
        new Thread(r, "UserService-network").start();
    }

    protected <T> void enqueueGet(String path, Class<T> dataClass, ApiCallback<T> callback) {
        Runnable r = () -> {
            String traceId = TraceContext.currentOrNew();
            try {
                String url = baseUrl + path;
                Request request = new Request.Builder().url(url).get().build();
                Response resp = client.newCall(request).execute();
                if (!resp.isSuccessful()) {
                    int code = ApiErrorMapper.fromHttpStatus(resp.code());
                    postError(callback, code, ApiErrorMapper.toUserMessage(code, "http=" + resp.code()), traceId, path);
                    return;
                }
                String respBody = resp.body() != null ? resp.body().string() : null;
                if (respBody == null) {
                    int code = ApiErrorMapper.emptyBody();
                    postError(callback, code, ApiErrorMapper.toUserMessage(code, "Empty response"), traceId, path);
                    return;
                }
                HttpResult<T> result = parseHttpResult(respBody, dataClass);
                if (result == null) {
                    int code = ApiErrorMapper.parseFailure();
                    postError(callback, code, ApiErrorMapper.toUserMessage(code, "Parse error"), traceId, path);
                    return;
                }
                if (result.isSuccess()) {
                    postSuccess(callback, result.getData(), traceId, path);
                } else {
                    int code = result.getCode();
                    postError(callback, code, ApiErrorMapper.toUserMessage(code, result.getMsg()), traceId, path);
                }
            } catch (IOException e) {
                int code = ApiErrorMapper.ioFailure();
                postError(callback, code, ApiErrorMapper.toUserMessage(code, e.getMessage()), traceId, path);
            } finally {
                TraceContext.clear();
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

    protected <T> void postSuccess(ApiCallback<T> callback, T data, String traceId, String path) {
        LogUtils.i(TAG, traceId, FEATURE, "request", "success", "path=" + path);
        if (callback == null) return;
        if (Looper.myLooper() == Looper.getMainLooper()) {
            callback.onSuccess(data);
        } else {
            mainHandler.post(() -> callback.onSuccess(data));
        }
    }

    protected void postError(ApiCallback<?> callback, int code, String msg, String traceId, String path) {
        LogUtils.e(TAG, traceId, FEATURE, "request", "error", "path=" + path + ",code=" + code + ",message=" + msg);
        if (callback == null) return;
        if (Looper.myLooper() == Looper.getMainLooper()) {
            callback.onError(code, msg);
        } else {
            mainHandler.post(() -> callback.onError(code, msg));
        }
    }

}

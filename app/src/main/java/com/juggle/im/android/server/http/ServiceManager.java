package com.juggle.im.android.server.http;

import android.text.TextUtils;
import androidx.annotation.NonNull;
import com.google.gson.Gson;
import com.juggle.im.android.model.ConfigUtils;
import com.juggle.im.android.server.beans.HttpResult;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;

import okhttp3.*;

public class ServiceManager {
    public static final MediaType MEDIA_TYPE_JSON =
            MediaType.parse("application/json;charset=UTF-8");
    private static final UserService userService;
    private static final MomentService momentService;

    static {
        OkHttpClient okHttpClient = new OkHttpClient.Builder()
                .sslSocketFactory(Objects.requireNonNull(SSLHelper.getTrustAllSSLSocketFactory()), SSLHelper.getTrustAllManager())
                .addInterceptor(new Interceptor() {
                    @NonNull
                    @Override
                    public Response intercept(@NonNull Chain chain) throws IOException {
                        Request request = chain.request();
                        if (!TextUtils.isEmpty(ConfigUtils.appToken)) {
                            request = request.newBuilder().addHeader("authorization", ConfigUtils.appToken).build();
                        }
                        request = request.newBuilder().addHeader("appkey", ConfigUtils.appKey).build();
                        return chain.proceed(request);
                    }
                })
                .hostnameVerifier((hostname, session) -> true)
                .build();
        userService = new UserServiceImpl(okHttpClient, ConfigUtils.appServerUrl);
        momentService = new MomentServiceImpl(okHttpClient, ConfigUtils.appServerUrl);
    }

    public static UserService getUserService() {
        return userService;
    }

    public static MomentService getMomentService() {
        return momentService;
    }
}

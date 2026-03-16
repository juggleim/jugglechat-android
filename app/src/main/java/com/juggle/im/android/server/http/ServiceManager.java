package com.juggle.im.android.server.http;

import android.text.TextUtils;
import androidx.annotation.NonNull;
import com.juggle.im.android.model.ConfigUtils;

import java.io.IOException;

import okhttp3.*;

public class ServiceManager {
    public static final MediaType MEDIA_TYPE_JSON =
            MediaType.parse("application/json;charset=UTF-8");
    private static final UserService userService;
    private static final MomentService momentService;

    static {
        OkHttpClient.Builder clientBuilder = new OkHttpClient.Builder()
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
                ;
        // 默认走系统 TLS 校验；仅在 DEBUG + 手动开关时放宽策略用于联调。
        SSLHelper.applyTlsPolicy(clientBuilder);
        OkHttpClient okHttpClient = clientBuilder.build();
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

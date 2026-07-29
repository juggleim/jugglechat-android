package com.juggle.im.android.server.http;

import android.text.TextUtils;
import androidx.annotation.NonNull;
import com.juggle.im.android.model.ConfigUtils;
import com.juggle.im.android.model.TraceContext;

import java.io.IOException;

import okhttp3.*;

public class ServiceManager {
    private static final String ORGANIZATION_INDEX_URL = "https://index.snailchat.im";

    public static final MediaType MEDIA_TYPE_JSON =
            MediaType.parse("application/json;charset=UTF-8");
    private static final OkHttpClient okHttpClient;
    private static final OrganizationService organizationService;
    private static volatile UserService userService;
    private static volatile MomentService momentService;

    static {
        OkHttpClient.Builder clientBuilder = new OkHttpClient.Builder()
                .addInterceptor(new Interceptor() {
                    @NonNull
                    @Override
                    public Response intercept(@NonNull Chain chain) throws IOException {
                        Request request = chain.request();
                        String traceId = TraceContext.currentOrNew();
                        if (!TextUtils.isEmpty(ConfigUtils.appToken)) {
                            request = request.newBuilder().addHeader("authorization", ConfigUtils.appToken).build();
                        }
                        request = request.newBuilder()
                                .addHeader("appkey", ConfigUtils.appKey)
                                .addHeader("x-trace-id", traceId)
                                .build();
                        return chain.proceed(request);
                    }
                })
                ;
        // 默认走系统 TLS 校验；仅在 DEBUG + 手动开关时放宽策略用于联调。
        SSLHelper.applyTlsPolicy(clientBuilder);
        okHttpClient = clientBuilder.build();
        organizationService = new OrganizationService(okHttpClient, ORGANIZATION_INDEX_URL);
        reconfigureBusinessServices();
    }

    /**
     * 获取用户业务服务。
     *
     * @return 当前企业对应的用户业务服务
     */
    public static UserService getUserService() {
        return userService;
    }

    /**
     * 获取朋友圈业务服务。
     *
     * @return 当前企业对应的朋友圈业务服务
     */
    public static MomentService getMomentService() {
        return momentService;
    }

    /**
     * 获取固定企业索引服务。
     *
     * @return 企业索引服务
     */
    public static OrganizationService getOrganizationService() {
        return organizationService;
    }

    /**
     * 按 ConfigUtils 中最新的业务服务器地址重建业务服务。
     * <p>
     * TIPS：BaseService 会在构造时固定 baseUrl，企业切换后必须重建实例。
     */
    public static synchronized void reconfigureBusinessServices() {
        userService = new UserServiceImpl(okHttpClient, ConfigUtils.appServerUrl);
        momentService = new MomentServiceImpl(okHttpClient, ConfigUtils.appServerUrl);
    }
}

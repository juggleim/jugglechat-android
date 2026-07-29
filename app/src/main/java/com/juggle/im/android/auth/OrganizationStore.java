package com.juggle.im.android.auth;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import com.google.gson.annotations.SerializedName;
import com.juggle.im.android.model.ConfigUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 企业运行配置的持久化与解析入口。
 */
public final class OrganizationStore {
    private static final String PREFS_NAME = "organization_config";
    private static final String KEY_ORGANIZATION_ID = "organization_id";
    private static final String KEY_APP_KEY = "app_key";
    private static final String KEY_APP_SERVER = "app_server";
    private static final String KEY_IM_SERVERS = "im_servers";

    private final SharedPreferences preferences;
    private final Gson gson = new Gson();

    /**
     * 创建企业配置存储。
     *
     * @param context Android 上下文
     */
    public OrganizationStore(@NonNull Context context) {
        preferences = context.getApplicationContext()
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    /**
     * 读取已保存配置；没有有效记录时返回企业 100000 的内置默认配置。
     *
     * @return 可直接应用的企业配置
     */
    @NonNull
    public OrganizationConfig read() {
        String organizationId = trimToEmpty(
                preferences.getString(KEY_ORGANIZATION_ID, ConfigUtils.DEFAULT_ORGANIZATION_ID));
        String appKey = trimToEmpty(
                preferences.getString(KEY_APP_KEY, ConfigUtils.DEFAULT_APP_KEY));
        String appServer = trimToEmpty(
                preferences.getString(KEY_APP_SERVER, ConfigUtils.DEFAULT_APP_SERVER_URL));
        List<String> imServers = parseStringList(
                preferences.getString(KEY_IM_SERVERS, ""));

        OrganizationConfig config =
                new OrganizationConfig(organizationId, appKey, appServer, imServers);
        return config.isValid() ? config : defaultConfig();
    }

    /**
     * 解析企业索引接口返回的 server_info_plain。
     *
     * @param requestedOrganizationId 用户输入的企业 ID
     * @param plainJson server_info_plain JSON 字符串
     * @return 有效配置；字段不完整或 JSON 非法时返回 null
     */
    @Nullable
    public static OrganizationConfig parseServerInfo(@NonNull String requestedOrganizationId,
                                                     @Nullable String plainJson) {
        if (plainJson == null || plainJson.trim().isEmpty()) {
            return null;
        }
        try {
            ServerInfo serverInfo = new Gson().fromJson(plainJson, ServerInfo.class);
            if (serverInfo == null) {
                return null;
            }
            String resolvedOrganizationId = trimToEmpty(serverInfo.organizationId);
            if (resolvedOrganizationId.isEmpty()) {
                resolvedOrganizationId = trimToEmpty(requestedOrganizationId);
            }
            String appServer = firstNonEmpty(serverInfo.appServers);
            OrganizationConfig config = new OrganizationConfig(
                    resolvedOrganizationId,
                    serverInfo.appKey,
                    appServer,
                    serverInfo.imServers);
            return config.isValid() ? config : null;
        } catch (JsonSyntaxException exception) {
            return null;
        }
    }

    /**
     * 保存完整企业配置。
     *
     * @param config 已通过校验的企业配置
     */
    public void save(@NonNull OrganizationConfig config) {
        if (!config.isValid()) {
            throw new IllegalArgumentException("Organization config is invalid");
        }
        preferences.edit()
                .putString(KEY_ORGANIZATION_ID, config.getOrganizationId())
                .putString(KEY_APP_KEY, config.getAppKey())
                .putString(KEY_APP_SERVER, config.getAppServerUrl())
                .putString(KEY_IM_SERVERS, gson.toJson(config.getImServers()))
                .apply();
    }

    @NonNull
    private OrganizationConfig defaultConfig() {
        return new OrganizationConfig(
                ConfigUtils.DEFAULT_ORGANIZATION_ID,
                ConfigUtils.DEFAULT_APP_KEY,
                ConfigUtils.DEFAULT_APP_SERVER_URL,
                Collections.singletonList(ConfigUtils.DEFAULT_IM_SERVER));
    }

    @NonNull
    private List<String> parseStringList(@Nullable String json) {
        if (json == null || json.trim().isEmpty()) {
            return Collections.singletonList(ConfigUtils.DEFAULT_IM_SERVER);
        }
        try {
            String[] values = gson.fromJson(json, String[].class);
            List<String> result = new ArrayList<>();
            if (values != null) {
                for (String value : values) {
                    String normalized = trimToEmpty(value);
                    if (!normalized.isEmpty()) {
                        result.add(normalized);
                    }
                }
            }
            return result;
        } catch (JsonSyntaxException exception) {
            return Collections.singletonList(ConfigUtils.DEFAULT_IM_SERVER);
        }
    }

    @NonNull
    private static String firstNonEmpty(@Nullable List<String> values) {
        if (values == null) {
            return "";
        }
        for (String value : values) {
            String normalized = trimToEmpty(value);
            if (!normalized.isEmpty()) {
                return normalized;
            }
        }
        return "";
    }

    @NonNull
    private static String trimToEmpty(@Nullable String value) {
        return value == null ? "" : value.trim();
    }

    private static final class ServerInfo {
        @SerializedName("app_key")
        private String appKey;
        @SerializedName("alias_no")
        private String organizationId;
        @SerializedName("im_servers")
        private List<String> imServers;
        @SerializedName("app_servers")
        private List<String> appServers;
    }

    /**
     * 企业对应的完整运行配置。
     */
    public static final class OrganizationConfig {
        private final String organizationId;
        private final String appKey;
        private final String appServerUrl;
        private final List<String> imServers;

        /**
         * 创建企业运行配置。
         *
         * @param organizationId 企业 ID
         * @param appKey 应用密钥
         * @param appServerUrl 业务服务器地址
         * @param imServers IM 服务器列表
         */
        public OrganizationConfig(@Nullable String organizationId,
                                  @Nullable String appKey,
                                  @Nullable String appServerUrl,
                                  @Nullable List<String> imServers) {
            this.organizationId = trimToEmpty(organizationId);
            this.appKey = trimToEmpty(appKey);
            this.appServerUrl = trimToEmpty(appServerUrl);
            List<String> normalizedServers = new ArrayList<>();
            if (imServers != null) {
                for (String server : imServers) {
                    String normalized = trimToEmpty(server);
                    if (!normalized.isEmpty()) {
                        normalizedServers.add(normalized);
                    }
                }
            }
            this.imServers = Collections.unmodifiableList(normalizedServers);
        }

        /**
         * 获取企业 ID。
         *
         * @return 企业 ID
         */
        @NonNull
        public String getOrganizationId() {
            return organizationId;
        }

        /**
         * 获取应用密钥。
         *
         * @return 应用密钥
         */
        @NonNull
        public String getAppKey() {
            return appKey;
        }

        /**
         * 获取业务服务器地址。
         *
         * @return 业务服务器地址
         */
        @NonNull
        public String getAppServerUrl() {
            return appServerUrl;
        }

        /**
         * 获取 IM 服务器列表。
         *
         * @return 不可变 IM 服务器列表
         */
        @NonNull
        public List<String> getImServers() {
            return imServers;
        }

        /**
         * 校验企业配置是否包含运行所需的全部字段。
         *
         * @return true 表示配置完整
         */
        public boolean isValid() {
            return !organizationId.isEmpty()
                    && !appKey.isEmpty()
                    && !appServerUrl.isEmpty()
                    && !imServers.isEmpty();
        }
    }
}

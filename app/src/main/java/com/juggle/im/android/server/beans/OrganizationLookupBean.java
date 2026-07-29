package com.juggle.im.android.server.beans;

import com.google.gson.annotations.SerializedName;

/**
 * 企业索引接口返回的数据。
 */
public final class OrganizationLookupBean {
    @SerializedName("server_info_plain")
    private String serverInfoPlain;

    /**
     * 获取未加密的企业服务器配置 JSON。
     *
     * @return 企业服务器配置 JSON
     */
    public String getServerInfoPlain() {
        return serverInfoPlain;
    }
}

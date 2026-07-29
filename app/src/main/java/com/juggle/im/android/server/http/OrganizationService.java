package com.juggle.im.android.server.http;

import android.net.Uri;

import androidx.annotation.NonNull;

import com.juggle.im.android.server.beans.OrganizationLookupBean;

import okhttp3.OkHttpClient;

/**
 * 企业索引服务。
 */
public final class OrganizationService extends BaseService {

    /**
     * 创建企业索引服务。
     *
     * @param client HTTP 客户端
     * @param baseUrl 企业索引服务地址
     */
    public OrganizationService(@NonNull OkHttpClient client, @NonNull String baseUrl) {
        super(client, baseUrl);
    }

    /**
     * 按企业 ID 查询 appKey、业务服务器和 IM 服务器。
     *
     * @param organizationId 企业 ID
     * @param callback 请求回调
     */
    public void getOrganization(@NonNull String organizationId,
                                @NonNull ApiCallback<OrganizationLookupBean> callback) {
        enqueueGet("/serverinfos?no=" + Uri.encode(organizationId.trim()),
                OrganizationLookupBean.class,
                callback);
    }
}

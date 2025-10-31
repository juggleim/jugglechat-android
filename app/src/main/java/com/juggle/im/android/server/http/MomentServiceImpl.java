package com.juggle.im.android.server.http;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.juggle.im.android.model.ConfigUtils;
import com.juggle.im.android.server.beans.PostBean;
import com.juggle.im.android.server.beans.PostsListData;

import java.util.HashMap;
import java.util.List;

import okhttp3.Request;
import okhttp3.RequestBody;

/**
 * Simple OkHttp based implementation for moment endpoints.
 */
public class MomentServiceImpl extends BaseService implements MomentService {
    public MomentServiceImpl(okhttp3.OkHttpClient client, String baseUrl) {
        super(client, baseUrl);
    }

    @Override
    public void getPosts(Long start, Integer limit, Integer order, ApiCallback<PostsListData> callback) {
        int l = (limit == null || limit < 1) ? 20 : limit;
        if (l > 50) l = 50;
        StringBuilder sb = new StringBuilder("/jim/posts/list?");
        sb.append("limit=").append(l);
        if (start != null && start > 0) sb.append("&start=").append(start);
        if (order != null) sb.append("&order=").append(order);
        enqueueGet(sb.toString(), PostsListData.class, callback);
    }

    @Override
    public void getPost(String postId, ApiCallback<PostBean> callback) {
        StringBuilder sb = new StringBuilder("/jim/posts/info?post_id=");
        sb.append(postId);
        enqueueGet(sb.toString(), PostBean.class, callback);
    }

    @Override
    public void addComment(String postId, String parentCommentId, String parentUserId, String text, ApiCallback<Void> callback) {
        String url = "/jim/posts/comments/add";
        JsonObject body = new JsonObject();
        body.addProperty("post_id", postId);
        body.addProperty("parent_comment_id", parentCommentId);
        body.addProperty("parent_user_id", parentUserId);
        body.addProperty("text", text);
        enqueueJson(url, body, Void.class, callback);
    }

    @Override
    public void addPost(PostBean content, ApiCallback<Void> callback) {
        String url = "/jim/posts/add";
        enqueueJson(url, content, Void.class, callback);
    }

    @Override
    public void addReaction(String postId, String key, String value, ApiCallback<Void> callback) {
        String url = "/jim/posts/reactions/add";
        JsonObject body = new JsonObject();
        body.addProperty("post_id", postId);
        body.addProperty("key", key);
        body.addProperty("value", value);
        enqueueJson(url, body, Void.class, callback);
    }

    @Override
    public void deleteComment(List<String> commentIds, ApiCallback<Void> callback) {
        String url = "/jim/posts/comments/del";
        HashMap<String, Object> params = new HashMap<>();
        params.put("comment_ids", commentIds);
        enqueueJson(url, params, Void.class, callback);;
    }
}

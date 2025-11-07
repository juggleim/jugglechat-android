package com.juggle.im.android.server.http;

import com.juggle.im.android.server.beans.PostBean;
import com.juggle.im.android.server.beans.PostsListData;

import java.util.List;

/**
 * API for Moments (posts) endpoints.
 */
public interface MomentService {
    /**
     * GET /jim/posts/list
     *
     * @param start optional start timestamp in ms (13 digits)
     * @param limit optional page size (default 20, max 50)
     * @param order optional order 0: desc, 1: asc
     */
    void getPosts(Long start, Integer limit, Integer order, ApiCallback<PostsListData> callback);


    void getPost(String postId, ApiCallback<PostBean> callback);

    /**
     * POST /jim/posts/comments/add
     *
     * @param postId          ID of the post
     * @param parentCommentId ID of the parent comment
     * @param parentUserId    ID of the parent user
     * @param text            Content of the comment
     */
    void addComment(String postId, String parentCommentId, String parentUserId, String text, ApiCallback<Void> callback);

    /**
     * POST /jim/posts/add
     *
     * @param content Content of the post
     */
    void addPost(PostBean content, ApiCallback<Void> callback);

    /**
     * like post
     *
     * @param postId ID of the post
     * @param key    Reaction key
     * @param value  Reaction value
     */
    void addReaction(String postId, String key, String value, ApiCallback<Void> callback);

    /**
     * DELETE /jim/posts/comments/delete
     * Delete a comment.
     * @param commentIds
     * @param callback
     */
    public void deleteComment(List<String> commentIds, ApiCallback<Void> callback);

    /**
     * DELETE /jim/posts/delete
     * Delete a post.
     * @param postIds
     * @param callback
     */
    public void deletePost(List<String> postIds, ApiCallback<Void> callback);
}

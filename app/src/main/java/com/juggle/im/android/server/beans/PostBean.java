package com.juggle.im.android.server.beans;

import java.util.List;
import java.util.Map;

public class PostBean {
    private String post_id;
    private ContentBean content;
    private UserInfoBean user_info;
    private Map<String, List<ReactionItem>> reactions;
    private List<TopCommentBean> top_comments;
    private long created_time;
    private long updated_time;

    public String getPost_id() { return post_id; }
    public void setPost_id(String post_id) { this.post_id = post_id; }
    public ContentBean getContent() { return content; }
    public void setContent(ContentBean content) { this.content = content; }
    public UserInfoBean getUser_info() { return user_info; }
    public void setUser_info(UserInfoBean user_info) { this.user_info = user_info; }
    public Map<String, List<ReactionItem>> getReactions() { return reactions; }
    public void setReactions(Map<String, List<ReactionItem>> reactions) { this.reactions = reactions; }
    public List<TopCommentBean> getTop_comments() { return top_comments; }
    public void setTop_comments(List<TopCommentBean> top_comments) { this.top_comments = top_comments; }
    public long getCreated_time() { return created_time; }
    public void setCreated_time(long created_time) { this.created_time = created_time; }
    public long getUpdated_time() { return updated_time; }
    public void setUpdated_time(long updated_time) { this.updated_time = updated_time; }
}

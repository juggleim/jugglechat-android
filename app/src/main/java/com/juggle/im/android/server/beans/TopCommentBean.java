package com.juggle.im.android.server.beans;

public class TopCommentBean {
    private String comment_id;
    private String post_id;
    private String parent_comment_id;
    private String text;
    private UserInfoBean parent_user_info;
    private UserInfoBean user_info;
    private long created_time;
    private long updated_time;

    public String getComment_id() { return comment_id; }
    public void setComment_id(String comment_id) { this.comment_id = comment_id; }
    public String getPost_id() { return post_id; }
    public void setPost_id(String post_id) { this.post_id = post_id; }
    public String getParent_comment_id() { return parent_comment_id; }
    public void setParent_comment_id(String parent_comment_id) { this.parent_comment_id = parent_comment_id; }
    public String getText() { return text; }
    public void setText(String text) { this.text = text; }
    public UserInfoBean getParent_user_info() { return parent_user_info; }
    public void setParent_user_info(UserInfoBean parent_user_info) { this.parent_user_info = parent_user_info; }
    public UserInfoBean getUser_info() { return user_info; }
    public void setUser_info(UserInfoBean user_info) { this.user_info = user_info; }
    public long getCreated_time() { return created_time; }
    public void setCreated_time(long created_time) { this.created_time = created_time; }
    public long getUpdated_time() { return updated_time; }
    public void setUpdated_time(long updated_time) { this.updated_time = updated_time; }
}

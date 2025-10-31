package com.juggle.im.android.server.beans;

import java.util.List;

public class PostsListData {
    private List<PostBean> items;
    private boolean is_finished;

    public List<PostBean> getItems() { return items; }
    public void setItems(List<PostBean> items) { this.items = items; }
    public boolean isIs_finished() { return is_finished; }
    public void setIs_finished(boolean is_finished) { this.is_finished = is_finished; }
}

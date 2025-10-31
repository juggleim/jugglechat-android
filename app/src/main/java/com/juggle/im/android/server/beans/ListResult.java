package com.juggle.im.android.server.beans;

import java.util.List;

public class ListResult<T> {
    private List<T> items;

    public List<T> getItems() {
        return items;
    }

    public void setItems(List<T> items) {
        this.items = items;
    }
}

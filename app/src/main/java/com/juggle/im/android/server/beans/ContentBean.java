package com.juggle.im.android.server.beans;

import java.util.List;

public class ContentBean {
    private String text;
    private List<ImageBean> images;
    private VideoBean video;

    public String getText() { return text; }
    public void setText(String text) { this.text = text; }
    public List<ImageBean> getImages() { return images; }
    public void setImages(List<ImageBean> images) { this.images = images; }
    public VideoBean getVideo() { return video; }
    public void setVideo(VideoBean video) { this.video = video; }
}

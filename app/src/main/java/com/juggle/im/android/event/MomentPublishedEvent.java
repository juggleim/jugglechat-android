package com.juggle.im.android.event;

/**
 * 朋友圈动态发布成功事件。
 *
 * <p>对齐 iOS 的 MomentDidPublish 通知：发布方只负责广播，动态流页面订阅后自行刷新，
 * 这样从任意入口发布都能让已打开的列表同步，而不依赖 startActivityForResult 的返回链路。</p>
 */
public class MomentPublishedEvent {

    private final String userId;
    private final String momentId;

    /**
     * @param userId   发布者用户 ID
     * @param momentId 新动态 ID，SDK 未回传时可为 null
     */
    public MomentPublishedEvent(String userId, String momentId) {
        this.userId = userId;
        this.momentId = momentId;
    }

    /**
     * 获取发布者用户 ID。
     *
     * @return 发布者用户 ID
     */
    public String getUserId() {
        return userId;
    }

    /**
     * 获取新动态 ID。
     *
     * @return 新动态 ID，可能为 null
     */
    public String getMomentId() {
        return momentId;
    }
}

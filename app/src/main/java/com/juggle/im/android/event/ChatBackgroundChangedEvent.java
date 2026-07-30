package com.juggle.im.android.event;

/**
 * 聊天背景变更事件。
 *
 * <p>对齐 iOS 的 ChatBackgroundStore.didChangeNotification：设置页保存后广播，
 * 已打开的会话页收到即刷新背景，不必等页面重建。</p>
 */
public class ChatBackgroundChangedEvent {

    private final int backgroundRes;

    /**
     * @param backgroundRes 新选中的背景图资源 id，0 表示无背景
     */
    public ChatBackgroundChangedEvent(int backgroundRes) {
        this.backgroundRes = backgroundRes;
    }

    /**
     * 获取新选中的背景图资源 id。
     *
     * @return 背景图资源 id，0 表示无背景
     */
    public int getBackgroundRes() {
        return backgroundRes;
    }
}

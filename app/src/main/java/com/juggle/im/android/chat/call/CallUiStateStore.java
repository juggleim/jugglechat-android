package com.juggle.im.android.chat.call;

import java.util.ArrayList;

/**
 * 通话UI临时状态仓库。
 *
 * 简要描述：
 * 仅缓存“最小化浮窗恢复”所需信息，不持久化到磁盘，进程被杀后自动失效。
 */
public final class CallUiStateStore {
    private static FloatingCallInfo floatingCallInfo;

    private CallUiStateStore() {
    }

    /**
     * 保存当前最小化通话信息。
     *
     * @param info 浮窗通话信息
     */
    public static synchronized void saveFloatingCallInfo(FloatingCallInfo info) {
        floatingCallInfo = info;
    }

    /**
     * 获取当前最小化通话信息。
     *
     * @return 浮窗通话信息；为空表示当前无最小化通话
     */
    public static synchronized FloatingCallInfo getFloatingCallInfo() {
        return floatingCallInfo;
    }

    /**
     * 清空最小化通话信息。
     */
    public static synchronized void clearFloatingCallInfo() {
        floatingCallInfo = null;
    }

    /**
     * 浮窗通话信息快照。
     */
    public static class FloatingCallInfo {
        public String callId = "";
        public String conversationId = "";
        public String inviterUserId = "";
        public ArrayList<String> targetUserIds = new ArrayList<>();
        public boolean isVideoCall;
        public boolean isMultiCall;
        public boolean isGroupCall;
        public String direction = "outgoing";
        public boolean connected;
        public long connectedStartAt;
    }
}

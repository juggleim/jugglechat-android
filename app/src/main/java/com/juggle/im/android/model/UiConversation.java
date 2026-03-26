package com.juggle.im.android.model;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.juggle.im.model.Conversation;
import com.juggle.im.model.ConversationInfo;
import com.juggle.im.model.Message;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 会话 UI 领域状态。
 * <p>
 * 设计说明：
 * - 允许同时承载 SDK 原始对象和脱离 SDK 的快照字段，便于后续 UDF/缓存迁移。
 * - Getter 统一做兜底，避免 UI 层因空对象出现 NPE。
 */
public class UiConversation {
    private static final long UNSET_LONG = Long.MIN_VALUE;
    private static final int UNSET_INT = Integer.MIN_VALUE;

    @Nullable
    private ConversationInfo conversationInfo;
    private String id = "";
    private String name = "";
    private String lastMessageUserName = "";
    private String avatar = "";
    private String draft = "";
    private boolean isGroup;
    private boolean isMuted;
    private boolean isTop;
    private long topTime = UNSET_LONG;
    private long sortTime = UNSET_LONG;
    private int unreadCount = UNSET_INT;
    private String conversationTypeKey = "";
    private final Map<String, Object> extensions = new LinkedHashMap<>();

    @NonNull
    public String getId() {
        if (!id.isEmpty()) {
            return id;
        }
        if (conversationInfo != null && conversationInfo.getConversation() != null) {
            return trimToEmpty(conversationInfo.getConversation().getConversationId());
        }
        return "";
    }

    public void setId(@Nullable String id) {
        this.id = trimToEmpty(id);
    }

    @NonNull
    public String getName() {
        if (!name.isEmpty()) {
            return name;
        }
        return getId();
    }

    public void setName(@Nullable String name) {
        this.name = trimToEmpty(name);
    }

    @NonNull
    public String getAvatar() {
        return avatar;
    }

    public void setAvatar(@Nullable String avatar) {
        this.avatar = trimToEmpty(avatar);
    }

    /**
     * 获取会话草稿内容。
     */
    @NonNull
    public String getDraft() {
        if (!draft.isEmpty()) {
            return draft;
        }
        if (conversationInfo != null) {
            return trimToEmpty(conversationInfo.getDraft());
        }
        return "";
    }

    /**
     * 设置会话草稿内容。
     *
     * @param draft 草稿文本
     */
    public void setDraft(@Nullable String draft) {
        this.draft = trimToEmpty(draft);
    }

    @NonNull
    public String getLastMessageUserName() {
        return lastMessageUserName;
    }

    public void setLastMessageUserName(@Nullable String lastMessageUserName) {
        this.lastMessageUserName = trimToEmpty(lastMessageUserName);
    }

    @Nullable
    public Message getLastMessage() {
        return conversationInfo != null ? conversationInfo.getLastMessage() : null;
    }

    public long getSortTime() {
        if (sortTime != UNSET_LONG) {
            return sortTime;
        }
        if (conversationInfo != null) {
            return conversationInfo.getSortTime();
        }
        return 0L;
    }

    public void setSortTime(long sortTime) {
        this.sortTime = sortTime;
    }

    public boolean isTop() {
        if (conversationInfo != null) {
            return safeTop(conversationInfo, isTop);
        }
        return isTop;
    }

    public void setTop(boolean top) {
        isTop = top;
    }

    public long getTopTime() {
        if (topTime != UNSET_LONG) {
            return topTime;
        }
        if (conversationInfo != null) {
            return safeTopTime(conversationInfo, 0L);
        }
        return 0L;
    }

    public void setTopTime(long topTime) {
        this.topTime = topTime;
    }

    public boolean isMuted() {
        if (conversationInfo != null) {
            return conversationInfo.isMute();
        }
        return isMuted;
    }

    public void setMuted(boolean muted) {
        isMuted = muted;
    }

    public int getUnreadCount() {
        if (unreadCount != UNSET_INT) {
            return Math.max(unreadCount, 0);
        }
        return conversationInfo != null ? Math.max(conversationInfo.getUnreadCount(), 0) : 0;
    }

    public void setUnreadCount(int unreadCount) {
        this.unreadCount = Math.max(unreadCount, 0);
    }

    public boolean isGroup() {
        if (conversationInfo != null && conversationInfo.getConversation() != null) {
            return conversationInfo.getConversation().getConversationType() == Conversation.ConversationType.GROUP;
        }
        return isGroup;
    }

    public void setGroup(boolean group) {
        isGroup = group;
    }

    @NonNull
    public String getConversationTypeKey() {
        if (!conversationTypeKey.isEmpty()) {
            return conversationTypeKey;
        }
        if (conversationInfo != null && conversationInfo.getConversation() != null
                && conversationInfo.getConversation().getConversationType() != null) {
            return conversationInfo.getConversation().getConversationType().name();
        }
        return isGroup ? Conversation.ConversationType.GROUP.name() : Conversation.ConversationType.PRIVATE.name();
    }

    public void setConversationTypeKey(@Nullable String conversationTypeKey) {
        this.conversationTypeKey = trimToEmpty(conversationTypeKey);
    }

    @NonNull
    public Conversation.ConversationType getConversationType() {
        if (conversationInfo != null && conversationInfo.getConversation() != null
                && conversationInfo.getConversation().getConversationType() != null) {
            return conversationInfo.getConversation().getConversationType();
        }
        String typeKey = getConversationTypeKey();
        if (Conversation.ConversationType.GROUP.name().equalsIgnoreCase(typeKey)) {
            return Conversation.ConversationType.GROUP;
        }
        if (Conversation.ConversationType.SYSTEM.name().equalsIgnoreCase(typeKey)) {
            return Conversation.ConversationType.SYSTEM;
        }
        return Conversation.ConversationType.PRIVATE;
    }

    @Nullable
    public ConversationInfo getConversationInfo() {
        return conversationInfo;
    }

    public void setConversationInfo(@Nullable ConversationInfo conversationInfo) {
        this.conversationInfo = conversationInfo;
    }

    /**
     * 扩展字段：用于灰度字段或业务方自定义状态，不影响主流程字段。
     */
    public void putExtension(@NonNull String key, @Nullable Object value) {
        extensions.put(key, value);
    }

    @Nullable
    public Object getExtension(@NonNull String key) {
        return extensions.get(key);
    }

    @NonNull
    public Map<String, Object> getExtensions() {
        return Collections.unmodifiableMap(extensions);
    }

    @NonNull
    public static UiConversation fromConversationInfo(@Nullable ConversationInfo info) {
        UiConversation ui = new UiConversation();
        ui.conversationInfo = info;
        if (info == null || info.getConversation() == null) {
            return ui;
        }

        ui.id = trimToEmpty(info.getConversation().getConversationId());
        ui.name = ui.id;
        ui.isTop = safeTop(info, false);
        ui.topTime = safeTopTime(info, 0L);
        ui.isMuted = info.isMute();
        ui.unreadCount = Math.max(info.getUnreadCount(), 0);
        ui.sortTime = info.getSortTime();
        ui.draft = trimToEmpty(info.getDraft());
        if (info.getConversation().getConversationType() != null) {
            ui.conversationTypeKey = info.getConversation().getConversationType().name();
            ui.isGroup = info.getConversation().getConversationType() == Conversation.ConversationType.GROUP;
        }
        return ui;
    }

    private static boolean safeTop(@NonNull ConversationInfo info, boolean fallback) {
        try {
            return info.isTop();
        } catch (Throwable ignored) {
            return fallback;
        }
    }

    private static long safeTopTime(@NonNull ConversationInfo info, long fallback) {
        try {
            return info.getTopTime();
        } catch (Throwable ignored) {
            return fallback;
        }
    }

    @NonNull
    private static String trimToEmpty(@Nullable String value) {
        return value == null ? "" : value.trim();
    }
}

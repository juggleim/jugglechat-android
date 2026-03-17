package com.juggle.im.android.chat.domain;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.juggle.im.JIM;
import com.juggle.im.JIMConst;
import com.juggle.im.android.model.UiConversation;
import com.juggle.im.model.Conversation;
import com.juggle.im.model.ConversationInfo;
import com.juggle.im.model.GroupInfo;
import com.juggle.im.model.Message;
import com.juggle.im.model.UserInfo;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 会话仓储：统一承接会话读取、映射和排序规则。
 * <p>
 * 设计目标：
 * 1. 保证会话排序规则只有一处实现，避免不同页面出现顺序漂移。
 * 2. 对 UI 层隐藏 SDK 读取细节，后续可无缝替换数据来源。
 * 3. 通过 Gateway 抽象提供可测试性，方便在单测中注入 Fake。
 */
public final class ConversationRepository {

    /**
     * SDK/数据源访问抽象。 
     */
    public interface Gateway {

        /**
         * 拉取会话列表分页数据。
         */
        @Nullable
        List<ConversationInfo> getConversationInfoList(int pageSize, long cursor, @NonNull JIMConst.PullDirection direction);

        /**
         * 查询群信息，用于渲染会话标题与头像。
         */
        @Nullable
        GroupInfo getGroupInfo(@NonNull String groupId);

        /**
         * 查询用户信息，用于渲染私聊会话与最后一条发送者名称。
         */
        @Nullable
        UserInfo getUserInfo(@NonNull String userId);

        /**
         * 清空会话未读数。
         */
        void clearUnreadCount(@NonNull Conversation conversation);

        /**
         * 更新会话置顶状态。
         */
        void setTop(@NonNull Conversation conversation, boolean isTop);

        /**
         * 更新会话免打扰状态。
         */
        void setMute(@NonNull Conversation conversation, boolean isMute);

        /**
         * 删除会话。
         */
        void deleteConversation(@NonNull Conversation conversation);
    }

    private static final Comparator<UiConversation> CONVERSATION_COMPARATOR = (left, right) -> {
        if (left == right) {
            return 0;
        }
        if (left == null) {
            return 1;
        }
        if (right == null) {
            return -1;
        }

        if (left.isTop() != right.isTop()) {
            return left.isTop() ? -1 : 1;
        }

        int sortTimeResult = Long.compare(right.getSortTime(), left.getSortTime());
        if (sortTimeResult != 0) {
            return sortTimeResult;
        }

        return trimToEmpty(left.getId()).compareTo(trimToEmpty(right.getId()));
    };

    private final Gateway gateway;

    public ConversationRepository() {
        this(new JuggleGateway());
    }

    public ConversationRepository(@NonNull Gateway gateway) {
        this.gateway = gateway;
    }

    /**
     * 拉取并映射会话分页。
     */
    @NonNull
    public List<UiConversation> fetchPage(int pageSize, long cursor, @NonNull JIMConst.PullDirection direction) {
        List<ConversationInfo> infos = gateway.getConversationInfoList(pageSize, cursor, direction);
        return mapAndSort(infos);
    }

    /**
     * 将 SDK 会话对象映射为 UiConversation 并应用统一排序。
     */
    @NonNull
    public List<UiConversation> mapAndSort(@Nullable List<ConversationInfo> infos) {
        if (infos == null || infos.isEmpty()) {
            return new ArrayList<>();
        }

        List<UiConversation> conversations = new ArrayList<>();
        for (ConversationInfo info : infos) {
            UiConversation mapped = mapToUiConversation(info);
            if (mapped != null) {
                conversations.add(mapped);
            }
        }
        conversations.sort(CONVERSATION_COMPARATOR);
        return conversations;
    }

    /**
     * 基于会话 ID 进行 upsert，输出排好序的最终快照。
     * <p>
     * 复杂逻辑说明：
     * - 先写 current，再写 incoming；后写会覆盖前写，确保“增量更新优先”。
     * - 最终统一执行一次排序，避免多轮插入排序带来的位置抖动。
     */
    @NonNull
    public List<UiConversation> mergeSnapshot(@Nullable List<UiConversation> current,
            @Nullable List<UiConversation> incoming) {
        Map<String, UiConversation> mergedById = new LinkedHashMap<>();
        upsertToMap(mergedById, current);
        upsertToMap(mergedById, incoming);

        List<UiConversation> merged = new ArrayList<>(mergedById.values());
        merged.sort(CONVERSATION_COMPARATOR);
        return merged;
    }

    /**
     * 清空指定会话未读。
     */
    public void clearUnread(@NonNull UiConversation uiConversation) {
        Conversation conversation = requireConversation(uiConversation);
        gateway.clearUnreadCount(conversation);
    }

    /**
     * 设置会话置顶。
     */
    public void setTop(@NonNull UiConversation uiConversation, boolean isTop) {
        Conversation conversation = requireConversation(uiConversation);
        gateway.setTop(conversation, isTop);
    }

    /**
     * 设置会话免打扰。
     */
    public void setMute(@NonNull UiConversation uiConversation, boolean isMute) {
        Conversation conversation = requireConversation(uiConversation);
        gateway.setMute(conversation, isMute);
    }

    /**
     * 删除会话。
     */
    public void delete(@NonNull UiConversation uiConversation) {
        Conversation conversation = requireConversation(uiConversation);
        gateway.deleteConversation(conversation);
    }

    @Nullable
    private UiConversation mapToUiConversation(@Nullable ConversationInfo info) {
        if (info == null || info.getConversation() == null) {
            return null;
        }
        Conversation conversation = info.getConversation();
        if (conversation.getConversationType() == Conversation.ConversationType.SYSTEM) {
            return null;
        }

        UiConversation ui = UiConversation.fromConversationInfo(info);

        if (conversation.getConversationType() == Conversation.ConversationType.GROUP) {
            GroupInfo groupInfo = gateway.getGroupInfo(trimToEmpty(conversation.getConversationId()));
            if (groupInfo != null) {
                ui.setName(trimToEmpty(groupInfo.getGroupName()));
                ui.setAvatar(trimToEmpty(groupInfo.getPortrait()));
            }
            Message lastMessage = info.getLastMessage();
            if (lastMessage != null) {
                UserInfo senderInfo = gateway.getUserInfo(trimToEmpty(lastMessage.getSenderUserId()));
                if (senderInfo != null) {
                    ui.setLastMessageUserName(trimToEmpty(senderInfo.getUserName()));
                }
            }
        } else if (conversation.getConversationType() == Conversation.ConversationType.PRIVATE) {
            UserInfo userInfo = gateway.getUserInfo(trimToEmpty(conversation.getConversationId()));
            if (userInfo != null) {
                ui.setName(trimToEmpty(userInfo.getUserName()));
                ui.setAvatar(trimToEmpty(userInfo.getPortrait()));
                ui.setLastMessageUserName(trimToEmpty(userInfo.getUserName()));
            }
        }

        if (trimToEmpty(ui.getName()).isEmpty()) {
            ui.setName(trimToEmpty(conversation.getConversationId()));
        }
        return ui;
    }

    private void upsertToMap(@NonNull Map<String, UiConversation> target,
            @Nullable List<UiConversation> source) {
        if (source == null || source.isEmpty()) {
            return;
        }
        for (UiConversation conversation : source) {
            if (conversation == null) {
                continue;
            }
            String id = trimToEmpty(conversation.getId());
            if (id.isEmpty()) {
                continue;
            }
            target.put(id, conversation);
        }
    }

    @NonNull
    private Conversation requireConversation(@NonNull UiConversation uiConversation) {
        ConversationInfo info = uiConversation.getConversationInfo();
        if (info != null && info.getConversation() != null) {
            return info.getConversation();
        }
        String conversationId = trimToEmpty(uiConversation.getId());
        if (conversationId.isEmpty()) {
            throw new IllegalArgumentException("conversationId 不能为空");
        }
        return new Conversation(uiConversation.getConversationType(), conversationId);
    }

    @NonNull
    private static String trimToEmpty(@Nullable String value) {
        return value == null ? "" : value.trim();
    }

    private static final class JuggleGateway implements Gateway {

        @Nullable
        @Override
        public List<ConversationInfo> getConversationInfoList(int pageSize, long cursor,
                @NonNull JIMConst.PullDirection direction) {
            return JIM.getInstance().getConversationManager()
                    .getConversationInfoList(pageSize, cursor, direction);
        }

        @Nullable
        @Override
        public GroupInfo getGroupInfo(@NonNull String groupId) {
            return JIM.getInstance().getUserInfoManager().getGroupInfo(groupId);
        }

        @Nullable
        @Override
        public UserInfo getUserInfo(@NonNull String userId) {
            return JIM.getInstance().getUserInfoManager().getUserInfo(userId);
        }

        @Override
        public void clearUnreadCount(@NonNull Conversation conversation) {
            JIM.getInstance().getConversationManager().clearUnreadCount(conversation, null);
        }

        @Override
        public void setTop(@NonNull Conversation conversation, boolean isTop) {
            JIM.getInstance().getConversationManager().setTop(conversation, isTop, null);
        }

        @Override
        public void setMute(@NonNull Conversation conversation, boolean isMute) {
            JIM.getInstance().getConversationManager().setMute(conversation, isMute, null);
        }

        @Override
        public void deleteConversation(@NonNull Conversation conversation) {
            JIM.getInstance().getConversationManager().deleteConversationInfo(conversation, null);
        }
    }
}

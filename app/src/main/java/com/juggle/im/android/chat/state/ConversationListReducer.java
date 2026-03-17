package com.juggle.im.android.chat.state;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.juggle.im.android.chat.domain.ConversationRepository;
import com.juggle.im.android.model.UiConversation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 会话列表 Reducer：将会话列表状态流收敛为单向数据流。
 * <p>
 * 复杂逻辑说明：
 * - 所有会话合并统一走 ConversationRepository.mergeSnapshot，确保排序规则不漂移。
 * - Reducer 只做纯状态计算，不直接触发 SDK 调用，便于测试回归。
 */
public final class ConversationListReducer {

    public interface Action {
    }

    /**
     * 接收会话增量并合并到当前快照。
     */
    public static final class ConversationsMerged implements Action {
        private final List<UiConversation> conversations;

        public ConversationsMerged(@Nullable List<UiConversation> conversations) {
            this.conversations = immutableCopy(conversations);
        }

        @NonNull
        public List<UiConversation> getConversations() {
            return conversations;
        }
    }

    /**
     * 分页加载开始。
     */
    public static final class LoadMoreStarted implements Action {
    }

    /**
     * 分页加载成功。
     */
    public static final class LoadMoreSucceeded implements Action {
        private final List<UiConversation> page;
        private final int requestPageSize;

        public LoadMoreSucceeded(@Nullable List<UiConversation> page, int requestPageSize) {
            this.page = immutableCopy(page);
            this.requestPageSize = requestPageSize;
        }

        @NonNull
        public List<UiConversation> getPage() {
            return page;
        }

        public int getRequestPageSize() {
            return requestPageSize;
        }
    }

    /**
     * 分页加载失败。
     */
    public static final class LoadMoreFailed implements Action {
    }

    /**
     * 删除会话后的状态更新。
     */
    public static final class ConversationRemoved implements Action {
        private final String conversationId;

        public ConversationRemoved(@Nullable String conversationId) {
            this.conversationId = trimToEmpty(conversationId);
        }

        @NonNull
        public String getConversationId() {
            return conversationId;
        }
    }

    public static final class ConversationListState {
        private final List<UiConversation> conversations;
        private final boolean loadingMore;
        private final boolean hasMore;
        private final long cursor;

        private ConversationListState(@NonNull List<UiConversation> conversations,
                boolean loadingMore,
                boolean hasMore,
                long cursor) {
            this.conversations = conversations;
            this.loadingMore = loadingMore;
            this.hasMore = hasMore;
            this.cursor = cursor;
        }

        @NonNull
        public static ConversationListState initial() {
            return new ConversationListState(Collections.emptyList(), false, true, -1L);
        }

        @NonNull
        public List<UiConversation> getConversations() {
            return conversations;
        }

        public boolean isLoadingMore() {
            return loadingMore;
        }

        public boolean hasMore() {
            return hasMore;
        }

        public long getCursor() {
            return cursor;
        }
    }

    private final ConversationRepository conversationRepository;

    public ConversationListReducer() {
        this(new ConversationRepository());
    }

    public ConversationListReducer(@NonNull ConversationRepository conversationRepository) {
        this.conversationRepository = conversationRepository;
    }

    @NonNull
    public ConversationListState reduce(@Nullable ConversationListState current, @NonNull Action action) {
        ConversationListState state = current == null ? ConversationListState.initial() : current;

        if (action instanceof ConversationsMerged) {
            ConversationsMerged mergedAction = (ConversationsMerged) action;
            List<UiConversation> merged = conversationRepository.mergeSnapshot(
                    state.getConversations(), mergedAction.getConversations());
            return new ConversationListState(
                    immutableCopy(merged),
                    state.isLoadingMore(),
                    state.hasMore(),
                    resolveCursor(merged));
        }

        if (action instanceof LoadMoreStarted) {
            if (state.isLoadingMore() || !state.hasMore()) {
                return state;
            }
            return new ConversationListState(
                    state.getConversations(),
                    true,
                    state.hasMore(),
                    state.getCursor());
        }

        if (action instanceof LoadMoreSucceeded) {
            LoadMoreSucceeded loadAction = (LoadMoreSucceeded) action;
            List<UiConversation> merged = conversationRepository.mergeSnapshot(
                    state.getConversations(), loadAction.getPage());
            boolean hasMore = loadAction.getPage().size() >= loadAction.getRequestPageSize();
            return new ConversationListState(
                    immutableCopy(merged),
                    false,
                    hasMore,
                    resolveCursor(merged));
        }

        if (action instanceof LoadMoreFailed) {
            return new ConversationListState(
                    state.getConversations(),
                    false,
                    state.hasMore(),
                    state.getCursor());
        }

        if (action instanceof ConversationRemoved) {
            ConversationRemoved removedAction = (ConversationRemoved) action;
            if (removedAction.getConversationId().isEmpty()) {
                return state;
            }
            List<UiConversation> next = new ArrayList<>();
            for (UiConversation uiConversation : state.getConversations()) {
                if (!removedAction.getConversationId().equals(uiConversation.getId())) {
                    next.add(uiConversation);
                }
            }
            if (next.size() == state.getConversations().size()) {
                return state;
            }
            return new ConversationListState(
                    immutableCopy(next),
                    state.isLoadingMore(),
                    state.hasMore(),
                    resolveCursor(next));
        }

        return state;
    }

    private static long resolveCursor(@NonNull List<UiConversation> conversations) {
        if (conversations.isEmpty()) {
            return -1L;
        }
        return conversations.get(conversations.size() - 1).getSortTime();
    }

    @NonNull
    private static List<UiConversation> immutableCopy(@Nullable List<UiConversation> source) {
        if (source == null || source.isEmpty()) {
            return Collections.emptyList();
        }
        return Collections.unmodifiableList(new ArrayList<>(source));
    }

    @NonNull
    private static String trimToEmpty(@Nullable String value) {
        return value == null ? "" : value.trim();
    }
}

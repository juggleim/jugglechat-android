package com.juggle.im.android.chat;

import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.juggle.im.JIM;
import com.juggle.im.JIMConst;
import com.juggle.im.android.server.beans.FriendBean;
import com.juggle.im.model.Conversation;
import com.juggle.im.model.ConversationInfo;
import com.juggle.im.model.UserInfo;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 本地用户搜索编排器：统一从本地会话快照构建用户索引，并提供异步搜索能力。
 */
public final class LocalUserSearchCoordinator {

    private static final int PAGE_SIZE = 100;
    private static final int MAX_SCAN_PAGES = 80;
    private static final long SNAPSHOT_CACHE_TTL_MS = 15_000L;

    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "local-user-search");
        thread.setDaemon(true);
        return thread;
    });
    private static final Handler MAIN_HANDLER = new Handler(Looper.getMainLooper());
    private static final Object CACHE_LOCK = new Object();
    private static final List<FriendBean> CACHED_USERS = new ArrayList<>();

    private static long cachedAtElapsed = 0L;

    private LocalUserSearchCoordinator() {
    }

    /**
     * 本地用户搜索回调。
     */
    public interface Callback {

        /**
         * 搜索成功回调。
         *
         * @param users 命中的本地用户列表
         */
        void onSuccess(@NonNull List<FriendBean> users);

        /**
         * 搜索失败回调。
         *
         * @param message 失败原因
         */
        void onError(@NonNull String message);
    }

    /**
     * 异步执行本地用户搜索。
     *
     * @param keyword  搜索关键词，支持匹配昵称、用户 ID、手机号
     * @param limit    结果上限，小于等于 0 表示不限制
     * @param callback 搜索回调（主线程回调）
     */
    public static void searchUsers(@Nullable String keyword, int limit, @NonNull Callback callback) {
        String normalizedKeyword = normalizeKeyword(keyword);
        if (normalizedKeyword.isEmpty()) {
            MAIN_HANDLER.post(() -> callback.onSuccess(new ArrayList<>()));
            return;
        }

        EXECUTOR.execute(() -> {
            try {
                List<FriendBean> snapshot = getSnapshotUsers();
                List<FriendBean> result = filterUsers(snapshot, normalizedKeyword, limit);
                MAIN_HANDLER.post(() -> callback.onSuccess(result));
            } catch (Throwable throwable) {
                String errorMessage = resolveErrorMessage(throwable);
                MAIN_HANDLER.post(() -> callback.onError(errorMessage));
            }
        });
    }

    private static List<FriendBean> getSnapshotUsers() {
        long now = SystemClock.elapsedRealtime();
        synchronized (CACHE_LOCK) {
            if (!CACHED_USERS.isEmpty() && now - cachedAtElapsed < SNAPSHOT_CACHE_TTL_MS) {
                return cloneUsers(CACHED_USERS);
            }
        }

        List<FriendBean> rebuilt = buildSnapshotUsers();
        synchronized (CACHE_LOCK) {
            CACHED_USERS.clear();
            CACHED_USERS.addAll(rebuilt);
            cachedAtElapsed = now;
            return cloneUsers(CACHED_USERS);
        }
    }

    /**
     * 复杂逻辑简要描述：
     * 通过会话分页扫描本地会话数据库，提取私聊会话用户 ID，再回查本地用户缓存补齐昵称与头像。
     */
    private static List<FriendBean> buildSnapshotUsers() {
        String currentUserId = trimToEmpty(JIM.getInstance().getCurrentUserId());
        Set<String> uniqueUserIds = new LinkedHashSet<>();

        long cursor = -1L;
        for (int page = 0; page < MAX_SCAN_PAGES; page++) {
            List<ConversationInfo> conversationInfos = JIM.getInstance()
                    .getConversationManager()
                    .getConversationInfoList(PAGE_SIZE, cursor, JIMConst.PullDirection.NEWER);
            if (conversationInfos == null || conversationInfos.isEmpty()) {
                break;
            }

            for (ConversationInfo info : conversationInfos) {
                if (info == null || info.getConversation() == null) {
                    continue;
                }
                Conversation conversation = info.getConversation();
                if (conversation.getConversationType() != Conversation.ConversationType.PRIVATE) {
                    continue;
                }
                String userId = trimToEmpty(conversation.getConversationId());
                if (userId.isEmpty() || TextUtils.equals(userId, currentUserId)) {
                    continue;
                }
                uniqueUserIds.add(userId);
            }

            long nextCursor = cursor;
            ConversationInfo last = conversationInfos.get(conversationInfos.size() - 1);
            if (last != null) {
                nextCursor = last.getSortTime();
            }
            if (conversationInfos.size() < PAGE_SIZE || nextCursor == cursor) {
                break;
            }
            cursor = nextCursor;
        }

        List<FriendBean> snapshot = new ArrayList<>(uniqueUserIds.size());
        for (String userId : uniqueUserIds) {
            UserInfo userInfo = JIM.getInstance().getUserInfoManager().getUserInfo(userId);
            FriendBean item = new FriendBean();
            item.setUser_id(userId);
            item.setNickname(userInfo == null ? "" : trimToEmpty(userInfo.getUserName()));
            item.setAvatar(userInfo == null ? "" : trimToEmpty(userInfo.getPortrait()));
            snapshot.add(item);
        }
        return snapshot;
    }

    private static List<FriendBean> filterUsers(@NonNull List<FriendBean> source, @NonNull String keyword, int limit) {
        int realLimit = limit <= 0 ? Integer.MAX_VALUE : limit;
        String lowerKeyword = keyword.toLowerCase(Locale.ROOT);
        List<FriendBean> matched = new ArrayList<>();
        for (FriendBean item : source) {
            if (item == null || !matchesKeyword(item, lowerKeyword)) {
                continue;
            }
            matched.add(copyUser(item));
            if (matched.size() >= realLimit) {
                break;
            }
        }
        return matched;
    }

    private static boolean matchesKeyword(@NonNull FriendBean item, @NonNull String lowerKeyword) {
        String nickname = trimToEmpty(item.getNickname());
        String userId = trimToEmpty(item.getUser_id());
        String phone = trimToEmpty(item.getPhone());
        return nickname.toLowerCase(Locale.ROOT).contains(lowerKeyword)
                || userId.toLowerCase(Locale.ROOT).contains(lowerKeyword)
                || phone.toLowerCase(Locale.ROOT).contains(lowerKeyword);
    }

    private static List<FriendBean> cloneUsers(@NonNull List<FriendBean> source) {
        List<FriendBean> cloned = new ArrayList<>(source.size());
        for (FriendBean item : source) {
            if (item == null) {
                continue;
            }
            cloned.add(copyUser(item));
        }
        return cloned;
    }

    private static FriendBean copyUser(@NonNull FriendBean source) {
        FriendBean target = new FriendBean();
        target.setUser_id(source.getUser_id());
        target.setNickname(source.getNickname());
        target.setPhone(source.getPhone());
        target.setAvatar(source.getAvatar());
        return target;
    }

    private static String normalizeKeyword(@Nullable String keyword) {
        return keyword == null ? "" : keyword.trim();
    }

    private static String trimToEmpty(@Nullable String value) {
        return value == null ? "" : value.trim();
    }

    private static String resolveErrorMessage(@NonNull Throwable throwable) {
        String message = trimToEmpty(throwable.getMessage());
        return message.isEmpty() ? throwable.getClass().getSimpleName() : message;
    }
}

package com.juggle.im.android.chat;

import android.text.TextUtils;

import com.juggle.im.android.server.beans.FriendBean;
import com.juggle.im.android.server.beans.GroupBean;
import com.juggle.im.model.SearchConversationsResult;

import java.util.ArrayList;
import java.util.List;
import com.juggle.im.android.i18n.AppRes;
import com.juggle.im.android.R;

final class SearchResultMapper {

    private SearchResultMapper() {
    }

    static List<SearchResult> mapFriendResults(List<FriendBean> source, int limit) {
        List<SearchResult> results = new ArrayList<>();
        if (source == null || source.isEmpty()) {
            return results;
        }
        int realLimit = normalizeLimit(limit, source.size());
        for (int i = 0; i < realLimit; i++) {
            FriendBean friend = source.get(i);
            if (friend == null || TextUtils.isEmpty(friend.getUser_id())) {
                continue;
            }
            results.add(new SearchResult(
                    friend.getUser_id(),
                    resolveFriendName(friend),
                    friend.getAvatar(),
                    SearchActivity.SEARCH_TYPE_CONTACT,
                    null));
        }
        return results;
    }

    static List<SearchResult> mapGroupResults(List<GroupBean> source, int limit) {
        List<SearchResult> results = new ArrayList<>();
        if (source == null || source.isEmpty()) {
            return results;
        }
        int realLimit = normalizeLimit(limit, source.size());
        for (int i = 0; i < realLimit; i++) {
            GroupBean group = source.get(i);
            if (group == null || TextUtils.isEmpty(group.getGroup_id())) {
                continue;
            }
            results.add(new SearchResult(
                    group.getGroup_id(),
                    resolveGroupName(group),
                    group.getGroup_portrait(),
                    SearchActivity.SEARCH_TYPE_GROUP,
                    null));
        }
        return results;
    }

    static List<SearchResult> mapRecordResults(List<SearchConversationsResult> source, int limit) {
        List<SearchResult> results = new ArrayList<>();
        if (source == null || source.isEmpty()) {
            return results;
        }
        int realLimit = normalizeLimit(limit, source.size());
        for (int i = 0; i < realLimit; i++) {
            SearchConversationsResult item = source.get(i);
            if (item == null || item.getConversationInfo() == null || item.getConversationInfo().getConversation() == null) {
                continue;
            }
            String conversationId = item.getConversationInfo().getConversation().getConversationId();
            SearchResult result = new SearchResult(
                    conversationId,
                    conversationId,
                    "",
                    SearchActivity.SEARCH_TYPE_RECORD,
                    AppRes.string(R.string.search_matched_count, item.getMatchedCount()));
            result.setConversation(item.getConversationInfo().getConversation());
            results.add(result);
        }
        return results;
    }

    private static int normalizeLimit(int limit, int size) {
        if (limit <= 0) {
            return size;
        }
        return Math.min(limit, size);
    }

    private static String resolveFriendName(FriendBean friend) {
        if (friend == null) {
            return "";
        }
        if (!TextUtils.isEmpty(friend.getNickname())) {
            return friend.getNickname();
        }
        if (!TextUtils.isEmpty(friend.getUser_id())) {
            return friend.getUser_id();
        }
        return friend.getPhone() == null ? "" : friend.getPhone();
    }

    private static String resolveGroupName(GroupBean group) {
        if (group == null) {
            return "";
        }
        if (!TextUtils.isEmpty(group.getGroup_name())) {
            return group.getGroup_name();
        }
        return group.getGroup_id() == null ? "" : group.getGroup_id();
    }
}

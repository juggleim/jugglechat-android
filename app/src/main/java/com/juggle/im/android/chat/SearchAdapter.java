package com.juggle.im.android.chat;

import android.content.Intent;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.style.ForegroundColorSpan;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.juggle.im.JIM;
import com.juggle.im.android.R;
import com.juggle.im.android.utils.AvatarUtils;
import com.juggle.im.model.Conversation;
import com.juggle.im.model.GroupInfo;
import com.juggle.im.model.UserInfo;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

public class SearchAdapter extends RecyclerView.Adapter<SearchAdapter.ViewHolder> {

    public interface OnMoreClickListener {
        void onMoreClick(String type);
    }

    private final LinkedHashMap<String, List<SearchResult>> resultsMap = new LinkedHashMap<>();
    private static final List<String> TYPE_ORDER = Arrays.asList(
            SearchActivity.SEARCH_TYPE_CONTACT,
            SearchActivity.SEARCH_TYPE_GROUP,
            SearchActivity.SEARCH_TYPE_RECORD);

    private OnMoreClickListener onMoreClickListener;
    private String keyword = "";

    public void setOnMoreClickListener(OnMoreClickListener onMoreClickListener) {
        this.onMoreClickListener = onMoreClickListener;
    }

    public void setKeyword(String keyword) {
        this.keyword = keyword == null ? "" : keyword.trim();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_search_result_nav, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        List<String> keys = getOrderedTypes();
        String type = keys.get(position);
        List<SearchResult> items = resultsMap.get(type);
        holder.bind(type, items, keyword, onMoreClickListener);
    }

    @Override
    public int getItemCount() {
        return getOrderedTypes().size();
    }

    public void clear() {
        if (resultsMap.isEmpty()) {
            return;
        }
        resultsMap.clear();
        notifyDataSetChanged();
    }

    public void submitResults(LinkedHashMap<String, List<SearchResult>> newResults, String keyword) {
        String newKeyword = keyword == null ? "" : keyword.trim();
        if (TextUtils.equals(this.keyword, newKeyword) && isSameResults(newResults)) {
            return;
        }
        this.keyword = newKeyword;
        resultsMap.clear();
        if (newResults != null && !newResults.isEmpty()) {
            resultsMap.putAll(newResults);
        }
        notifyDataSetChanged();
    }

    public void addResults(List<SearchResult> results, String type) {
        if (results == null || results.isEmpty()) {
            return;
        }
        resultsMap.put(type, results);
        notifyDataSetChanged();
    }

    private List<String> getOrderedTypes() {
        List<String> ordered = new ArrayList<>();
        for (String type : TYPE_ORDER) {
            if (resultsMap.containsKey(type)) {
                ordered.add(type);
            }
        }
        for (String key : resultsMap.keySet()) {
            if (!ordered.contains(key)) {
                ordered.add(key);
            }
        }
        return ordered;
    }

    private boolean isSameResults(LinkedHashMap<String, List<SearchResult>> newResults) {
        if (newResults == null) {
            return resultsMap.isEmpty();
        }
        if (resultsMap.size() != newResults.size()) {
            return false;
        }
        for (String key : TYPE_ORDER) {
            List<SearchResult> current = resultsMap.get(key);
            List<SearchResult> incoming = newResults.get(key);
            if (current == null && incoming == null) {
                continue;
            }
            if (current == null || incoming == null) {
                return false;
            }
            if (current.size() != incoming.size()) {
                return false;
            }
            for (int i = 0; i < current.size(); i++) {
                if (!isSameResult(current.get(i), incoming.get(i))) {
                    return false;
                }
            }
        }
        for (String key : resultsMap.keySet()) {
            if (TYPE_ORDER.contains(key)) {
                continue;
            }
            List<SearchResult> current = resultsMap.get(key);
            List<SearchResult> incoming = newResults.get(key);
            if (current == null && incoming == null) {
                continue;
            }
            if (current == null || incoming == null) {
                return false;
            }
            if (current.size() != incoming.size()) {
                return false;
            }
            for (int i = 0; i < current.size(); i++) {
                if (!isSameResult(current.get(i), incoming.get(i))) {
                    return false;
                }
            }
        }
        return true;
    }

    private boolean isSameResult(SearchResult left, SearchResult right) {
        if (left == right) {
            return true;
        }
        if (left == null || right == null) {
            return false;
        }
        return Objects.equals(left.getId(), right.getId())
                && Objects.equals(left.getType(), right.getType())
                && Objects.equals(left.getName(), right.getName())
                && Objects.equals(left.getAvatar(), right.getAvatar())
                && Objects.equals(left.getDescription(), right.getDescription());
    }

    static class ViewHolder extends RecyclerView.ViewHolder {

        ViewHolder(@NonNull View itemView) {
            super(itemView);
        }

        void bind(String type, List<SearchResult> items, String keyword, OnMoreClickListener onMoreClickListener) {
            TextView tvTitle = itemView.findViewById(R.id.search_header_title);
            View moreLayout = itemView.findViewById(R.id.layout_more);
            ViewGroup container = itemView.findViewById(R.id.search_result_container);

            tvTitle.setText(SearchActivity.searchTypeTitleRes(type));
            if (onMoreClickListener == null) {
                moreLayout.setVisibility(View.GONE);
                moreLayout.setOnClickListener(null);
            } else {
                moreLayout.setVisibility(View.VISIBLE);
                moreLayout.setOnClickListener(v -> onMoreClickListener.onMoreClick(type));
            }

            container.removeAllViews();
            if (items == null || items.isEmpty()) {
                return;
            }

            LayoutInflater inflater = LayoutInflater.from(itemView.getContext());
            for (int i = 0; i < items.size(); i++) {
                SearchResult item = items.get(i);
                View vItem = inflater.inflate(R.layout.item_member_extra, container, false);
                View itemRow = vItem.findViewById(R.id.item_row);
                ImageView memberImage = vItem.findViewById(R.id.img_member_avatar);
                TextView memberTitle = vItem.findViewById(R.id.tv_member_name);
                TextView memberContent = vItem.findViewById(R.id.tv_member_content);
                View itemDivider = vItem.findViewById(R.id.item_divider);

                DisplayMeta meta = resolveDisplayMeta(item);
                AvatarUtils.loadAvatar(memberImage, meta.avatar, meta.name);
                memberTitle.setText(highlightText(vItem, meta.name, keyword));

                if (TextUtils.isEmpty(item.getDescription())) {
                    memberContent.setVisibility(View.GONE);
                } else {
                    memberContent.setVisibility(View.VISIBLE);
                    memberContent.setText(styleDescription(vItem, item, keyword));
                }

                String displayName = meta.name;
                View clickTarget = itemRow == null ? vItem : itemRow;
                clickTarget.setOnClickListener(v -> openResult(v, item, displayName));
                itemDivider.setVisibility(i == items.size() - 1 ? View.GONE : View.VISIBLE);
                container.addView(vItem);
            }
        }

        private DisplayMeta resolveDisplayMeta(SearchResult item) {
            String name = item == null ? "" : item.getName();
            String avatar = item == null ? "" : item.getAvatar();

            if (item == null || TextUtils.isEmpty(item.getType())) {
                return new DisplayMeta(name, avatar);
            }
            if (!SearchActivity.SEARCH_TYPE_RECORD.equals(item.getType()) || item.getConversation() == null) {
                return new DisplayMeta(name, avatar);
            }

            if (item.getConversation().getConversationType() == Conversation.ConversationType.GROUP) {
                GroupInfo groupInfo = JIM.getInstance().getUserInfoManager().getGroupInfo(item.getId());
                if (groupInfo != null) {
                    if (!TextUtils.isEmpty(groupInfo.getGroupName())) {
                        name = groupInfo.getGroupName();
                    }
                    if (!TextUtils.isEmpty(groupInfo.getPortrait())) {
                        avatar = groupInfo.getPortrait();
                    }
                }
            } else {
                UserInfo userInfo = JIM.getInstance().getUserInfoManager().getUserInfo(item.getId());
                if (userInfo != null) {
                    if (!TextUtils.isEmpty(userInfo.getUserName())) {
                        name = userInfo.getUserName();
                    }
                    if (!TextUtils.isEmpty(userInfo.getPortrait())) {
                        avatar = userInfo.getPortrait();
                    }
                }
            }
            return new DisplayMeta(name, avatar);
        }

        private CharSequence styleDescription(View anchor, SearchResult item, String keyword) {
            String description = item.getDescription();
            if (TextUtils.isEmpty(description)) {
                return "";
            }
            SpannableStringBuilder builder = new SpannableStringBuilder(description);
            if (SearchActivity.SEARCH_TYPE_RECORD.equals(item.getType())) {
                int countEnd = findCountPrefixEnd(description);
                if (countEnd > 0) {
                    builder.setSpan(
                            new ForegroundColorSpan(ContextCompat.getColor(anchor.getContext(), R.color.app_primary)),
                            0,
                            countEnd,
                            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                }
            }
            applyKeywordHighlight(anchor, builder, description, keyword);
            return builder;
        }

        private CharSequence highlightText(View anchor, String text, String keyword) {
            if (TextUtils.isEmpty(text)) {
                return "";
            }
            SpannableStringBuilder builder = new SpannableStringBuilder(text);
            applyKeywordHighlight(anchor, builder, text, keyword);
            return builder;
        }

        private void applyKeywordHighlight(View anchor,
                                           SpannableStringBuilder builder,
                                           String source,
                                           String keyword) {
            if (TextUtils.isEmpty(source) || TextUtils.isEmpty(keyword)) {
                return;
            }
            String sourceLower = source.toLowerCase(Locale.getDefault());
            String keywordLower = keyword.toLowerCase(Locale.getDefault());
            int keywordLength = keywordLower.length();
            int start = 0;
            int color = ContextCompat.getColor(anchor.getContext(), R.color.app_primary);
            while (true) {
                int index = sourceLower.indexOf(keywordLower, start);
                if (index < 0) {
                    break;
                }
                builder.setSpan(
                        new ForegroundColorSpan(color),
                        index,
                        index + keywordLength,
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                start = index + keywordLength;
            }
        }

        private int findCountPrefixEnd(String description) {
            int index = 0;
            while (index < description.length() && Character.isDigit(description.charAt(index))) {
                index++;
            }
            return index;
        }

        private void openResult(View anchor, SearchResult item, String displayName) {
            if (item == null || TextUtils.isEmpty(item.getId()) || TextUtils.isEmpty(item.getType())) {
                return;
            }
            if (SearchActivity.SEARCH_TYPE_CONTACT.equals(item.getType())) {
                Conversation conversation = new Conversation(Conversation.ConversationType.PRIVATE, item.getId());
                JIM.getInstance().getConversationManager().clearUnreadCount(conversation, null);
                Intent intent = ConversationActivity.intentFor(anchor.getContext(), item.getId(), false, displayName);
                anchor.getContext().startActivity(intent);
                return;
            }
            if (SearchActivity.SEARCH_TYPE_GROUP.equals(item.getType())) {
                Conversation conversation = new Conversation(Conversation.ConversationType.GROUP, item.getId());
                JIM.getInstance().getConversationManager().clearUnreadCount(conversation, null);
                Intent intent = ConversationActivity.intentFor(anchor.getContext(), item.getId(), true, displayName);
                anchor.getContext().startActivity(intent);
                return;
            }
            if (item.getConversation() == null) {
                return;
            }
            JIM.getInstance().getConversationManager().clearUnreadCount(item.getConversation(), null);
            boolean isGroup = item.getConversation().getConversationType() == Conversation.ConversationType.GROUP;
            Intent intent = ConversationActivity.intentFor(anchor.getContext(), item.getId(), isGroup, displayName);
            anchor.getContext().startActivity(intent);
        }

        private static final class DisplayMeta {
            final String name;
            final String avatar;

            DisplayMeta(String name, String avatar) {
                this.name = name;
                this.avatar = avatar;
            }
        }
    }
}

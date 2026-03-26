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
import java.util.List;
import java.util.Locale;

class SearchMoreResultAdapter extends RecyclerView.Adapter<SearchMoreResultAdapter.ViewHolder> {

    private final List<SearchResult> items = new ArrayList<>();
    private String keyword = "";

    void setKeyword(String keyword) {
        this.keyword = keyword == null ? "" : keyword.trim();
    }

    void submit(List<SearchResult> newItems) {
        items.clear();
        if (newItems != null && !newItems.isEmpty()) {
            items.addAll(newItems);
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_member_extra, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        holder.bind(items.get(position), keyword, position == items.size() - 1);
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        private final ImageView avatarView;
        private final TextView nameView;
        private final TextView descView;
        private final View dividerView;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            avatarView = itemView.findViewById(R.id.img_member_avatar);
            nameView = itemView.findViewById(R.id.tv_member_name);
            descView = itemView.findViewById(R.id.tv_member_content);
            dividerView = itemView.findViewById(R.id.item_divider);
        }

        void bind(SearchResult item, String keyword, boolean lastItem) {
            View rowView = itemView.findViewById(R.id.item_row);
            DisplayMeta meta = resolveDisplayMeta(item);
            AvatarUtils.loadAvatar(avatarView, meta.avatar, meta.name);
            nameView.setText(highlightText(itemView, meta.name, keyword));

            if (TextUtils.isEmpty(item.getDescription())) {
                descView.setVisibility(View.GONE);
            } else {
                descView.setVisibility(View.VISIBLE);
                descView.setText(styleDescription(itemView, item, keyword));
            }

            String displayName = meta.name;
            View clickTarget = rowView == null ? itemView : rowView;
            clickTarget.setOnClickListener(v -> openResult(v, item, displayName));
            dividerView.setVisibility(lastItem ? View.GONE : View.VISIBLE);
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

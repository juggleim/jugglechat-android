package com.juggle.im.android.chat;

import static android.view.View.GONE;
import static android.view.View.VISIBLE;

import static com.juggle.im.android.chat.SearchActivity.SEARCH_TYPE_CONTACT;
import static com.juggle.im.android.chat.SearchActivity.SEARCH_TYPE_GROUP;

import android.content.Intent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.juggle.im.JIM;
import com.juggle.im.android.R;
import com.juggle.im.android.utils.AvatarUtils;
import com.juggle.im.model.Conversation;
import com.juggle.im.model.GroupInfo;
import com.juggle.im.model.UserInfo;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

public class SearchAdapter extends RecyclerView.Adapter<SearchAdapter.ViewHolder> {

    private LinkedHashMap<String, List<SearchResult>> resultsMap = new LinkedHashMap<>();

    private static final int VIEW_TYPE_ITEM = 0;
    private static final int VIEW_TYPE_ITEM_EXTRA = 1;

    public SearchAdapter() {
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_search_result_nav, parent, false);
        return new ViewHolder(view, viewType);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        List<String> keys = new ArrayList<>(resultsMap.keySet());
        String title = keys.get(position);
        List<SearchResult> r = resultsMap.get(title);
        holder.bind(title, r);
    }

    @Override
    public int getItemCount() {
        return resultsMap.size();
    }

    public void clear() {
        resultsMap.clear();
        notifyDataSetChanged();
    }

    public void addResults(List<SearchResult> results, String type) {
        if (results.isEmpty()) {
            return;
        }

        resultsMap.put(type, results);
        notifyDataSetChanged();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        private int type;
        private ImageView memberImage;
        private TextView memberTitle, memberContent;

        public ViewHolder(@NonNull View itemView, int type) {
            super(itemView);
            this.type = type;
        }

        public void bind(String title, List<SearchResult> items) {
            ViewGroup container = itemView.findViewById(R.id.search_result_container);
            LayoutInflater inflater = LayoutInflater.from(itemView.getContext());
            TextView tvTitle = itemView.findViewById(R.id.search_header_title);
            tvTitle.setText(title);
            container.removeAllViews();
            for (SearchResult item : items) {
                View vItem = inflater.inflate(R.layout.item_member_extra, container, false);
                memberImage = vItem.findViewById(R.id.img_member_avatar);
                memberTitle = vItem.findViewById(R.id.tv_member_name);
                memberContent = vItem.findViewById(R.id.tv_member_content);
                String name = item.getName(), url = item.getAvatar();
                if (item.getType().equals(SEARCH_TYPE_CONTACT)) {
                    vItem.setOnClickListener(v -> {
                        Conversation conversation = new Conversation(Conversation.ConversationType.PRIVATE, item.getId());
                        JIM.getInstance().getConversationManager().clearUnreadCount(conversation, null);
                        Intent intent = ConversationActivity.intentFor(v.getContext(), item.getId(), false, item.getName());
                        v.getContext().startActivity(intent);
                    });
                    AvatarUtils.loadAvatar(memberImage, item.getAvatar(), item.getName());
                } else if (item.getType().equals(SEARCH_TYPE_GROUP)) {
                    Conversation conversation = new Conversation(Conversation.ConversationType.GROUP, item.getId());
                    vItem.setOnClickListener(v -> {
                        JIM.getInstance().getConversationManager().clearUnreadCount(conversation, null);
                        Intent intent = ConversationActivity.intentFor(v.getContext(), item.getId(), true, item.getName());
                        v.getContext().startActivity(intent);
                    });
                    AvatarUtils.loadAvatar(memberImage, item.getAvatar(), item.getName());
                } else {
                    if (item.getConversation().getConversationType() == Conversation.ConversationType.GROUP) {
                        GroupInfo groupInfo = JIM.getInstance().getUserInfoManager().getGroupInfo(item.getId());
                        if (groupInfo != null) {
                            name = groupInfo.getGroupName();
                            url = groupInfo.getPortrait();
                        }
                    } else {
                        UserInfo userInfo = JIM.getInstance().getUserInfoManager().getUserInfo(item.getId());
                        if (userInfo != null) {
                            name = userInfo.getUserName();
                            url = userInfo.getPortrait();
                        }
                    }
                    AvatarUtils.loadAvatar(memberImage, url, name);
                    final String n = name;
                    vItem.setOnClickListener(v -> {
                        JIM.getInstance().getConversationManager().clearUnreadCount(item.getConversation(), null);
                        Intent intent = ConversationActivity.intentFor(v.getContext(), item.getId(), item.getConversation().getConversationType() == Conversation.ConversationType.GROUP, n);
                        v.getContext().startActivity(intent);
                    });
                }
                memberTitle.setText(name);
                if (item.getDescription() != null) {
                    memberContent.setVisibility(VISIBLE);
                    memberContent.setText(item.getDescription());
                } else {
                    memberContent.setVisibility(GONE);
                }
                container.addView(vItem);
            }
        }
    }
}
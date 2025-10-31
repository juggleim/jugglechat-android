package com.juggle.im.android.chat;

import android.annotation.SuppressLint;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.juggle.im.android.R;
import com.juggle.im.android.chat.utils.MessageUtils;
import com.juggle.im.android.model.UiConversation;
import com.juggle.im.android.utils.AvatarUtils;
import com.juggle.im.model.Message;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class ConversationListAdapter extends RecyclerView.Adapter<ConversationListAdapter.ViewHolder> {
    private final List<UiConversation> uiConversations = new ArrayList<>();
    private OnConversationClickListener listener;
    private final SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm", Locale.getDefault());

    public interface OnConversationClickListener {
        void onConversationClick(UiConversation uiConversation);
    }

    public void setOnConversationClickListener(OnConversationClickListener listener) {
        this.listener = listener;
    }

    /**
     * Incrementally upsert a list of conversations into the adapter.
     * New conversations will be inserted at the top (index 0).
     * Existing conversations (matched by id) will be updated in place.
     */
    public void upsertConversations(List<UiConversation> newConversations) {
        if (newConversations == null || newConversations.isEmpty()) return;
        for (UiConversation newUi : newConversations) {
            if (newUi == null) continue;

            // determine target position by sortTime (descending: newer first)
            long newSortTime = newUi.getSortTime();

            // find existing by id
            int existingIndex = -1;
            for (int i = 0; i < uiConversations.size(); i++) {
                UiConversation exist = uiConversations.get(i);
                if (exist.getId() != null && exist.getId().equals(newUi.getId())) {
                    existingIndex = i;
                    break;
                }
            }

            if (existingIndex >= 0) {
                // update data at existing index
                uiConversations.set(existingIndex, newUi);

                // compute new insertion index in the list after removal
                UiConversation removed = uiConversations.remove(existingIndex);

                int insertIndex = findInsertIndex(newUi.isTop(), newSortTime, uiConversations);

                uiConversations.add(insertIndex, removed);

                if (existingIndex != insertIndex) {
                    // notify move and then update content at new position
                    notifyItemMoved(existingIndex, insertIndex);
                    notifyItemChanged(insertIndex);
                } else {
                    // same position, just notify changed
                    notifyItemChanged(insertIndex);
                }
            } else {
                // new item: insert according to sortTime
                int insertIndex = findInsertIndex(newUi.isTop(), newSortTime, uiConversations);
                uiConversations.add(insertIndex, newUi);
                notifyItemInserted(insertIndex);
            }
        }
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_conversation_list, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        UiConversation uiConversation = uiConversations.get(position);
        holder.bind(uiConversation);
    }

    @Override
    public int getItemCount() {
        return uiConversations.size();
    }

    class ViewHolder extends RecyclerView.ViewHolder {
        private TextView nameView;
        private TextView timeView;
        private TextView lastMessageView;
        private ImageView muteView;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            nameView = itemView.findViewById(R.id.tv_name);
            timeView = itemView.findViewById(R.id.tv_time);
            lastMessageView = itemView.findViewById(R.id.tv_last_message);
            muteView = itemView.findViewById(R.id.iv_mute);

            itemView.setOnClickListener(v -> {
                int position = getAbsoluteAdapterPosition();
                if (position != RecyclerView.NO_POSITION && listener != null) {
                    listener.onConversationClick(uiConversations.get(position));
                }
            });
        }

        @SuppressLint("DefaultLocale")
        void bind(UiConversation uiConversation) {
            ImageView avatarView = itemView.findViewById(R.id.iv_avatar);
            TextView unreadDot = itemView.findViewById(R.id.unread_dot);

            AvatarUtils.loadAvatar(avatarView, uiConversation.getAvatar(), uiConversation.getName());

            // 设置名称
            nameView.setText(uiConversation.getName());

            // 设置时间
            timeView.setText(MessageUtils.formateConversationTime(uiConversation.getSortTime()));

            // 设置最后一条消息
            Message lastMessage = uiConversation.getLastMessage();
            if (lastMessage != null) {
                lastMessageView.setText(MessageUtils.formatChatListMessageSummary(itemView, uiConversation.getLastMessageUserName(), lastMessage));
            }

            // 设置免打扰图标
            muteView.setVisibility(uiConversation.isMuted() ? View.VISIBLE : View.GONE);

            // 未读红点（简单样式：如果 unreadCount > 0 则显示）
            if (uiConversation.getUnreadCount() > 0) {
                unreadDot.setVisibility(View.VISIBLE);
                unreadDot.setText(String.format("%d", uiConversation.getUnreadCount()));
            } else {
                unreadDot.setVisibility(View.GONE);
            }

            // pinned background
            if (uiConversation.isTop()) {
                itemView.setBackgroundResource(R.drawable.bg_pinned);
            } else {
                itemView.setBackgroundResource(android.R.color.transparent);
            }
        }
    }

    /**
     * Find insert index: pinned (isTop==true) conversations come first, and
     * within the pinned group conversations are ordered by sortTime desc. After
     * pinned items, non-pinned conversations are ordered by sortTime desc as well.
     */
    public static int findInsertIndex(boolean isTop, long sortTime, List<UiConversation> list) {
        for (int i = 0; i < list.size(); i++) {
            UiConversation exist = list.get(i);
            boolean existTop = exist.isTop();
            long existSort = exist.getSortTime();

            if (isTop && !existTop) {
                // new is pinned, existing is not -> insert before
                return i;
            } else if (!isTop && existTop) {
                // existing is pinned, new is not -> skip pinned items
                continue;
            } else {
                // same pinned status -> compare sortTime desc
                if (sortTime > existSort) return i;
            }
        }
        return list.size();
    }
}
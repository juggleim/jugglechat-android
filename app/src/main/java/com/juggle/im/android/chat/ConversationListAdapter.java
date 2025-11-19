package com.juggle.im.android.chat;

import static android.view.View.GONE;
import static android.view.View.VISIBLE;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.drawable.Drawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.juggle.im.JIM;
import com.juggle.im.android.R;
import com.juggle.im.android.chat.utils.MessageUtils;
import com.juggle.im.android.model.UiConversation;
import com.juggle.im.android.utils.AvatarUtils;
import com.juggle.im.model.Message;

import java.util.ArrayList;
import java.util.List;

public class ConversationListAdapter extends RecyclerView.Adapter<ConversationListAdapter.ViewHolder> {
    private final List<UiConversation> uiConversations = new ArrayList<>();
    private OnConversationClickListener listener;
    private int selectedPosition = -1;
    private Drawable selectableItemBackground;

    public interface OnConversationClickListener {
        void onConversationClick(UiConversation uiConversation);

        void onConversationLongClick(UiConversation uiConversation);
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
        
        // 获取系统点击效果
        if (selectableItemBackground == null) {
            int[] attrs = new int[]{android.R.attr.selectableItemBackground};
            Context context = parent.getContext();
            android.content.res.TypedArray typedArray = context.obtainStyledAttributes(attrs);
            selectableItemBackground = typedArray.getDrawable(0);
            typedArray.recycle();
        }
        
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        UiConversation uiConversation = uiConversations.get(position);
        holder.bind(uiConversation);
        
        // 设置选中状态
        if (position == selectedPosition) {
            holder.itemView.setBackgroundResource(R.color.selected);
        } else if (uiConversation.isTop()) {
            holder.itemView.setBackgroundResource(R.color.app_primary_inverse);
        } else {
            holder.itemView.setBackgroundResource(android.R.color.transparent);
        }
    }

    @Override
    public int getItemCount() {
        return uiConversations.size();
    }
    
    // 添加方法来清除选中状态
    public void clearSelectedPosition() {
        if (selectedPosition >= 0) {
            int previousPosition = selectedPosition;
            selectedPosition = -1;
            notifyItemChanged(previousPosition);
        }
    }
    
    // 添加方法来设置选中状态
    public void setSelectedPosition(int position) {
        // 清除之前的选中状态
        if (selectedPosition >= 0) {
            int previousPosition = selectedPosition;
            selectedPosition = -1;
            notifyItemChanged(previousPosition);
        }
        
        // 设置新的选中状态
        if (position >= 0) {
            selectedPosition = position;
            notifyItemChanged(position);
        }
    }

    class ViewHolder extends RecyclerView.ViewHolder {
        private TextView nameView;
        private TextView timeView;
        private TextView lastMessageView;
        private ImageView muteView;
        private ImageView avatarView;
        private TextView unreadDot;
        private ProgressBar progressBar;
        private ImageView ivMsgStatus;



        ViewHolder(@NonNull View itemView) {
            super(itemView);
            nameView = itemView.findViewById(R.id.tv_name);
            timeView = itemView.findViewById(R.id.tv_time);
            lastMessageView = itemView.findViewById(R.id.tv_last_message);
            muteView = itemView.findViewById(R.id.iv_mute);
            avatarView = itemView.findViewById(R.id.iv_avatar);
            unreadDot = itemView.findViewById(R.id.unread_dot);
            progressBar = itemView.findViewById(R.id.msg_progress);
            ivMsgStatus = itemView.findViewById(R.id.iv_msg_status);


            itemView.setOnClickListener(v -> {
                int position = getAbsoluteAdapterPosition();
                if (position != RecyclerView.NO_POSITION && listener != null) {
                    // 添加点击效果
                    if (selectableItemBackground != null) {
                        itemView.setBackground(selectableItemBackground);
                    }
                    
                    // 延迟一点时间后恢复原状
                    itemView.postDelayed(() -> {
                        if (position == selectedPosition) {
                            itemView.setBackgroundColor(itemView.getContext().getResources().getColor(R.color.gray));
                        } else if (uiConversations.size() > position && uiConversations.get(position).isTop()) {
                            itemView.setBackgroundResource(R.drawable.bg_pinned);
                        } else {
                            itemView.setBackgroundResource(android.R.color.transparent);
                        }
                    }, 100);
                    
                    listener.onConversationClick(uiConversations.get(position));
                }
            });
            itemView.setOnLongClickListener(v -> {
                int position = getAbsoluteAdapterPosition();
                if (position != RecyclerView.NO_POSITION && listener != null) {
                    listener.onConversationLongClick(uiConversations.get(position));
                    return true;
                }
                return false;
            });
        }

        @SuppressLint("DefaultLocale")
        void bind(UiConversation uiConversation) {
            AvatarUtils.loadAvatar(avatarView, uiConversation.getAvatar(), uiConversation.getName());

            // 设置名称
            nameView.setText(uiConversation.getName());

            // 设置时间
            timeView.setText(MessageUtils.formateConversationTime(uiConversation.getSortTime()));

            // 设置最后一条消息
            Message lastMessage = uiConversation.getLastMessage();
            if (lastMessage != null) {
                String senderName = lastMessage.getSenderUserId().equals(JIM.getInstance().getCurrentUserId()) ? "你" : uiConversation.getLastMessageUserName();
                lastMessageView.setText(MessageUtils.formatChatListMessageSummary(itemView, senderName, lastMessage));
            }

            // 设置免打扰图标
            muteView.setVisibility(uiConversation.isMuted() ? VISIBLE : GONE);

            // 未读红点（简单样式：如果 unreadCount > 0 则显示）
            if (uiConversation.getUnreadCount() > 0) {
                unreadDot.setVisibility(VISIBLE);
                unreadDot.setText(String.format("%d", uiConversation.getUnreadCount()));
            } else {
                unreadDot.setVisibility(GONE);
            }

            if (lastMessage.getState() == Message.MessageState.FAIL) {
                ivMsgStatus.setVisibility(VISIBLE);
                progressBar.setVisibility(GONE);
            } else if (lastMessage.getState() == Message.MessageState.SENT) {
                progressBar.setVisibility(GONE);
                ivMsgStatus.setVisibility(GONE);
            } else {
                progressBar.setVisibility(VISIBLE);
                ivMsgStatus.setVisibility(GONE);
            }
        }
    }

    /**
     * 获取会话在列表中的位置
     *
     * @param uiConversation 会话对象
     * @return 位置索引，未找到返回-1
     */
    public int getPosition(UiConversation uiConversation) {
        for (int i = 0; i < uiConversations.size(); i++) {
            if (uiConversations.get(i).getId().equals(uiConversation.getId())) {
                return i;
            }
        }
        return -1;
    }

    /**
     * 从列表中移除指定会话
     *
     * @param uiConversation 要移除的会话
     */
    public void removeConversation(UiConversation uiConversation) {
        int position = getPosition(uiConversation);
        if (position >= 0) {
            uiConversations.remove(position);
            notifyItemRemoved(position);
            
            // 如果删除的是选中的项目，清除选中状态
            if (position == selectedPosition) {
                selectedPosition = -1;
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
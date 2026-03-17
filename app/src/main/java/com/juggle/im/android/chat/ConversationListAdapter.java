package com.juggle.im.android.chat;

import static android.view.View.GONE;
import static android.view.View.VISIBLE;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
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
    private static final String TAG = "ConvListAdapter";
    private final List<UiConversation> uiConversations = new ArrayList<>();
    private OnConversationClickListener listener;
    private OnNewConversationListener newConversationListener;
    private RecyclerView recyclerView; // 持有RecyclerView引用,用于直接控制滚动
    private int selectedPosition = -1;
    private Drawable selectableItemBackground;

    public interface OnConversationClickListener {
        void onConversationClick(UiConversation uiConversation);

        void onConversationLongClick(UiConversation uiConversation);
    }

    /**
     * 新会话插入监听器，当新会话插入到顶部时回调
     */
    public interface OnNewConversationListener {
        void onNewConversationAtTop();
    }

    /**
     * 检查是否应该自动滚动的接口
     */
    public interface ShouldAutoScrollChecker {
        boolean shouldAutoScroll();
    }

    private ShouldAutoScrollChecker shouldAutoScrollChecker;

    public void setOnNewConversationListener(OnNewConversationListener listener) {
        this.newConversationListener = listener;
    }

    /**
     * 设置RecyclerView引用,用于直接控制滚动
     */
    public void setRecyclerView(RecyclerView recyclerView) {
        this.recyclerView = recyclerView;
    }

    /**
     * 设置自动滚动检查器
     */
    public void setShouldAutoScrollChecker(ShouldAutoScrollChecker checker) {
        this.shouldAutoScrollChecker = checker;
    }

    public void setOnConversationClickListener(OnConversationClickListener listener) {
        this.listener = listener;
    }

    /**
     * UDF 渲染入口：接收新的会话快照并增量合并到当前列表。
     */
    public void renderSnapshot(List<UiConversation> snapshot) {
        upsertConversations(snapshot);
    }

    /**
     * Incrementally upsert a list of conversations into the adapter.
     * Uses batched updates to minimize animations and prevent flickering.
     * New conversations will be inserted at the correct position based on sortTime.
     * Existing conversations (matched by id) will be updated in place or moved if position changed.
     */
    public void upsertConversations(List<UiConversation> newConversations) {
        if (newConversations == null || newConversations.isEmpty()) return;

        // Use a temporary map to track all updates first
        java.util.Map<String, UiConversation> updateMap = new java.util.LinkedHashMap<>();
        java.util.Map<String, Integer> existingPositions = new java.util.LinkedHashMap<>();

        // First pass: collect all existing positions and build update map
        for (int i = 0; i < uiConversations.size(); i++) {
            String id = uiConversations.get(i).getId();
            existingPositions.put(id, i);
        }

        // Second pass: categorize new items as updates or inserts
        for (UiConversation newUi : newConversations) {
            if (newUi == null) continue;
            updateMap.put(newUi.getId(), newUi);
        }

        // Process in order: handle updates first, then inserts
        List<String> insertedIds = new ArrayList<>();
        List<String> movedIds = new ArrayList<>();

        for (UiConversation newUi : newConversations) {
            if (newUi == null) continue;

            String id = newUi.getId();
            Integer existingIndex = existingPositions.get(id);

            if (existingIndex != null) {
                // Existing item: update data in place
                uiConversations.set(existingIndex, newUi);

                // Check if position should change
                int targetIndex = findInsertIndexExcluding(id, newUi.isTop(), newUi.getSortTime());
                if (targetIndex != existingIndex) {
                    movedIds.add(id);
                }
            } else {
                // New item: mark for insertion
                insertedIds.add(id);
            }
        }

        // Apply moves (from bottom to top to maintain indices)
        movedIds.sort((a, b) -> {
            int posA = uiConversations.indexOf(getConversationById(a));
            int posB = uiConversations.indexOf(getConversationById(b));
            return Integer.compare(posB, posA); // Descending order
        });

        for (String id : movedIds) {
            int currentPos = uiConversations.indexOf(getConversationById(id));
            if (currentPos >= 0) {
                UiConversation item = uiConversations.remove(currentPos);
                int targetPos = findInsertIndex(item.isTop(), item.getSortTime(), uiConversations);
                uiConversations.add(targetPos, item);
                notifyItemMoved(currentPos, targetPos);
                
                // 检查是否移动到了顶部,如果是且满足条件,则自动滚动
                boolean isMovingToTop = (targetPos == 0);
                if (isMovingToTop && recyclerView != null) {
                    // 检查是否应该自动滚动(用户是否在顶部且未主动滚动离开)
                    boolean shouldScroll = (shouldAutoScrollChecker == null) || shouldAutoScrollChecker.shouldAutoScroll();
                    android.util.Log.i(TAG, "[移动] 会话移动到顶部, shouldScroll=" + shouldScroll);
                    
                    if (shouldScroll) {
                        // 直接滚动,不等待RecyclerView的自动调整
                        LinearLayoutManager layoutManager = (LinearLayoutManager) recyclerView.getLayoutManager();
                        if (layoutManager != null) {
                            layoutManager.scrollToPositionWithOffset(0, 0);
                            android.util.Log.d(TAG, "[移动] 滚动完成");
                        }
                    }
                }
            }
        }

        // Apply inserts
        for (String id : insertedIds) {
            UiConversation newUi = updateMap.get(id);
            int insertIndex = findInsertIndex(newUi.isTop(), newUi.getSortTime(), uiConversations);
            
            // 在插入之前先通知监听器(此时可以准确判断是否在顶部)
            boolean isInsertingAtTop = (insertIndex == 0);
            
            uiConversations.add(insertIndex, newUi);
            notifyItemInserted(insertIndex);

            // 如果新会话插入到顶部,检查是否应该自动滚动
            // 关键:在notifyItemInserted之后立即滚动,不使用post延迟
            if (isInsertingAtTop && recyclerView != null) {
                // 检查是否应该自动滚动(用户是否在顶部且未主动滚动离开)
                boolean shouldScroll = (shouldAutoScrollChecker == null) || shouldAutoScrollChecker.shouldAutoScroll();
                android.util.Log.i(TAG, "[插入] 新会话插入到顶部, shouldScroll=" + shouldScroll);
                
                if (shouldScroll) {
                    // 直接滚动,不等待RecyclerView的自动调整
                    LinearLayoutManager layoutManager = (LinearLayoutManager) recyclerView.getLayoutManager();
                    if (layoutManager != null) {
                        layoutManager.scrollToPositionWithOffset(0, 0);
                        android.util.Log.d(TAG, "[插入] 滚动完成");
                    }
                } else {
                    android.util.Log.d(TAG, "[插入] 用户不在顶部或已滚动离开,不自动滚动");
                }
            }
            
            // 仍然通知监听器(用于其他逻辑,如用户滚动状态追踪)
            if (isInsertingAtTop && newConversationListener != null) {
                newConversationListener.onNewConversationAtTop();
            }
        }

        // Notify changed for all updated items (including moved ones)
        for (String id : updateMap.keySet()) {
            Integer pos = existingPositions.get(id);
            if (pos != null) {
                // Check if this item was moved - if so, use the new position
                int newPos = uiConversations.indexOf(getConversationById(id));
                notifyItemChanged(newPos);
            }
        }
    }

    /**
     * Find insert index excluding a specific ID (used to avoid self-comparison)
     */
    private int findInsertIndexExcluding(String excludeId, boolean isTop, long sortTime) {
        for (int i = 0; i < uiConversations.size(); i++) {
            UiConversation exist = uiConversations.get(i);
            if (exist.getId().equals(excludeId)) continue;

            boolean existTop = exist.isTop();
            long existSort = exist.getSortTime();

            if (isTop && !existTop) {
                return i;
            } else if (!isTop && existTop) {
                continue;
            } else {
                if (sortTime > existSort) return i;
            }
        }
        return uiConversations.size();
    }

    /**
     * Helper to get conversation by ID
     */
    private UiConversation getConversationById(String id) {
        for (UiConversation ui : uiConversations) {
            if (ui.getId().equals(id)) {
                return ui;
            }
        }
        return null;
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
                if (uiConversation.getConversationInfo().getMentionInfo() != null) {
                    SpannableString spannable = new SpannableString("[有人@我]" + MessageUtils.formatChatListMessageSummary(itemView, senderName, lastMessage));
                    spannable.setSpan(
                            new ForegroundColorSpan(Color.RED),
                            0,
                            6,
                            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    lastMessageView.setText(spannable);
                } else {
                    lastMessageView.setText(MessageUtils.formatChatListMessageSummary(itemView, senderName, lastMessage));
                }
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

            if (lastMessage != null) {
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

    /**
     * 获取列表中最后一个会话的sortTime，用于分页加载
     * @return 最后一个会话的sortTime，如果列表为空返回-1
     */
    public long getLastSortTime() {
        if (uiConversations.isEmpty()) {
            return -1;
        }
        return uiConversations.get(uiConversations.size() - 1).getSortTime();
    }
}

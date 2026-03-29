package com.juggle.im.android.chat;

import static android.view.View.GONE;
import static android.view.View.VISIBLE;

import android.annotation.SuppressLint;
import android.graphics.RenderEffect;
import android.graphics.Shader;
import android.os.Build;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextUtils;
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
import com.juggle.im.model.ConversationInfo;
import com.juggle.im.model.ConversationMentionInfo;
import com.juggle.im.model.Message;
import com.juggle.im.model.MessageMentionInfo;

import java.util.ArrayList;
import java.util.List;

public class ConversationListAdapter extends RecyclerView.Adapter<ConversationListAdapter.ViewHolder> {
    private static final String TAG = "ConvListAdapter";
    private final List<UiConversation> uiConversations = new ArrayList<>();
    private OnConversationClickListener listener;
    private OnNewConversationListener newConversationListener;
    private RecyclerView recyclerView; // 持有RecyclerView引用,用于直接控制滚动
    private int selectedPosition = -1;

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
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        UiConversation uiConversation = uiConversations.get(position);
        holder.bind(uiConversation);
        updateSelectionVisuals(holder, position, uiConversation);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position, @NonNull List<Object> payloads) {
        if (payloads.contains("selection")) {
            // 只更新选中状态的视觉效果，不重新绑定数据（避免头像闪烁）
            UiConversation uiConversation = uiConversations.get(position);
            updateSelectionVisuals(holder, position, uiConversation);
        } else {
            // 完整绑定
            super.onBindViewHolder(holder, position, payloads);
        }
    }

    private void updateSelectionVisuals(@NonNull ViewHolder holder, int position, UiConversation uiConversation) {
        // 长按弹窗打开时：被选中会话保持清晰，其它会话弱化并虚化。
        if (selectedPosition >= 0) {
            boolean isSelected = position == selectedPosition;
            holder.itemView.setAlpha(isSelected ? 1f : 0.35f);
            applyCardMargins(holder.itemView, isSelected);
            holder.itemView.setBackgroundResource(isSelected
                    ? R.drawable.bg_conversation_item_floating
                    : R.color.white);
            holder.itemView.setScaleX(isSelected ? 1.02f : 1f);
            holder.itemView.setScaleY(isSelected ? 1.02f : 1f);
            holder.itemView.setTranslationY(isSelected ? -dpToPx(holder.itemView, 3f) : 0f);
            holder.setDividerVisible(!isSelected);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                holder.itemView.setElevation(isSelected ? dpToPx(holder.itemView, 18f) : 0f);
                holder.itemView.setTranslationZ(isSelected ? dpToPx(holder.itemView, 10f) : 0f);
                holder.itemView.setClipToOutline(isSelected);
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                holder.itemView.setRenderEffect(isSelected
                        ? null
                        : RenderEffect.createBlurEffect(6f, 6f, Shader.TileMode.CLAMP));
            }
        } else {
            holder.itemView.setAlpha(1f);
            applyCardMargins(holder.itemView, false);
            // 置顶会话使用浅灰色背景，非置顶使用白色背景
            holder.itemView.setBackgroundResource(uiConversation.isTop()
                    ? R.color.conversation_top_bg
                    : R.color.white);
            holder.itemView.setScaleX(1f);
            holder.itemView.setScaleY(1f);
            holder.itemView.setTranslationY(0f);
            holder.setDividerVisible(true);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                holder.itemView.setElevation(0f);
                holder.itemView.setTranslationZ(0f);
                holder.itemView.setClipToOutline(false);
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                holder.itemView.setRenderEffect(null);
            }
        }
    }

    @Override
    public int getItemCount() {
        return uiConversations.size();
    }
    
    // 添加方法来清除选中状态
    public void clearSelectedPosition() {
        if (selectedPosition >= 0) {
            selectedPosition = -1;
            // 直接遍历可见的 ViewHolder 更新视觉效果，避免 notify 导致的重新绑定
            if (recyclerView != null) {
                int childCount = recyclerView.getChildCount();
                for (int i = 0; i < childCount; i++) {
                    View child = recyclerView.getChildAt(i);
                    RecyclerView.ViewHolder holder = recyclerView.getChildViewHolder(child);
                    if (holder instanceof ViewHolder) {
                        int position = holder.getAbsoluteAdapterPosition();
                        if (position != RecyclerView.NO_POSITION) {
                            UiConversation uiConversation = uiConversations.get(position);
                            updateSelectionVisualsForView((ViewHolder) holder, uiConversation);
                        }
                    }
                }
            }
        }
    }

    // 添加方法来设置选中状态
    public void setSelectedPosition(int position) {
        int target = position >= 0 ? position : -1;
        if (selectedPosition != target) {
            // 保存旧位置和新位置
            int oldPosition = selectedPosition;
            selectedPosition = target;

            // 直接遍历可见的 ViewHolder 更新视觉效果
            if (recyclerView != null) {
                int childCount = recyclerView.getChildCount();
                for (int i = 0; i < childCount; i++) {
                    View child = recyclerView.getChildAt(i);
                    RecyclerView.ViewHolder holder = recyclerView.getChildViewHolder(child);
                    if (holder instanceof ViewHolder) {
                        int pos = holder.getAbsoluteAdapterPosition();
                        if (pos != RecyclerView.NO_POSITION) {
                            UiConversation uiConversation = uiConversations.get(pos);
                            updateSelectionVisualsForView((ViewHolder) holder, uiConversation);
                        }
                    }
                }
            }
        }
    }

    /**
     * 直接更新单个 ViewHolder 的选中状态视觉效果，不触发重新绑定
     */
    private void updateSelectionVisualsForView(ViewHolder holder, UiConversation uiConversation) {
        int position = holder.getAbsoluteAdapterPosition();
        if (position == RecyclerView.NO_POSITION) return;

        if (selectedPosition >= 0) {
            boolean isSelected = position == selectedPosition;
            holder.itemView.setAlpha(isSelected ? 1f : 0.35f);
            applyCardMargins(holder.itemView, isSelected);
            holder.itemView.setBackgroundResource(isSelected
                    ? R.drawable.bg_conversation_item_floating
                    : R.color.white);
            holder.itemView.setScaleX(isSelected ? 1.02f : 1f);
            holder.itemView.setScaleY(isSelected ? 1.02f : 1f);
            holder.itemView.setTranslationY(isSelected ? -dpToPx(holder.itemView, 3f) : 0f);
            holder.setDividerVisible(!isSelected);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                holder.itemView.setElevation(isSelected ? dpToPx(holder.itemView, 18f) : 0f);
                holder.itemView.setTranslationZ(isSelected ? dpToPx(holder.itemView, 10f) : 0f);
                holder.itemView.setClipToOutline(isSelected);
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                holder.itemView.setRenderEffect(isSelected
                        ? null
                        : RenderEffect.createBlurEffect(6f, 6f, Shader.TileMode.CLAMP));
            }
        } else {
            holder.itemView.setAlpha(1f);
            applyCardMargins(holder.itemView, false);
            holder.itemView.setBackgroundResource(uiConversation.isTop()
                    ? R.color.conversation_top_bg
                    : R.color.white);
            holder.itemView.setScaleX(1f);
            holder.itemView.setScaleY(1f);
            holder.itemView.setTranslationY(0f);
            holder.setDividerVisible(true);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                holder.itemView.setElevation(0f);
                holder.itemView.setTranslationZ(0f);
                holder.itemView.setClipToOutline(false);
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                holder.itemView.setRenderEffect(null);
            }
        }
    }

    class ViewHolder extends RecyclerView.ViewHolder {
        private TextView nameView;
        private TextView timeView;
        private TextView lastMessageView;
        private ImageView muteView;
        private ImageView avatarView;
        private TextView unreadCountView;
        private View mutedUnreadDotView;
        private ProgressBar progressBar;
        private ImageView ivMsgStatus;
        private View itemDivider;



        ViewHolder(@NonNull View itemView) {
            super(itemView);
            nameView = itemView.findViewById(R.id.tv_name);
            timeView = itemView.findViewById(R.id.tv_time);
            lastMessageView = itemView.findViewById(R.id.tv_last_message);
            muteView = itemView.findViewById(R.id.iv_mute);
            avatarView = itemView.findViewById(R.id.iv_avatar);
            unreadCountView = itemView.findViewById(R.id.tv_unread_count);
            mutedUnreadDotView = itemView.findViewById(R.id.v_muted_unread_dot);
            progressBar = itemView.findViewById(R.id.msg_progress);
            ivMsgStatus = itemView.findViewById(R.id.iv_msg_status);
            itemDivider = itemView.findViewById(R.id.item_divider);


            itemView.setOnClickListener(v -> {
                int position = getAbsoluteAdapterPosition();
                if (position != RecyclerView.NO_POSITION && listener != null) {
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
            AvatarUtils.loadAvatar(avatarView, uiConversation.getAvatar(), uiConversation.getName(), uiConversation.getId());

            // 设置名称
            nameView.setText(uiConversation.getName());

            // 设置时间
            timeView.setText(MessageUtils.formateConversationTime(uiConversation.getSortTime()));

            // 设置最后一条消息
            Message lastMessage = uiConversation.getLastMessage();
            String draft = uiConversation.getDraft();
            ConversationInfo conversationInfo = uiConversation.getConversationInfo();
            if (!TextUtils.isEmpty(draft)) {
                // 简要描述：会话存在草稿时，摘要区域始终优先显示草稿，不被新消息摘要覆盖。
                lastMessageView.setText(buildDraftSummary(draft));
            } else if (lastMessage != null) {
                String senderName = lastMessage.getSenderUserId().equals(JIM.getInstance().getCurrentUserId()) ? "你" : uiConversation.getLastMessageUserName();
                // 处理 @提及 显示
                String mentionPrefix = getMentionPrefix(conversationInfo);
                if (mentionPrefix != null) {
                    SpannableString spannable = new SpannableString(mentionPrefix + MessageUtils.formatChatListMessageSummary(itemView, senderName, lastMessage));
                    spannable.setSpan(
                            new ForegroundColorSpan(itemView.getResources().getColor(R.color.conversation_badge_red)),
                            0,
                            mentionPrefix.length(),
                            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    lastMessageView.setText(spannable);
                } else {
                    lastMessageView.setText(MessageUtils.formatChatListMessageSummary(itemView, senderName, lastMessage));
                }
            } else {
                lastMessageView.setText("");
            }

            // 设置免打扰图标
            muteView.setVisibility(uiConversation.isMuted() ? VISIBLE : GONE);

            // 未读展示规则：
            // 1) 非免打扰：显示数量胶囊（最大 99+）
            // 2) 免打扰：仅显示红点，不显示数量
            int unreadCount = uiConversation.getUnreadCount();
            if (unreadCount > 0) {
                if (uiConversation.isMuted()) {
                    unreadCountView.setVisibility(GONE);
                    mutedUnreadDotView.setVisibility(VISIBLE);
                } else {
                    mutedUnreadDotView.setVisibility(GONE);
                    unreadCountView.setVisibility(VISIBLE);
                    boolean overflow = unreadCount > 99;
                    unreadCountView.setMinWidth(dpToPx(overflow ? 30 : 18));
                    unreadCountView.setText(overflow ? "99+" : String.format("%d", unreadCount));
                }
            } else {
                unreadCountView.setVisibility(GONE);
                mutedUnreadDotView.setVisibility(GONE);
            }

            if (!TextUtils.isEmpty(draft)) {
                progressBar.setVisibility(GONE);
                ivMsgStatus.setVisibility(GONE);
            } else if (lastMessage != null) {
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
            } else {
                progressBar.setVisibility(GONE);
                ivMsgStatus.setVisibility(GONE);
            }
        }

        /**
         * 构建会话草稿摘要文案，并将前缀 [草稿] 渲染为红色。
         */
        @NonNull
        private CharSequence buildDraftSummary(@NonNull String rawDraft) {
            String summary = rawDraft.replace('\n', ' ').trim();
            String prefix = "[草稿]";
            SpannableString spannable = new SpannableString(prefix + summary);
            spannable.setSpan(
                    new ForegroundColorSpan(itemView.getResources().getColor(R.color.conversation_badge_red)),
                    0,
                    prefix.length(),
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            return spannable;
        }

        private int dpToPx(int dp) {
            float density = itemView.getResources().getDisplayMetrics().density;
            return Math.round(dp * density);
        }

        void setDividerVisible(boolean visible) {
            itemDivider.setVisibility(visible ? VISIBLE : GONE);
        }
    }

    private float dpToPx(@NonNull View view, float dp) {
        float density = view.getResources().getDisplayMetrics().density;
        return dp * density;
    }

    /**
     * 获取 @提及 前缀文本
     * 根据 MentionType 返回不同的前缀：
     * - ALL: "[@所有人]"
     * - SOMEONE: "[有人@我]"
     * - ALL_AND_SOMEONE: "[@所有人]"
     * - 其他: null
     *
     * @param conversationInfo 会话信息
     * @return 前缀文本，如果没有 @提及 返回 null
     */
    private String getMentionPrefix(ConversationInfo conversationInfo) {
        if (conversationInfo == null || conversationInfo.getMentionInfo() == null) {
            return null;
        }

        ConversationMentionInfo mentionInfo = conversationInfo.getMentionInfo();
        List<ConversationMentionInfo.MentionMsg> mentionMsgList = mentionInfo.getMentionMsgList();

        if (mentionMsgList == null || mentionMsgList.isEmpty()) {
            return null;
        }

        // 获取最后一条 @消息 的类型
        ConversationMentionInfo.MentionMsg lastMentionMsg = mentionMsgList.get(mentionMsgList.size() - 1);
        MessageMentionInfo.MentionType type = lastMentionMsg.getType();

        if (type == MessageMentionInfo.MentionType.ALL) {
            return "[@所有人]";
        } else if (type == MessageMentionInfo.MentionType.SOMEONE) {
            return "[有人@我]";
        } else if (type == MessageMentionInfo.MentionType.ALL_AND_SOMEONE) {
            return "[@所有人]";
        }

        return "[有人@我]";
    }

    private void applyCardMargins(@NonNull View itemView, boolean isCardStyle) {
        ViewGroup.LayoutParams params = itemView.getLayoutParams();
        if (!(params instanceof RecyclerView.LayoutParams)) {
            return;
        }
        RecyclerView.LayoutParams layoutParams = (RecyclerView.LayoutParams) params;
        int horizontal = Math.round(dpToPx(itemView, isCardStyle ? 10f : 0f));
        int vertical = Math.round(dpToPx(itemView, isCardStyle ? 4f : 0f));
        if (layoutParams.leftMargin == horizontal
                && layoutParams.rightMargin == horizontal
                && layoutParams.topMargin == vertical
                && layoutParams.bottomMargin == vertical) {
            return;
        }
        layoutParams.setMargins(horizontal, vertical, horizontal, vertical);
        itemView.setLayoutParams(layoutParams);
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

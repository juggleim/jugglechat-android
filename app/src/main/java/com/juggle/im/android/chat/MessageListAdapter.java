package com.juggle.im.android.chat;

import static android.view.View.GONE;
import static android.view.View.VISIBLE;

import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.juggle.im.JIM;
import com.juggle.im.android.R;
import com.juggle.im.android.chat.utils.MessageUtils;
import com.juggle.im.android.chat.provider.MessageView;
import com.juggle.im.android.model.UiMessage;
import com.juggle.im.android.utils.AvatarUtils;
import com.juggle.im.android.utils.ResourceUtils;
import com.juggle.im.android.widget.JuggleCheckBox;
import com.juggle.im.model.Message;
import com.juggle.im.model.MessageReaction;
import com.juggle.im.model.MessageReactionItem;
import com.juggle.im.model.UserInfo;
import com.juggle.im.model.messages.ImageMessage;
import com.juggle.im.model.messages.MergeMessage;
import com.juggle.im.model.messages.TextMessage;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class MessageListAdapter extends ListAdapter<UiMessage, RecyclerView.ViewHolder> {
    private final boolean isGroup;
    private final OnMessageActionListener actionListener;
    private final OnMessageLongClickListener longClickListener;
    private final OnAvatarInteractionListener avatarInteractionListener;
    private final OnMessageStatusClickListener statusClickListener;
    // selection mode state
    private boolean selectionMode = false;
    private final List<UiMessage> selectedMsg = new ArrayList<>();
    private OnSelectionChangeListener selectionChangeListener = null;

    protected MessageListAdapter(boolean isGroup) {
        this(isGroup, null, null, null, null);
    }

    protected MessageListAdapter(boolean isGroup, OnMessageActionListener listener) {
        this(isGroup, listener, null, null, null);
    }

    protected MessageListAdapter(boolean isGroup, OnMessageActionListener listener,
            OnMessageLongClickListener longClickListener) {
        this(isGroup, listener, longClickListener, null, null);
    }

    protected MessageListAdapter(boolean isGroup, OnMessageActionListener listener,
            OnMessageLongClickListener longClickListener,
            OnAvatarInteractionListener avatarInteractionListener) {
        this(isGroup, listener, longClickListener, avatarInteractionListener, null);
    }

    protected MessageListAdapter(boolean isGroup, OnMessageActionListener listener,
            OnMessageLongClickListener longClickListener,
            OnAvatarInteractionListener avatarInteractionListener,
            OnMessageStatusClickListener statusClickListener) {
        super(DIFF);
        this.isGroup = isGroup;
        this.actionListener = listener;
        this.longClickListener = longClickListener;
        this.avatarInteractionListener = avatarInteractionListener;
        this.statusClickListener = statusClickListener;
    }

    public void setSelectionChangeListener(OnSelectionChangeListener l) {
        this.selectionChangeListener = l;
    }

    public interface OnSelectionChangeListener {
        void onSelectionModeChanged(boolean inSelectionMode);

        void onSelectionChanged(List<UiMessage> selectedMsg);
    }

    public void enterSelectionMode(UiMessage initialMessage) {
        selectionMode = true;
        if (initialMessage != null) selectedMsg.add(initialMessage);
        notifyDataSetChanged();
        if (selectionChangeListener != null) {
            selectionChangeListener.onSelectionModeChanged(true);
            selectionChangeListener.onSelectionChanged(selectedMsg);
        }
    }

    public void exitSelectionMode() {
        selectionMode = false;
        selectedMsg.clear();
        notifyDataSetChanged();
        if (selectionChangeListener != null) {
            selectionChangeListener.onSelectionModeChanged(false);
            selectionChangeListener.onSelectionChanged(selectedMsg);
        }
    }

    public List<UiMessage> getSelectedMessages() {
        List<UiMessage> res = new ArrayList<>();
        for (int i = 0; i < getItemCount(); i++) {
            UiMessage m = getItem(i);
            if (m.getMessageId() != null && selectedMsg.contains(m)) res.add(m);
        }
        return res;
    }

    public int getSelectionCount() {
        return selectedMsg.size();
    }

    public int getIndexByMessageId(String messageId) {
        List<UiMessage> current = getCurrentList();
        int idx = -1;
        for (int i = 0; i < current.size(); i++) {
            UiMessage um = current.get(i);
            if (um.getMessageId() != null && um.getMessageId().equals(messageId)) {
                idx = i;
                break;
            }
        }
        return idx;
    }

    public int updateMessageReadByMessageId(String messageId) {
        List<UiMessage> current = getCurrentList();
        for (int i = 0; i < current.size(); i++) {
            UiMessage um = current.get(i);
            if (um.getMessageId() != null && um.getMessageId().equals(messageId)) {
                um.getMessage().setHasRead(true);
                return i;
            }
        }
        return -1;
    }

    public int getIndexByMessageNo(long msgNo) {
        if (msgNo <= 0L) {
            return -1;
        }
        List<UiMessage> current = getCurrentList();
        int idx = -1;
        for (int i = 0; i < current.size(); i++) {
            UiMessage um = current.get(i);
            if (um.getMessage().getClientMsgNo() > 0L && um.getMessage().getClientMsgNo() == msgNo) {
                idx = i;
                break;
            }
        }
        return idx;
    }

    public void toggleSelect(UiMessage message) {
        if (message == null) return;
        if (selectedMsg.contains(message)) selectedMsg.remove(message);
        else selectedMsg.add(message);
        int idx = getIndexByMessageId(message.getMessageId());
        if (idx != -1) notifyItemChanged(idx);
        if (selectionChangeListener != null)
            selectionChangeListener.onSelectionChanged(selectedMsg);
    }

    @Override
    public int getItemViewType(int position) {
        UiMessage m = getItem(position);
        int resId = MessageUtils.getMessageViewTemplate(m);
        return resId;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        View v = inflater.inflate(viewType, parent, false);
        return new MessageHolder(v, actionListener, longClickListener, avatarInteractionListener, statusClickListener);
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        UiMessage m = getItem(position);
        if (holder instanceof MessageHolder) {
            boolean sent = m.getDirection() == com.juggle.im.model.Message.MessageDirection.SEND;
            boolean selected = m.getMessageId() != null && selectedMsg.contains(m);
            ((MessageHolder) holder).bind(m, isGroup, sent, selectionMode, selected);
        }
    }

    @Override
    public void onViewRecycled(@NonNull RecyclerView.ViewHolder holder) {
        super.onViewRecycled(holder);
        resetSendStateViews(holder.itemView);
    }

    private void resetSendStateViews(View itemView) {
        View msgStatusContainer = itemView.findViewById(R.id.msg_status_container);
        if (msgStatusContainer != null) {
            msgStatusContainer.setVisibility(GONE);
        }
        ImageView msgReadStatus = itemView.findViewById(R.id.msg_read_status);
        if (msgReadStatus != null) {
            msgReadStatus.setVisibility(GONE);
        }
        ProgressBar msgSendStatus = itemView.findViewById(R.id.msg_send_status);
        if (msgSendStatus != null) {
            msgSendStatus.setVisibility(GONE);
        }

        View imageStatusContainer = itemView.findViewById(R.id.image_msg_status_container);
        if (imageStatusContainer != null) {
            imageStatusContainer.setVisibility(GONE);
        }
        ImageView imageReadStatus = itemView.findViewById(R.id.image_msg_read_status);
        if (imageReadStatus != null) {
            imageReadStatus.setVisibility(GONE);
        }
        ProgressBar imageSendStatus = itemView.findViewById(R.id.image_msg_send_status);
        if (imageSendStatus != null) {
            imageSendStatus.setVisibility(GONE);
        }

        TextView msgTime = itemView.findViewById(R.id.msg_sent_time);
        if (msgTime != null) {
            msgTime.setVisibility(VISIBLE);
        }
        TextView imageMsgTime = itemView.findViewById(R.id.image_msg_time);
        if (imageMsgTime != null) {
            imageMsgTime.setVisibility(GONE);
        }
    }

    static class MessageHolder extends RecyclerView.ViewHolder {
        private static final int MAX_REACTION_PER_ROW = 5;
        private final ViewGroup container;
        private MessageView delegate;
        private final OnMessageActionListener actionListener;
        private final OnMessageLongClickListener longClickListener;
        private final OnAvatarInteractionListener avatarInteractionListener;
        private final OnMessageStatusClickListener statusClickListener;
        private final JuggleCheckBox checkBox;
        private final View reactionContainer;
        private final LinearLayout reactionChipContainer;
        private final ViewGroup replyPreviewContainer;
        private UiMessage boundMessage;
        private String lastBoundStableKey = "";
        private Class<?> lastBoundContentClass = null;
        private boolean lastBoundHasReply = false;

        MessageHolder(@NonNull View itemView, OnMessageActionListener listener,
                OnMessageLongClickListener longClickListener,
                OnAvatarInteractionListener avatarInteractionListener,
                OnMessageStatusClickListener statusClickListener) {
            super(itemView);
            this.container = itemView.findViewById(R.id.message_content_container);
            this.actionListener = listener;
            this.longClickListener = longClickListener;
            this.avatarInteractionListener = avatarInteractionListener;
            this.statusClickListener = statusClickListener;
            this.checkBox = itemView.findViewById(R.id.checkbox);
            this.reactionContainer = itemView.findViewById(R.id.reaction_container);
            this.reactionChipContainer = itemView.findViewById(R.id.reaction_chip_container);
            this.replyPreviewContainer = itemView.findViewById(R.id.reply_preview_container);
        }

        void bind(UiMessage m, boolean isGroup, boolean isSend, boolean inSelectionMode, boolean selected) {
            if (container == null) return;
            container.setVisibility(VISIBLE);
            if (replyPreviewContainer != null) {
                replyPreviewContainer.setVisibility(GONE);
                replyPreviewContainer.removeAllViews();
            }
            if (!(container instanceof ViewGroup)) {
                container.setVisibility(GONE);
                return;
            }
            long clientMsgNo = m.getMessage().getClientMsgNo();
            String stableKey = clientMsgNo > 0L
                    ? "c:" + clientMsgNo
                    : "m:" + m.getMessageId();
            Class<?> contentClass = m.getMessage().getContent() != null
                    ? m.getMessage().getContent().getClass()
                    : null;
            boolean hasReply = m.getMessage().getReferredMessage() != null;
            boolean needReinflate = delegate == null
                    || !TextUtils.equals(stableKey, lastBoundStableKey)
                    || !Objects.equals(contentClass, lastBoundContentClass)
                    || hasReply != lastBoundHasReply
                    || container.getChildCount() == 0;

            if (needReinflate) {
                container.removeAllViews();
                delegate = MessageUtils.createMessageViewHolder(m, container);
                lastBoundStableKey = stableKey;
                lastBoundContentClass = contentClass;
                lastBoundHasReply = hasReply;
            }
            delegate.bind(m, m.getMessage().getContent(), isGroup, itemView, this.avatarInteractionListener);
            bindSendStatusClick(m);
            bindReplyPreview(m);
            boundMessage = m;
            bindPinnedState(m);

            // Display reactions
            bindReactions(m, isSend);
            bindHighlightState(m);

            // set long click to either enter selection mode (if supported) or show actions
            container.setOnLongClickListener(v -> {
                int pos = getBindingAdapterPosition();
                if (pos == RecyclerView.NO_POSITION) return true;
                if (inSelectionMode) return true;
                if (!MessageUtils.shownInMessageList(m.getMessage())) return true;
                if (longClickListener != null) {
                    longClickListener.onMessageLongClick(m, v, pos);
                }
                return true;
            });

            // click whole item toggles selection when in selection mode
            container.setOnClickListener(v -> {
                if (inSelectionMode) {
                    if (actionListener != null) actionListener.onMessageAction(m, "toggle_select");
                }
            });

            // configure select box visibility and state
            if (checkBox != null) {
                if (inSelectionMode) {
                    checkBox.setVisibility(VISIBLE);
                    checkBox.setChecked(selected);
                } else {
                    checkBox.setVisibility(GONE);
                }
                checkBox.setOnClickListener(v -> {
                    if (actionListener != null) {
                        actionListener.onMessageAction(m, "toggle_select");
                    }
                });
            }
        }

        /**
         * 绑定发送失败状态点击事件。
         *
         * @param message 当前消息
         */
        private void bindSendStatusClick(UiMessage message) {
            bindStatusClick(itemView.findViewById(R.id.msg_read_status), message);
            bindStatusClick(itemView.findViewById(R.id.image_msg_read_status), message);
        }

        /**
         * 绑定单个状态图标的点击事件。
         * tips: 仅发送失败时响应点击，避免影响已发送/已读状态展示。
         *
         * @param statusView 状态图标
         * @param message 当前消息
         */
        private void bindStatusClick(View statusView, UiMessage message) {
            if (statusView == null) {
                return;
            }
            statusView.setOnClickListener(null);
            statusView.setClickable(false);
            boolean isFail = message.getDirection() == Message.MessageDirection.SEND
                    && message.getMessage().getState() != null
                    && message.getMessage().getState().getValue() == Message.MessageState.FAIL.getValue();
            if (!isFail || statusClickListener == null) {
                return;
            }
            statusView.setClickable(true);
            statusView.setOnClickListener(v -> statusClickListener.onStatusClick(message));
        }

        /**
         *
         * <p>tips：高亮只做临时覆盖，非高亮时始终回退到当前消息类型应有的默认样式，避免 RecyclerView 复用导致背景串位。</p>
         */
        private void bindHighlightState(UiMessage uiMessage) {
            boolean isHighlight = Boolean.TRUE.equals(uiMessage.getExtension("highlight"));
            if (reactionChipContainer != null) {
                // reaction 使用多 chip 后，使用透明度做弱高亮，避免覆盖 chip 自身样式。
                float chipAlpha = isHighlight ? 0.88f : 1f;
                for (int i = 0; i < reactionChipContainer.getChildCount(); i++) {
                    View child = reactionChipContainer.getChildAt(i);
                    if (child != null) {
                        child.setAlpha(chipAlpha);
                    }
                }
            }
        }

        /**
         * 绑定消息 pin 态。
         *
         * <p>tips：pin 态只隐藏原列表中的消息内容，保留 cell 占位，避免 RecyclerView 因高度变化导致上下消息跳动。</p>
         */
        private void bindReplyPreview(UiMessage message) {
            if (replyPreviewContainer == null) {
                return;
            }
            replyPreviewContainer.removeAllViews();
            Message replyMsg = message.getMessage().getReferredMessage();
            if (replyMsg == null) {
                replyPreviewContainer.setVisibility(GONE);
                return;
            }

            View vReply = LayoutInflater.from(itemView.getContext()).inflate(R.layout.content_reply, replyPreviewContainer, false);
            TextView vTitle = vReply.findViewById(R.id.text_message_title);
            TextView vContent = vReply.findViewById(R.id.reply_text_message_content);
            ImageView ivImage = vReply.findViewById(R.id.reply_image_id);
            UserInfo sendUser = JIM.getInstance().getUserInfoManager().getUserInfo(replyMsg.getSenderUserId());
            String senderName = sendUser != null && !TextUtils.isEmpty(sendUser.getUserName())
                    ? sendUser.getUserName()
                    : replyMsg.getSenderUserId();
            vTitle.setText((TextUtils.isEmpty(senderName) ? "" : senderName) + "：");
            if (!(replyMsg.getContent() instanceof ImageMessage)) {
                vContent.setText(MessageUtils.getMessageSummary(itemView.getContext(), replyMsg));
            }
            if (replyMsg.getContent() instanceof ImageMessage) {
                ivImage.setVisibility(VISIBLE);
                String thumbnailUrl = ((ImageMessage) replyMsg.getContent()).getThumbnailUrl();
                if (TextUtils.isEmpty(thumbnailUrl)) {
                    thumbnailUrl = ((ImageMessage) replyMsg.getContent()).getLocalPath();
                }
                if (TextUtils.isEmpty(thumbnailUrl)) {
                    thumbnailUrl = ((ImageMessage) replyMsg.getContent()).getUrl();
                }
                AvatarUtils.loadImage(ivImage, thumbnailUrl);
            } else {
                ivImage.setVisibility(GONE);
            }
            replyPreviewContainer.addView(vReply);
            replyPreviewContainer.setVisibility(VISIBLE);
        }

        private void bindPinnedState(UiMessage uiMessage) {
            boolean isPinned = Boolean.TRUE.equals(uiMessage.getExtension("context_pinned"));
            itemView.setAlpha(isPinned ? 0f : 1f);
        }

        /**
         * 绑定消息 Reaction 展示。
         * <p>
         * 简要描述：
         * 将每个 reaction 类型渲染为独立 chip，满足“同类型多人显示计数，单人显示头像”的产品规则。
         *
         * @param message 当前消息
         * @param isSendDirection 是否发送方消息（用于切换 chip 颜色）
         */
        private void bindReactions(UiMessage message, boolean isSendDirection) {
            if (reactionContainer == null || reactionChipContainer == null) {
                return;
            }
            reactionChipContainer.removeAllViews();

            String messageId = message.getMessageId();
            if (TextUtils.isEmpty(messageId)) {
                reactionContainer.setVisibility(GONE);
                return;
            }

            List<String> messageIdList = new ArrayList<>();
            messageIdList.add(messageId);
            List<MessageReaction> reactions = JIM.getInstance().getMessageManager()
                    .getCachedMessagesReaction(messageIdList);
            if (reactions == null || reactions.isEmpty()) {
                reactionContainer.setVisibility(GONE);
                return;
            }

            MessageReaction reaction = reactions.get(0);
            List<MessageReactionItem> items = reaction.getItemList();
            if (items == null || items.isEmpty()) {
                reactionContainer.setVisibility(GONE);
                return;
            }

            int visibleChipCount = 0;
            LayoutInflater inflater = LayoutInflater.from(itemView.getContext());
            LinearLayout currentRow = null;
            int currentRowChipCount = 0;
            for (MessageReactionItem item : items) {
                int userCount = getReactionUserCount(item);
                if (userCount <= 0) {
                    continue;
                }
                View chipView = inflater.inflate(R.layout.item_message_reaction_chip, reactionChipContainer, false);
                LinearLayout chipRoot = chipView.findViewById(R.id.layout_reaction_chip);
                TextView emojiView = chipView.findViewById(R.id.tv_reaction_emoji);
                TextView countView = chipView.findViewById(R.id.tv_reaction_count);
                ImageView avatarView = chipView.findViewById(R.id.iv_reaction_avatar);

                emojiView.setText(reactionIdToEmoji(item.getReactionId()));
                if (chipRoot != null) {
                    chipRoot.setBackgroundResource(isSendDirection
                            ? R.drawable.bg_reaction_chip_sent
                            : R.drawable.bg_reaction_chip_received);
                }

                if (userCount > 1) {
                    countView.setVisibility(VISIBLE);
                    countView.setText(String.valueOf(userCount));
                    avatarView.setVisibility(GONE);
                } else {
                    countView.setVisibility(GONE);
                    UserInfo reactor = getFirstReactionUser(item);
                    if (reactor == null) {
                        // 理论上单人 reaction 会带 userInfo；兜底避免出现空头像占位。
                        avatarView.setVisibility(GONE);
                        countView.setVisibility(VISIBLE);
                        countView.setText("1");
                    } else {
                        avatarView.setVisibility(VISIBLE);
                        bindReactionAvatar(avatarView, reactor);
                    }
                }

                LinearLayout.LayoutParams layoutParams = (LinearLayout.LayoutParams) chipView.getLayoutParams();
                if (layoutParams == null) {
                    layoutParams = new LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.WRAP_CONTENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT);
                }
                if (currentRow == null || currentRowChipCount >= MAX_REACTION_PER_ROW) {
                    currentRow = createReactionRow(isSendDirection);
                    LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.WRAP_CONTENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT);
                    if (reactionChipContainer.getChildCount() > 0) {
                        rowParams.topMargin = ResourceUtils.dp2px(itemView.getContext(), 4f);
                    }
                    currentRow.setLayoutParams(rowParams);
                    reactionChipContainer.addView(currentRow);
                    currentRowChipCount = 0;
                }
                if (currentRowChipCount > 0) {
                    layoutParams.setMarginStart(ResourceUtils.dp2px(itemView.getContext(), 4f));
                } else {
                    layoutParams.setMarginStart(0);
                }
                chipView.setLayoutParams(layoutParams);
                currentRow.addView(chipView);
                currentRowChipCount++;
                visibleChipCount++;
            }

            reactionContainer.setVisibility(visibleChipCount > 0 ? VISIBLE : GONE);
        }

        /**
         * 创建 reaction 的单行容器。
         * <p>
         * 简要描述：
         * 每行最多放置 5 个表情 chip；发送方右对齐，接收方左对齐，和消息气泡方向一致。
         *
         * @param isSendDirection 是否发送方消息
         * @return 单行容器
         */
        private LinearLayout createReactionRow(boolean isSendDirection) {
            LinearLayout row = new LinearLayout(itemView.getContext());
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(isSendDirection ? android.view.Gravity.END : android.view.Gravity.START);
            return row;
        }

        /**
         * 获取单个 Reaction 类型的参与人数。
         *
         * @param item Reaction 项
         * @return 人数
         */
        private int getReactionUserCount(MessageReactionItem item) {
            if (item == null || item.getUserInfoList() == null) {
                return 0;
            }
            return item.getUserInfoList().size();
        }

        /**
         * 取当前 Reaction 类型的第一个参与用户。
         *
         * @param item Reaction 项
         * @return 用户信息，若不存在则返回 null
         */
        private UserInfo getFirstReactionUser(MessageReactionItem item) {
            if (item == null || item.getUserInfoList() == null || item.getUserInfoList().isEmpty()) {
                return null;
            }
            return item.getUserInfoList().get(0);
        }

        /**
         * 绑定 reaction 用户头像。
         *
         * @param avatarView 头像控件
         * @param user 用户信息
         */
        private void bindReactionAvatar(ImageView avatarView, UserInfo user) {
            AvatarUtils.loadAvatar(
                    avatarView,
                    user.getPortrait(),
                    user.getUserName(),
                    user.getUserId());
        }

        void bindMenuState(View menuView, UiMessage ui) {
            if (menuView == null || ui == null) {
                return;
            }
            View vTranslate = menuView.findViewById(R.id.action_translate);
            View vCopy = menuView.findViewById(R.id.action_copy);
            View vEdit = menuView.findViewById(R.id.action_edit);
            View vRecall = menuView.findViewById(R.id.action_recall);
            View vTop = menuView.findViewById(R.id.action_top);
            View vFavorite = menuView.findViewById(R.id.action_favorite);
            View vReply = menuView.findViewById(R.id.action_reply);
            View vForward = menuView.findViewById(R.id.action_forward);
            View vMultiSelect = menuView.findViewById(R.id.action_multi_select);
            View vReport = menuView.findViewById(R.id.action_report);
            View vDelete = menuView.findViewById(R.id.action_delete);
            View reactionOk = menuView.findViewById(R.id.reaction_ok_hand);
            View reactionThumbUp = menuView.findViewById(R.id.reaction_thumb_up);
            View reactionLove = menuView.findViewById(R.id.reaction_love_face);
            View reactionSalute = menuView.findViewById(R.id.reaction_salute);
            View reactionHeart = menuView.findViewById(R.id.reaction_heart);
            View reactionBrokenHeart = menuView.findViewById(R.id.reaction_broken_heart);
            View reactionPoop = menuView.findViewById(R.id.reaction_poop);
            View reactionParty = menuView.findViewById(R.id.reaction_party);

            boolean isSend = ui.getMessage().getDirection() == Message.MessageDirection.SEND;
            boolean canRecall = isSend && ui.getMessage().getState() == Message.MessageState.SENT;
            boolean isText = ui.getMessage().getContent() instanceof TextMessage;
            boolean canEdit = isSend && isText;
            boolean canTranslate = isText;

            setActionEnabled(vRecall, canRecall);
            setActionEnabled(vEdit, canEdit);
            setActionEnabled(vCopy, isText);
            setActionEnabled(vTranslate, canTranslate);
            setActionEnabled(vReport, !isSend);

            bindAction(vTranslate, ui, Action.TRANSLATE);
            bindAction(vCopy, ui, Action.COPY);
            bindAction(vEdit, ui, Action.EDIT);
            bindAction(vRecall, ui, Action.RECALL);
            bindAction(vTop, ui, Action.TOP);
            bindAction(vFavorite, ui, Action.FAVORITE);
            bindAction(vReply, ui, Action.REPLY);
            bindAction(vForward, ui, Action.FORWARD);
            bindAction(vMultiSelect, ui, Action.MULTI_SELECT);
            bindAction(vReport, ui, Action.REPORT);
            bindAction(vDelete, ui, Action.DELETE);

            bindAction(reactionOk, ui, Action.REACTION_PREFIX + ReactionStakerMapper.REACTION_ID_OK_HAND);
            bindAction(reactionThumbUp, ui, Action.REACTION_PREFIX + ReactionStakerMapper.REACTION_ID_THUMB_UP);
            bindAction(reactionLove, ui, Action.REACTION_PREFIX + ReactionStakerMapper.REACTION_ID_SMILING_FACE_WITH_HEARTS);
            bindAction(reactionSalute, ui, Action.REACTION_PREFIX + ReactionStakerMapper.REACTION_ID_SALUTE);
            bindAction(reactionHeart, ui, Action.REACTION_PREFIX + ReactionStakerMapper.REACTION_ID_HEART);
            bindAction(reactionBrokenHeart, ui,
                    Action.REACTION_PREFIX + ReactionStakerMapper.REACTION_ID_BROKEN_HEART);
            bindAction(reactionPoop, ui, Action.REACTION_PREFIX + ReactionStakerMapper.REACTION_ID_POOP);
            bindAction(reactionParty, ui, Action.REACTION_PREFIX + ReactionStakerMapper.REACTION_ID_PARTY);
        }

        String reactionIdToEmoji(String reactionId) {
            return ReactionStakerMapper.toEmoji(reactionId);
        }

        private void bindAction(View actionView, UiMessage uiMessage, String action) {
            if (actionView == null) return;
            actionView.setOnClickListener(v -> {
                if (!actionView.isEnabled()) return;
                if (actionListener != null) {
                    actionListener.onMessageAction(uiMessage, action);
                }
            });
        }

        private void setActionEnabled(View actionView, boolean enabled) {
            if (actionView == null) return;
            actionView.setEnabled(enabled);
            actionView.setAlpha(enabled ? 1f : 0.35f);
        }
    }

    public void setContextPinnedMessageId(String messageId) {
        List<UiMessage> current = getCurrentList();
        for (int i = 0; i < current.size(); i++) {
            UiMessage uiMessage = current.get(i);
            boolean shouldPin = !TextUtils.isEmpty(messageId) && TextUtils.equals(messageId, uiMessage.getMessageId());
            boolean before = Boolean.TRUE.equals(uiMessage.getExtension("context_pinned"));
            if (before == shouldPin) {
                continue;
            }
            uiMessage.putExtension("context_pinned", shouldPin);
            notifyItemChanged(i);
        }
    }

    public View createContextPinnedMessageView(@NonNull ViewGroup parent, @NonNull UiMessage uiMessage) {
        View itemView = LayoutInflater.from(parent.getContext())
                .inflate(getMessageViewTemplate(uiMessage.getStableKey()), parent, false);
        MessageHolder holder = new MessageHolder(itemView, actionListener, null, avatarInteractionListener,
                statusClickListener);
        holder.bind(uiMessage, isGroup,
                uiMessage.getDirection() == Message.MessageDirection.SEND,
                false,
                false);
        itemView.setAlpha(1f);
        return itemView;
    }

    /**
     * 绑定长按浮层菜单状态。
     *
     * @param menuView 长按浮层菜单根视图
     * @param uiMessage 当前消息
     */
    public void bindContextMenu(@NonNull View menuView, @NonNull UiMessage uiMessage) {
        MessageHolder holder = new MessageHolder(menuView, actionListener, null, avatarInteractionListener,
                statusClickListener);
        holder.bindMenuState(menuView, uiMessage);
    }

    /**
     * 根据稳定键获取消息模板。
     *
     * @param stableKey 消息稳定键
     * @return 对应消息 item 布局
     */
    public int getMessageViewTemplate(@NonNull String stableKey) {
        int index = getIndexByStableKey(stableKey);
        if (index >= 0) {
            return getItemViewType(index);
        }
        for (UiMessage uiMessage : getCurrentList()) {
            if (stableKey.equals(uiMessage.getStableKey())) {
                return MessageUtils.getMessageViewTemplate(uiMessage);
            }
        }
        throw new IllegalArgumentException("unknown stableKey: " + stableKey);
    }

    private int getIndexByStableKey(@NonNull String stableKey) {
        List<UiMessage> current = getCurrentList();
        for (int i = 0; i < current.size(); i++) {
            if (stableKey.equals(current.get(i).getStableKey())) {
                return i;
            }
        }
        return -1;
    }

    public interface OnMessageLongClickListener {
        /**
         * 长按消息回调。
         *
         * @param message 被长按的消息
         * @param anchor  被长按的消息气泡锚点
         * @param position 当前适配器位置
         */
        void onMessageLongClick(UiMessage message, View anchor, int position);
    }

    public interface OnAvatarInteractionListener {
        /**
         * 点击消息头像。
         *
         * @param message 当前消息
         * @param userId 头像所属用户 ID
         * @param displayName 头像所属用户展示名
         */
        void onAvatarClick(UiMessage message, String userId, String displayName);

        /**
         * 长按消息头像。
         *
         * @param message 当前消息
         * @param userId 头像所属用户 ID
         * @param displayName 头像所属用户展示名
         */
        void onAvatarLongClick(UiMessage message, String userId, String displayName);
    }

    public interface OnMessageActionListener {
        void onMessageAction(UiMessage message, String action);
    }

    public interface OnMessageStatusClickListener {
        /**
         * 点击消息发送状态。
         *
         * @param message 当前消息
         */
        void onStatusClick(UiMessage message);
    }

    public static class Action {
        public static final String TRANSLATE = "translate";
        public static final String COPY = "copy";
        public static final String EDIT = "edit";
        public static final String TOP = "top";
        public static final String RECALL = "recall";
        public static final String FAVORITE = "favorite";
        public static final String FORWARD = "forward";
        public static final String REPLY = "relay";
        public static final String MULTI_SELECT = "multi_select";
        public static final String REPORT = "report";
        public static final String REACTION_PREFIX = "reaction:";
        public static final String DELETE = "delete";
    }


    private static final DiffUtil.ItemCallback<UiMessage> DIFF = new DiffUtil.ItemCallback<UiMessage>() {
        @Override
        public boolean areItemsTheSame(@NonNull UiMessage oldItem, @NonNull UiMessage newItem) {
            String oldMessageId = oldItem.getMessageId();
            String newMessageId = newItem.getMessageId();
            boolean sameId;
            if (!TextUtils.isEmpty(oldMessageId) && !TextUtils.isEmpty(newMessageId)) {
                sameId = oldMessageId.equals(newMessageId);
            } else {
                long oldClientMsgNo = oldItem.getMessage().getClientMsgNo();
                long newClientMsgNo = newItem.getMessage().getClientMsgNo();
                if (oldClientMsgNo > 0L && newClientMsgNo > 0L) {
                    sameId = oldClientMsgNo == newClientMsgNo;
                } else {
                    sameId = oldMessageId.equals(newMessageId);
                }
            }
            String oldContentType = oldItem.getMessage().getContentType();
            String newContentType = newItem.getMessage().getContentType();
            return sameId && (oldContentType == null || oldContentType.equals(newContentType));
        }

        @Override
        public boolean areContentsTheSame(@NonNull UiMessage oldItem, @NonNull UiMessage newItem) {
            return oldItem.getMessage().getState().getValue() == newItem.getMessage().getState().getValue()
                    && oldItem.getMessage().isHasRead() == newItem.getMessage().isHasRead()
                    && oldItem.getMessage().getDirection().getValue() == newItem.getMessage().getDirection().getValue()
                    && oldItem.getMessage().isEdit() == newItem.getMessage().isEdit();
        }
    };
}

package com.juggle.im.android.chat;

import static android.view.View.GONE;
import static android.view.View.VISIBLE;

import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
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
import com.juggle.im.model.messages.TextMessage;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class MessageListAdapter extends ListAdapter<UiMessage, RecyclerView.ViewHolder> {
    private final boolean isGroup;
    private final OnMessageActionListener actionListener;
    // selection mode state
    private boolean selectionMode = false;
    private final List<UiMessage> selectedMsg = new ArrayList<>();
    private OnSelectionChangeListener selectionChangeListener = null;

    protected MessageListAdapter(boolean isGroup) {
        this(isGroup, null);
    }

    protected MessageListAdapter(boolean isGroup, OnMessageActionListener listener) {
        super(DIFF);
        this.isGroup = isGroup;
        this.actionListener = listener;
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
        return new MessageHolder(v, actionListener);
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
        private final ViewGroup container;
        private MessageView delegate;
        private final OnMessageActionListener actionListener;
        private final JuggleCheckBox checkBox;
        private final View reactionContainer;
        private final View msgViewContainer;
        private final TextView reactionEmojis;
        private String lastBoundStableKey = "";
        private Class<?> lastBoundContentClass = null;
        private boolean lastBoundHasReply = false;

        MessageHolder(@NonNull View itemView, OnMessageActionListener listener) {
            super(itemView);
            this.container = itemView.findViewById(R.id.message_content_container);
            this.actionListener = listener;
            this.checkBox = itemView.findViewById(R.id.checkbox);
            this.reactionContainer = itemView.findViewById(R.id.reaction_container);
            this.msgViewContainer = itemView.findViewById(R.id.message_bubble_container);
            this.reactionEmojis = itemView.findViewById(R.id.reaction_emojis);
        }

        void bind(UiMessage m, boolean isGroup, boolean isSend, boolean inSelectionMode, boolean selected) {
            if (container == null) return;
            container.setVisibility(VISIBLE);
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
                // 回复消息
                if (hasReply) {
                    LayoutInflater inflater = LayoutInflater.from(itemView.getContext());
                    View vReply = inflater.inflate(R.layout.content_reply, container, false);
                    Message replyMsg = m.getMessage().getReferredMessage();
                    TextView vTitle = vReply.findViewById(R.id.text_message_title);
                    TextView vContent = vReply.findViewById(R.id.reply_text_message_content);
                    ImageView ivImage = vReply.findViewById(R.id.reply_image_id);
                    UserInfo sendUser = JIM.getInstance().getUserInfoManager().getUserInfo(replyMsg.getSenderUserId());
                    if (sendUser != null) {
                        vTitle.setText("回复：" + sendUser.getUserName());
                    }
                    vContent.setText(MessageUtils.getMessageSummary(itemView.getContext(), replyMsg));
                    if (replyMsg.getContent() instanceof ImageMessage) {
                        ivImage.setVisibility(VISIBLE);
                        AvatarUtils.loadImage(ivImage, ((ImageMessage) replyMsg.getContent()).getThumbnailUrl());
                    } else {
                        ivImage.setVisibility(GONE);
                    }
                    container.addView(vReply);
                }
                delegate = MessageUtils.createMessageViewHolder(m, container);
                lastBoundStableKey = stableKey;
                lastBoundContentClass = contentClass;
                lastBoundHasReply = hasReply;
            }
            delegate.bind(m, m.getMessage().getContent(), isGroup, itemView);
            bindHighlightState(m);

            // Display reactions
            bindReactions(m);

            // set long click to either enter selection mode (if supported) or show actions
            container.setOnLongClickListener(v -> {
                int pos = getBindingAdapterPosition();
                if (pos == RecyclerView.NO_POSITION) return true;
                if (inSelectionMode) return true;
                if (!MessageUtils.shownInMessageList(m.getMessage())) return true;
                showActionPopup(v, m);
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
         * 绑定消息高亮态。
         *
         * <p>tips：高亮只做临时覆盖，非高亮时始终回退到当前消息类型应有的默认样式，避免 RecyclerView 复用导致背景串位。</p>
         */
        private void bindHighlightState(UiMessage uiMessage) {
            boolean isHighlight = Boolean.TRUE.equals(uiMessage.getExtension("highlight"));
            if (msgViewContainer != null && isHighlight) {
                msgViewContainer.setBackgroundColor(0xFFFFF3C4);
            }
            if (reactionContainer != null) {
                if (isHighlight) {
                    reactionContainer.setBackgroundColor(0xFFE8B93C);
                } else {
                    reactionContainer.setBackgroundResource(R.drawable.bg_reaction_pill);
                }
            }
        }

        private void bindReactions(UiMessage m) {
            if (reactionContainer == null || reactionEmojis == null) {
                return;
            }

            String messageId = m.getMessageId();
            if (messageId == null || messageId.isEmpty()) {
                reactionContainer.setVisibility(GONE);
                setupMessageBubble(false);
                return;
            }

            // Get cached reactions
            List<String> messageIdList = new ArrayList<>();
            messageIdList.add(messageId);
            List<MessageReaction> reactions = JIM.getInstance().getMessageManager()
                    .getCachedMessagesReaction(messageIdList);

            if (reactions == null || reactions.isEmpty()) {
                reactionContainer.setVisibility(GONE);
                setupMessageBubble(false);
                return;
            }

            MessageReaction reaction = reactions.get(0);
            List<MessageReactionItem> items = reaction.getItemList();
            if (items == null || items.isEmpty()) {
                reactionContainer.setVisibility(GONE);
                setupMessageBubble(false);
                return;
            }

            // Build reaction display string
            StringBuilder sb = new StringBuilder();
            for (MessageReactionItem item : items) {
                String emoji = reactionIdToEmoji(item.getReactionId());
                int count = item.getUserInfoList() != null ? item.getUserInfoList().size() : 0;
                if (sb.length() > 0) sb.append(" ");
                sb.append(emoji);
                if (count > 1) {
                    sb.append(count);
                }
            }

            if (sb.length() > 0) {
                reactionEmojis.setText(sb.toString());
                reactionContainer.setVisibility(VISIBLE);
                setupMessageBubble(true);
            } else {
                reactionContainer.setVisibility(GONE);
                setupMessageBubble(false);
            }
        }

        private void setupMessageBubble(boolean hasReaction) {
            if (hasReaction) {
                FrameLayout.LayoutParams p = (FrameLayout.LayoutParams) msgViewContainer.getLayoutParams();
                p.setMargins(0, dp(msgViewContainer, 15), 0, 0);
                msgViewContainer.setLayoutParams(p);
            } else {
                FrameLayout.LayoutParams p = (FrameLayout.LayoutParams) msgViewContainer.getLayoutParams();
                p.setMargins(0, 0, 0, 0);
                msgViewContainer.setLayoutParams(p);
            }
        }

        private int dp(View itemView, int value) {
            return Math.round(value * itemView.getResources().getDisplayMetrics().density);
        }

        private String reactionIdToEmoji(String reactionId) {
            return ReactionStakerMapper.toEmoji(reactionId);
        }

        private void showActionPopup(View anchor, UiMessage ui) {
            if (anchor == null || ui == null || actionListener == null) return;
            LayoutInflater inflater = LayoutInflater.from(anchor.getContext());
            View popupView = inflater.inflate(R.layout.layout_message_popup, null);

            final PopupWindow pw = new PopupWindow(
                    popupView,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    true
            );
            pw.setOutsideTouchable(true);
            pw.setFocusable(true);
            pw.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));

            int[] location = new int[2];
            anchor.getLocationOnScreen(location);
            int anchorX = location[0];
            int anchorY = location[1];
            int anchorWidth = anchor.getWidth();
            int anchorHeight = anchor.getHeight();

            popupView.measure(
                    View.MeasureSpec.UNSPECIFIED,
                    View.MeasureSpec.UNSPECIFIED
            );
            int popupWidth = popupView.getMeasuredWidth();
            int popupHeight = popupView.getMeasuredHeight();

            DisplayMetrics dm = anchor.getContext().getResources().getDisplayMetrics();
            int screenWidth = dm.widthPixels;
            int screenHeight = dm.heightPixels;

            float centerX = anchorX + anchorWidth / 2f;

            int x = (int) (centerX - popupWidth / 2);
            int y = anchorY - popupHeight - ResourceUtils.dp2px(anchor.getContext(), 6);

            if (x < ResourceUtils.dp2px(anchor.getContext(), 4)) {
                x = ResourceUtils.dp2px(anchor.getContext(), 4);
            } else if (x + popupWidth > screenWidth - ResourceUtils.dp2px(anchor.getContext(), 4)) {
                x = screenWidth - popupWidth - ResourceUtils.dp2px(anchor.getContext(), 4);
            }
            if (y < ResourceUtils.dp2px(anchor.getContext(), 8)) {
                y = anchorY + anchorHeight + ResourceUtils.dp2px(anchor.getContext(), 6);
            }
            if (y + popupHeight > screenHeight - ResourceUtils.dp2px(anchor.getContext(), 8)) {
                y = screenHeight - popupHeight - ResourceUtils.dp2px(anchor.getContext(), 8);
            }

            pw.showAtLocation(anchor, Gravity.NO_GRAVITY, x, y);

            View vTranslate = popupView.findViewById(R.id.action_translate);
            View vCopy = popupView.findViewById(R.id.action_copy);
            View vEdit = popupView.findViewById(R.id.action_edit);
            View vRecall = popupView.findViewById(R.id.action_recall);
            View vTop = popupView.findViewById(R.id.action_top);
            View vFavorite = popupView.findViewById(R.id.action_favorite);
            View vReply = popupView.findViewById(R.id.action_reply);
            View vForward = popupView.findViewById(R.id.action_forward);
            View vMultiSelect = popupView.findViewById(R.id.action_multi_select);
            View vReport = popupView.findViewById(R.id.action_report);
            View vDelete = popupView.findViewById(R.id.action_delete);
            View reactionOk = popupView.findViewById(R.id.reaction_ok_hand);
            View reactionThumbUp = popupView.findViewById(R.id.reaction_thumb_up);
            View reactionLove = popupView.findViewById(R.id.reaction_love_face);
            View reactionSalute = popupView.findViewById(R.id.reaction_salute);
            View reactionHeart = popupView.findViewById(R.id.reaction_heart);
            View reactionBrokenHeart = popupView.findViewById(R.id.reaction_broken_heart);
            View reactionPoop = popupView.findViewById(R.id.reaction_poop);
            View reactionParty = popupView.findViewById(R.id.reaction_party);

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

            bindAction(vTranslate, pw, ui, Action.TRANSLATE);
            bindAction(vCopy, pw, ui, Action.COPY);
            bindAction(vEdit, pw, ui, Action.EDIT);
            bindAction(vRecall, pw, ui, Action.RECALL);
            bindAction(vTop, pw, ui, Action.TOP);
            bindAction(vFavorite, pw, ui, Action.FAVORITE);
            bindAction(vReply, pw, ui, Action.REPLY);
            bindAction(vForward, pw, ui, Action.FORWARD);
            bindAction(vMultiSelect, pw, ui, Action.MULTI_SELECT);
            bindAction(vReport, pw, ui, Action.REPORT);
            bindAction(vDelete, pw, ui, Action.DELETE);

            bindAction(reactionOk, pw, ui, Action.REACTION_PREFIX + ReactionStakerMapper.REACTION_ID_OK_HAND);
            bindAction(reactionThumbUp, pw, ui, Action.REACTION_PREFIX + ReactionStakerMapper.REACTION_ID_THUMB_UP);
            bindAction(reactionLove, pw, ui, Action.REACTION_PREFIX + ReactionStakerMapper.REACTION_ID_SMILING_FACE_WITH_HEARTS);
            bindAction(reactionSalute, pw, ui, Action.REACTION_PREFIX + ReactionStakerMapper.REACTION_ID_SALUTE);
            bindAction(reactionHeart, pw, ui, Action.REACTION_PREFIX + ReactionStakerMapper.REACTION_ID_HEART);
            bindAction(reactionBrokenHeart, pw, ui, Action.REACTION_PREFIX + ReactionStakerMapper.REACTION_ID_BROKEN_HEART);
            bindAction(reactionPoop, pw, ui, Action.REACTION_PREFIX + ReactionStakerMapper.REACTION_ID_POOP);
            bindAction(reactionParty, pw, ui, Action.REACTION_PREFIX + ReactionStakerMapper.REACTION_ID_PARTY);
        }

        private void bindAction(View actionView, PopupWindow popupWindow, UiMessage uiMessage, String action) {
            if (actionView == null) return;
            actionView.setOnClickListener(v -> {
                if (!actionView.isEnabled()) return;
                popupWindow.dismiss();
                actionListener.onMessageAction(uiMessage, action);
            });
        }

        private void setActionEnabled(View actionView, boolean enabled) {
            if (actionView == null) return;
            actionView.setEnabled(enabled);
            actionView.setAlpha(enabled ? 1f : 0.35f);
        }
    }

    public interface OnMessageActionListener {
        void onMessageAction(UiMessage message, String action);
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

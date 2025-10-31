package com.juggle.im.android.chat;

import static android.view.View.GONE;
import static android.view.View.VISIBLE;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
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
import com.juggle.im.model.Message;
import com.juggle.im.model.UserInfo;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class MessageListAdapter extends ListAdapter<UiMessage, RecyclerView.ViewHolder> {
    private static final int BASE_SENT = 100;
    private static final int BASE_RECEIVED = 200;
    private static final int BASE_STATUS = 300;
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
        List<UiMessage> current = getCurrentList();
        int idx = -1;
        for (int i = 0; i < current.size(); i++) {
            UiMessage um = current.get(i);
            if (um.getMessage().getClientMsgNo() == msgNo) {
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
        boolean sent = m.getDirection() == com.juggle.im.model.Message.MessageDirection.SEND;
        boolean isState = MessageUtils.isStatusMessage(m.getMessage());
        return isState ? BASE_STATUS : (sent ? BASE_SENT : BASE_RECEIVED);
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        View v = viewType != BASE_STATUS
                ? (viewType == BASE_SENT ? inflater.inflate(R.layout.item_message_sent, parent, false)
                : inflater.inflate(R.layout.item_message_received, parent, false))
                : inflater.inflate(R.layout.item_message_notification, parent, false);
        return new MessageHolder(v, viewType, actionListener);
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

    static class MessageHolder extends RecyclerView.ViewHolder {
        private final int viewType;
        private final View container;
        private MessageView delegate;
        private final OnMessageActionListener actionListener;
        private final ImageView selectBox;
        private final ProgressBar progressBar;
        private final ViewGroup msgStatusContainer;
        private final TextView msgTimeV;
        private final ImageView errorView;

        MessageHolder(@NonNull View itemView, int viewType, OnMessageActionListener listener) {
            super(itemView);
            this.viewType = viewType;
            this.container = itemView.findViewById(R.id.message_content_container);
            this.actionListener = listener;
            this.selectBox = itemView.findViewById(R.id.image_select_box);
            this.progressBar = itemView.findViewById(R.id.msg_send_status);
            this.errorView = itemView.findViewById(R.id.send_error);
            this.msgStatusContainer = itemView.findViewById(R.id.msg_status_container);
            this.msgTimeV = itemView.findViewById(R.id.msg_sent_time);
        }

        void bind(UiMessage m, boolean isGroup, boolean isSend, boolean inSelectionMode, boolean selected) {
            if (container == null) return;
            // remove previous content
            container.setVisibility(VISIBLE);
            if (container instanceof ViewGroup) {
                ((ViewGroup) container).removeAllViews();
            } else {
                container.setVisibility(GONE);
                return;
            }
            delegate = MessageUtils.createMessageViewHolder(m, (ViewGroup) container);
            if (viewType != BASE_STATUS) {
                delegate.bind(m, m.getMessage().getContent(), isGroup);
                ImageView ivAvatar = itemView.findViewById(R.id.image_avatar);
                UserInfo sendUser = JIM.getInstance().getUserInfoManager().getUserInfo(m.getSenderId());
                if (sendUser != null) {
                    String name = sendUser.getUserName();
                    m.setSenderName(name);
                    AvatarUtils.loadAvatar(ivAvatar, sendUser.getPortrait(), name);
                    TextView txSender = itemView.findViewById(R.id.text_sender_name);
                    if (txSender != null) {
                        if (isGroup && !isSend) {
                            txSender.setVisibility(VISIBLE);
                            txSender.setText(sendUser.getUserName());
                        } else {
                            txSender.setVisibility(GONE);
                        }
                    }
                }
                if (viewType == BASE_SENT) {
                    if (progressBar != null) {
                        if (m.getMessage().getState().getValue() == Message.MessageState.SENDING.getValue()
                                || m.getMessage().getState().getValue() == Message.MessageState.UPLOADING.getValue()) {
                            progressBar.setVisibility(VISIBLE);
                        } else {
                            progressBar.setVisibility(GONE);
                        }
                    }
                    if (errorView != null) {
                        if (m.getMessage().getState().getValue() == Message.MessageState.FAIL.getValue()) {
                            errorView.setVisibility(VISIBLE);
                        } else if (m.getMessage().getState().getValue() == Message.MessageState.SENT.getValue()) {
                            errorView.setVisibility(GONE);
                        }
                    }
                    if (msgStatusContainer != null) {
                        msgStatusContainer.setVisibility(VISIBLE);
                        ImageView iv = msgStatusContainer.findViewById(R.id.msg_read_status);
                        if (m.getMessage().isHasRead())
                            iv.setImageResource(R.drawable.ic_msg_read);
                        else
                            iv.setImageResource(R.drawable.ic_msg_sent);
                    }
                }
                DateFormat df = new SimpleDateFormat("HH:mm", Locale.getDefault());
                String text = df.format(new Date(m.getMessage().getTimestamp()));
                msgTimeV.setText(text);
            } else {
                delegate.bind(m, m.getMessage().getContent(), isGroup);
            }

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
            if (selectBox != null) {
                if (inSelectionMode) {
                    selectBox.setVisibility(VISIBLE);
                    selectBox.setImageResource(selected ? R.drawable.ic_checkbox : R.drawable.ic_checkbox_uncheck);
                } else {
                    selectBox.setVisibility(GONE);
                }
                selectBox.setOnClickListener(v -> {
                    if (actionListener != null) {
                        actionListener.onMessageAction(m, "toggle_select");
                    }
                });
            }
        }

        private void showActionPopup(View anchor, UiMessage ui) {
            if (anchor == null || ui == null || actionListener == null) return;
            android.view.LayoutInflater inflater = android.view.LayoutInflater.from(anchor.getContext());
            View popupView = inflater.inflate(R.layout.layout_message_popup, null);
            final android.widget.PopupWindow pw = new android.widget.PopupWindow(popupView,
                    android.view.ViewGroup.LayoutParams.WRAP_CONTENT,
                    android.view.ViewGroup.LayoutParams.WRAP_CONTENT);
            pw.setOutsideTouchable(true);
            pw.setFocusable(true);
            pw.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT));

            int[] loc = new int[2];
            anchor.getLocationOnScreen(loc);
            // show above the anchor if possible
            pw.showAtLocation(anchor, android.view.Gravity.NO_GRAVITY,
                    loc[0], loc[1] - 30 - (int) (8 * anchor.getContext().getResources().getDisplayMetrics().density));

            // wire buttons
            View vCopy = popupView.findViewById(R.id.action_copy);
            View vForward = popupView.findViewById(R.id.action_forward);
            View vRelay = popupView.findViewById(R.id.action_relay);
            View vDelete = popupView.findViewById(R.id.action_delete);

            vCopy.setOnClickListener(v -> {
                pw.dismiss();
                actionListener.onMessageAction(ui, Action.COPY);
            });
            vForward.setOnClickListener(v -> {
                pw.dismiss();
                actionListener.onMessageAction(ui, Action.FORWARD);
            });
            vRelay.setOnClickListener(v -> {
                pw.dismiss();
                actionListener.onMessageAction(ui, Action.RELAY);
            });
            vDelete.setOnClickListener(v -> {
                pw.dismiss();
                actionListener.onMessageAction(ui, Action.DELETE);
            });
        }
    }

    public interface OnMessageActionListener {
        void onMessageAction(UiMessage message, String action);
    }

    public static class Action {
        public static final String COPY = "copy";
        public static final String TOP = "top";
        public static final String FORWARD = "forward";
        public static final String RELAY = "relay";
        public static final String DELETE = "delete";
    }


    private static final DiffUtil.ItemCallback<UiMessage> DIFF = new DiffUtil.ItemCallback<UiMessage>() {
        @Override
        public boolean areItemsTheSame(@NonNull UiMessage oldItem, @NonNull UiMessage newItem) {
            if (oldItem.getMessageId() != null) {
                return oldItem.getMessageId().equals(newItem.getMessageId());
            } else {
                return oldItem.getMessage().getClientMsgNo() == newItem.getMessage().getClientMsgNo();
            }
        }

        @Override
        public boolean areContentsTheSame(@NonNull UiMessage oldItem, @NonNull UiMessage newItem) {
            return oldItem.getMessage().getState().getValue() == newItem.getMessage().getState().getValue()
                    && oldItem.getMessage().isHasRead() == newItem.getMessage().isHasRead()
                    && oldItem.getMessage().getDirection().getValue() == newItem.getMessage().getDirection().getValue();
        }
    };
}

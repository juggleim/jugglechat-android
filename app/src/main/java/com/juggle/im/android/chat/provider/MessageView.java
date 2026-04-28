package com.juggle.im.android.chat.provider;

import static android.view.View.GONE;
import static android.view.View.VISIBLE;

import android.text.TextUtils;
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
import com.juggle.im.android.model.UiMessage;
import com.juggle.im.android.utils.AvatarUtils;
import com.juggle.im.model.Message;
import com.juggle.im.model.UserInfo;

/**
 * Generic base for all message content views used by the adapter.
 * T is the UI wrapper type (UiMessage), K is the underlying SDK message content type.
 */
public abstract class MessageView<T extends UiMessage, K> extends RecyclerView.ViewHolder {
    public MessageView(ViewGroup container, int resId) {
        super(LayoutInflater.from(container.getContext()).inflate(resId, container, false));
    }

    public MessageView(@NonNull ViewGroup itemView) {
        super(itemView);
    }

    public abstract void bindItem(T message, K content, boolean isGroup);

    /**
     * 是否显示消息气泡背景。
     *
     * @param message 消息包装对象
     * @param content 消息内容
     * @return true 显示气泡，false 不显示气泡
     */
    protected boolean shouldShowBubble(T message, K content) {
        return true;
    }

    /**
     * Bind UI wrapper to the view.
     *
     * @param message  UiMessage wrapper
     * @param content  SDK message content
     * @param isGroup  whether the conversation is a group
     * @param itemView
     */
    final public void bind(T message, K content, boolean isGroup, View itemView) {
        boolean isSend = message.getDirection() == Message.MessageDirection.SEND;
        boolean showBubble = shouldShowBubble(message, content);

        resetMessageMetaViews(itemView);
        configureBubbleStyle(itemView, showBubble, isSend);

        ImageView ivAvatar = itemView.findViewById(R.id.image_avatar);
        String senderId = message.getSenderId();
        UserInfo sendUser = null;
        if (isSend) {
            String currentUserId = JIM.getInstance().getCurrentUserId();
            if (!TextUtils.isEmpty(currentUserId)) {
                senderId = currentUserId;
            }
            sendUser = JIM.getInstance().getUserInfoManager().getUserInfo(senderId);
        }
        if (sendUser == null) {
            if (TextUtils.isEmpty(senderId) && isSend) {
                senderId = JIM.getInstance().getCurrentUserId();
            }
            sendUser = JIM.getInstance().getUserInfoManager().getUserInfo(senderId);
        }
        String senderName = sendUser != null ? sendUser.getUserName() : message.getSenderName();
        String senderPortrait = sendUser != null ? sendUser.getPortrait() : null;

        if (ivAvatar != null) {
            ivAvatar.setVisibility(VISIBLE);
            AvatarUtils.loadAvatar(ivAvatar, senderPortrait, senderName, senderId);
        }
        if (!TextUtils.isEmpty(senderName)) {
            message.setSenderName(senderName);
        }
        TextView txSender = itemView.findViewById(R.id.text_sender_name);
        if (txSender != null) {
            if (isGroup && !isSend) {
                txSender.setVisibility(VISIBLE);
                txSender.setText(!TextUtils.isEmpty(senderName) ? senderName : senderId);
            } else {
                txSender.setVisibility(GONE);
            }
        }

        ProgressBar progressBar = itemView.findViewById(R.id.msg_send_status);
        ViewGroup msgStatusContainer = itemView.findViewById(R.id.msg_status_container);
        ImageView ivStatus = msgStatusContainer != null ? msgStatusContainer.findViewById(R.id.msg_read_status) : null;

        ProgressBar imageProgressBar = itemView.findViewById(R.id.image_msg_send_status);
        ViewGroup imageStatusContainer = itemView.findViewById(R.id.image_msg_status_container);
        ImageView imageStatusView = itemView.findViewById(R.id.image_msg_read_status);

        if (isSend) {
            if (showBubble) {
                hideStatus(imageStatusContainer, imageStatusView, imageProgressBar);
                bindSendStatus(message, msgStatusContainer, ivStatus, progressBar);
            } else {
                hideStatus(msgStatusContainer, ivStatus, progressBar);
                bindSendStatus(message, imageStatusContainer, imageStatusView, imageProgressBar);
            }
        } else {
            hideStatus(msgStatusContainer, ivStatus, progressBar);
            hideStatus(imageStatusContainer, imageStatusView, imageProgressBar);
        }

        TextView vMsgTime = itemView.findViewById(R.id.msg_sent_time);
        TextView imageMsgTime = itemView.findViewById(R.id.image_msg_time);
        String spanTimeTxt = message.getMessage().isEdit() ? "（已修改）" : "";
        if (isSend) {
            spanTimeTxt += MessageUtils.formatTimestamp(message.getMessage().getTimestamp());
        } else {
            spanTimeTxt = MessageUtils.formatTimestamp(message.getMessage().getTimestamp()) + spanTimeTxt;
        }

        if (showBubble) {
            if (imageMsgTime != null) {
                imageMsgTime.setVisibility(GONE);
            }
            if (vMsgTime != null) {
                vMsgTime.setVisibility(VISIBLE);
                vMsgTime.setText(spanTimeTxt);
                if (isSend) {
                    vMsgTime.setTextColor(0xCCFFFFFF);
                } else {
                    vMsgTime.setTextColor(0xFF9AA0AB);
                }
            }
        } else {
            if (vMsgTime != null) {
                vMsgTime.setVisibility(GONE);
            }
            if (imageMsgTime != null) {
                imageMsgTime.setVisibility(VISIBLE);
                imageMsgTime.setText(spanTimeTxt);
                imageMsgTime.setTextColor(0xF2FFFFFF);
            }
        }
        this.bindItem(message, content, isGroup);
    }

    private void resetMessageMetaViews(View itemView) {
        ViewGroup msgStatusContainer = itemView.findViewById(R.id.msg_status_container);
        ImageView msgStatusView = itemView.findViewById(R.id.msg_read_status);
        ProgressBar msgProgressBar = itemView.findViewById(R.id.msg_send_status);
        if (msgStatusContainer != null) {
            msgStatusContainer.setVisibility(GONE);
        }
        if (msgStatusView != null) {
            msgStatusView.setVisibility(GONE);
            msgStatusView.setImageDrawable(null);
            msgStatusView.clearColorFilter();
        }
        if (msgProgressBar != null) {
            msgProgressBar.setVisibility(GONE);
        }

        ViewGroup imageStatusContainer = itemView.findViewById(R.id.image_msg_status_container);
        ImageView imageStatusView = itemView.findViewById(R.id.image_msg_read_status);
        ProgressBar imageProgressBar = itemView.findViewById(R.id.image_msg_send_status);
        if (imageStatusContainer != null) {
            imageStatusContainer.setVisibility(GONE);
        }
        if (imageStatusView != null) {
            imageStatusView.setVisibility(GONE);
            imageStatusView.setImageDrawable(null);
            imageStatusView.clearColorFilter();
        }
        if (imageProgressBar != null) {
            imageProgressBar.setVisibility(GONE);
        }

        TextView msgTime = itemView.findViewById(R.id.msg_sent_time);
        if (msgTime != null) {
            msgTime.setVisibility(GONE);
        }
        TextView imageMsgTime = itemView.findViewById(R.id.image_msg_time);
        if (imageMsgTime != null) {
            imageMsgTime.setVisibility(GONE);
        }
    }

    /**
     * 绑定发送状态视图。
     *
     * @param message 消息包装对象
     * @param statusContainer 状态容器
     * @param statusView 已发送/已读/失败图标
     * @param progressBar 发送中进度条
     */
    private void bindSendStatus(T message, ViewGroup statusContainer, ImageView statusView, ProgressBar progressBar) {
        if (statusContainer == null && statusView == null && progressBar == null) {
            return;
        }
        if (statusContainer != null) {
            statusContainer.setVisibility(VISIBLE);
        }
        if (statusView != null) {
            statusView.setVisibility(GONE);
            statusView.setImageDrawable(null);
            statusView.clearColorFilter();
        }
        if (progressBar != null) {
            progressBar.setVisibility(GONE);
        }

        int msgState = message.getMessage().getState() != null
                ? message.getMessage().getState().getValue()
                : -1;
        boolean isSending = msgState == Message.MessageState.SENDING.getValue()
                || msgState == Message.MessageState.UPLOADING.getValue();

        if (isSending) {
            if (progressBar != null) {
                progressBar.setVisibility(VISIBLE);
            }
        } else if (message.getMessage().isHasRead()) {
            if (statusView != null) {
                statusView.setVisibility(VISIBLE);
                statusView.setImageResource(R.drawable.ic_msg_read);
            }
        } else if (msgState == Message.MessageState.SENT.getValue()) {
            if (statusView != null) {
                statusView.setVisibility(VISIBLE);
                statusView.setImageResource(R.drawable.ic_msg_sent);
            }
        } else if (msgState == Message.MessageState.FAIL.getValue()) {
            if (statusView != null) {
                statusView.setVisibility(VISIBLE);
                statusView.setImageResource(R.drawable.ic_send_error);
            }
        } else {
            if (statusContainer != null) {
                statusContainer.setVisibility(GONE);
            }
        }
    }

    private void hideStatus(ViewGroup statusContainer, ImageView statusView, ProgressBar progressBar) {
        if (statusContainer != null) {
            statusContainer.setVisibility(GONE);
        }
        if (statusView != null) {
            statusView.setVisibility(GONE);
        }
        if (progressBar != null) {
            progressBar.setVisibility(GONE);
        }
    }

    /**
     * 根据消息类型设置气泡背景与内边距。
     *
     * @param itemView 消息根视图
     * @param showBubble true 显示气泡，false 不显示气泡
     * @param isSend 是否发送消息
     */
    private void configureBubbleStyle(View itemView, boolean showBubble, boolean isSend) {
        ViewGroup bubbleContainer = itemView.findViewById(R.id.message_bubble_container);
        ViewGroup contentContainer = itemView.findViewById(R.id.message_content_container);
        if (bubbleContainer == null) {
            return;
        }
        if (!showBubble) {
            bubbleContainer.setBackgroundResource(R.drawable.bg_message_none);
            bubbleContainer.setPadding(0, 0, 0, 0);
            bubbleContainer.setMinimumWidth(0);
            bubbleContainer.setMinimumHeight(0);
            if (contentContainer != null) {
                contentContainer.setMinimumWidth(0);
            }
        } else {
            bubbleContainer.setBackgroundResource(isSend ? R.drawable.bg_message_sent : R.drawable.bg_message_received);
            bubbleContainer.setPadding(
                    dp(itemView, 4),
                    dp(itemView, 3),
                    dp(itemView, 4),
                    dp(itemView, 2)
            );
            bubbleContainer.setMinimumWidth(dp(itemView, 50));
            if (contentContainer != null) {
                contentContainer.setMinimumWidth(dp(itemView, 50));
            }
        }
    }

    private int dp(View itemView, int value) {
        return Math.round(value * itemView.getResources().getDisplayMetrics().density);
    }
}

package com.juggle.im.android.chat.provider;

import static android.view.View.GONE;
import static android.view.View.VISIBLE;

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

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

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
     * Bind UI wrapper to the view.
     *
     * @param message  UiMessage wrapper
     * @param content  SDK message content
     * @param isGroup  whether the conversation is a group
     * @param itemView
     */
    final public void bind(T message, K content, boolean isGroup, View itemView) {
        ImageView ivAvatar = itemView.findViewById(R.id.image_avatar);
        UserInfo sendUser = JIM.getInstance().getUserInfoManager().getUserInfo(message.getSenderId());
        if (sendUser != null && ivAvatar != null) {
            String name = sendUser.getUserName();
            message.setSenderName(name);
            AvatarUtils.loadAvatar(ivAvatar, sendUser.getPortrait(), name, sendUser.getUserId());
            TextView txSender = itemView.findViewById(R.id.text_sender_name);
            if (txSender != null) {
                if (isGroup && message.getDirection() != com.juggle.im.model.Message.MessageDirection.SEND) {
                    txSender.setVisibility(VISIBLE);
                    txSender.setText(sendUser.getUserName());
                } else {
                    txSender.setVisibility(GONE);
                }
            }
        }
        TextView vMsgTime = itemView.findViewById(R.id.msg_sent_time);
        if (message.getDirection() == com.juggle.im.model.Message.MessageDirection.SEND) {
            ProgressBar progressBar = itemView.findViewById(R.id.msg_send_status);
            ViewGroup msgStatusContainer = itemView.findViewById(R.id.msg_status_container);
            if (progressBar != null) {
                if (message.getMessage().getState().getValue() == Message.MessageState.SENDING.getValue()
                        || message.getMessage().getState().getValue() == Message.MessageState.UPLOADING.getValue()) {
                    progressBar.setVisibility(VISIBLE);
                } else {
                    progressBar.setVisibility(GONE);
                }
            }
            if (msgStatusContainer != null) {
                msgStatusContainer.setVisibility(VISIBLE);
                ImageView ivStatus = msgStatusContainer.findViewById(R.id.msg_read_status);
                // 已读
                if (message.getMessage().isHasRead()) {
                    if (ivStatus != null) {
                        ivStatus.setVisibility(VISIBLE);
                        ivStatus.setImageResource(R.drawable.ic_msg_read);
                    }
                }
                // 已发送
                else if (message.getMessage().getState().getValue() == Message.MessageState.SENT.getValue()) {
                    if (ivStatus != null) {
                        ivStatus.setVisibility(VISIBLE);
                        ivStatus.setImageResource(R.drawable.ic_msg_sent);
                    }
                } else if (message.getMessage().getState().getValue() == Message.MessageState.FAIL.getValue()) {
                    if (ivStatus != null) {
                        ivStatus.setVisibility(VISIBLE);
                        ivStatus.setImageResource(R.drawable.ic_send_error);
                    }
                }
            }
        }
        if (vMsgTime != null) {
            String spanTimeTxt = message.getMessage().isEdit() ? "（已修改）" : "";
            if (message.getDirection() == Message.MessageDirection.SEND) {
                spanTimeTxt += MessageUtils.formatTimestamp(message.getMessage().getTimestamp());
            } else {
                spanTimeTxt = MessageUtils.formatTimestamp(message.getMessage().getTimestamp()) + spanTimeTxt;
            }
            vMsgTime.setText(spanTimeTxt);
            if (message.getDirection() == Message.MessageDirection.SEND) {
                vMsgTime.setTextColor(0xCCFFFFFF);
            } else {
                vMsgTime.setTextColor(0xFF9AA0AB);
            }
        }
        this.bindItem(message, content, isGroup);
    }
}

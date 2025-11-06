package com.juggle.im.android.chat.provider;

import android.annotation.SuppressLint;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.juggle.im.JIM;
import com.juggle.im.android.R;
import com.juggle.im.android.chat.message.FriendNotifyMessage;
import com.juggle.im.android.chat.message.GroupNotifyMessage;
import com.juggle.im.android.chat.message.InsertTimeStatusMessage;
import com.juggle.im.android.model.UiMessage;
import com.juggle.im.model.MessageContent;
import com.juggle.im.model.UserInfo;
import com.juggle.im.model.messages.RecallInfoMessage;

/**
 * Text message content view.
 */
public class StatusMessageView extends MessageView<UiMessage, MessageContent> {
    public StatusMessageView(@NonNull ViewGroup root) {
        super(root, R.layout.content_notification);
    }

    public int getViewTemplateResId(UiMessage t) {
        return R.layout.item_message_notification;
    }

    @SuppressLint("SetTextI18n")
    @Override
    public void bindItem(UiMessage m, MessageContent t, boolean isGroup) {
        TextView tvContent = this.itemView.findViewById(R.id.text_message_content);
        if (t instanceof GroupNotifyMessage) {
            GroupNotifyMessage msg = (GroupNotifyMessage) t;
            tvContent.setText(msg.description());
        } else if (t instanceof FriendNotifyMessage) {
            FriendNotifyMessage msg = (FriendNotifyMessage) t;
            UserInfo userInfo = JIM.getInstance().getUserInfoManager().getUserInfo(m.getSenderId());
            tvContent.setText((userInfo != null ? userInfo.getUserName() : "") + msg.description() + "你为好友");
        } else if (t instanceof InsertTimeStatusMessage) {
            InsertTimeStatusMessage msg = (InsertTimeStatusMessage) t;
            tvContent.setText(msg.description());
        } else if (t instanceof RecallInfoMessage) {
            UserInfo userInfo = JIM.getInstance().getUserInfoManager().getUserInfo(m.getSenderId());
            tvContent.setText((userInfo != null ? userInfo.getUserName() : "") + "撤回了一条消息");
        } else {
            tvContent.setText(this.itemView.getResources().getString(R.string.unknown_message));
        }
    }
}

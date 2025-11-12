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
import com.juggle.im.android.chat.utils.MessageUtils;
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

    @SuppressLint("SetTextI18n")
    @Override
    public void bindItem(UiMessage m, MessageContent t, boolean isGroup) {
        TextView tvContent = this.itemView.findViewById(R.id.text_message_content);
        UserInfo userInfo = JIM.getInstance().getUserInfoManager().getUserInfo(m.getSenderId());
        String txt = MessageUtils.getStatusMessageSummary(m.getMessage().getContent(), userInfo);
        tvContent.setText(txt);
    }
}

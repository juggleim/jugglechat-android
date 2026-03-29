package com.juggle.im.android.chat.provider;

import android.text.SpannableString;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;

import com.juggle.im.android.R;
import com.juggle.im.android.chat.utils.MessageUtils;
import com.juggle.im.android.model.UiMessage;
import com.juggle.im.model.Message;
import com.juggle.im.model.MessageMentionInfo;
import com.juggle.im.model.messages.TextMessage;

/**
 * Text message content view.
 */
public class TextMessageView extends MessageView<UiMessage, TextMessage> {
    public TextMessageView(@NonNull ViewGroup root) {
        super(root, R.layout.content_text);
    }

    @Override
    public void bindItem(UiMessage m, TextMessage t, boolean isGroup) {
        TextView tvContent = this.itemView.findViewById(R.id.text_message_content);

        // 处理 @提及 文本高亮
        MessageMentionInfo mentionInfo = m.getMessage().getMentionInfo();
        if (mentionInfo != null) {
            SpannableString spannable = MessageUtils.formatMentionText(t.getContent(), mentionInfo, itemView.getContext());
            tvContent.setText(spannable);
        } else {
            tvContent.setText(t.getContent());
        }

        if (m.getDirection() == Message.MessageDirection.SEND) {
            tvContent.setTextColor(ContextCompat.getColor(itemView.getContext(), R.color.white));
        } else {
            tvContent.setTextColor(ContextCompat.getColor(itemView.getContext(), R.color.black));
        }
    }
}

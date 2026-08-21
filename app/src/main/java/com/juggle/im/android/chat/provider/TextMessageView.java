package com.juggle.im.android.chat.provider;

import android.text.SpannableString;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;

import com.juggle.im.android.R;
import com.juggle.im.android.chat.utils.MessageLinkUtils;
import com.juggle.im.android.chat.utils.MessageUtils;
import com.juggle.im.android.model.UiMessage;
import com.juggle.im.model.Message;
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

        boolean isSend = m.getDirection() == Message.MessageDirection.SEND;

        // 处理 @提及 文本高亮（将 {userId} 替换为 @用户名）
        SpannableString spannable = MessageUtils.formatMentionText(t.getContent(), m.getMessage().getMentionInfo(), itemView.getContext());
        // 识别文本中的网址并支持点击打开内置 WebView
        MessageLinkUtils.applyLinks(tvContent, spannable, isSend);

        if (isSend) {
            tvContent.setTextColor(ContextCompat.getColor(itemView.getContext(), R.color.white));
        } else {
            tvContent.setTextColor(ContextCompat.getColor(itemView.getContext(), R.color.black));
        }
    }
}

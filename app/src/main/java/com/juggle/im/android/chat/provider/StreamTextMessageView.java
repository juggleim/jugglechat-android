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
import com.juggle.im.model.messages.StreamTextMessage;

/**
 * Stream text message content view.
 * 用于显示 AI 流式输出的文本消息
 */
public class StreamTextMessageView extends MessageView<UiMessage, StreamTextMessage> {
    public StreamTextMessageView(@NonNull ViewGroup root) {
        super(root, R.layout.content_text);
    }

    @Override
    public void bindItem(UiMessage m, StreamTextMessage t, boolean isGroup) {
        TextView tvContent = this.itemView.findViewById(R.id.text_message_content);

        // 处理 @提及 文本高亮（将 {userId} 替换为 @用户名）
        SpannableString spannable = MessageUtils.formatMentionText(t.getContent(), m.getMessage().getMentionInfo(), itemView.getContext());
        tvContent.setText(spannable);

        if (m.getDirection() == Message.MessageDirection.SEND) {
            tvContent.setTextColor(ContextCompat.getColor(itemView.getContext(), R.color.white));
        } else {
            tvContent.setTextColor(ContextCompat.getColor(itemView.getContext(), R.color.black));
        }
    }
}
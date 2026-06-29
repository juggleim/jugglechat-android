package com.juggle.im.android.chat.provider;

import android.text.SpannableString;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;

import com.juggle.im.android.R;
import com.juggle.im.android.chat.utils.MarkdownRenderer;
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

        // 先设置文字颜色，Markwon 渲染正文时会沿用 TextView 当前的 textColor
        if (m.getDirection() == Message.MessageDirection.SEND) {
            tvContent.setTextColor(ContextCompat.getColor(itemView.getContext(), R.color.white));
        } else {
            tvContent.setTextColor(ContextCompat.getColor(itemView.getContext(), R.color.black));
        }

        if (hasMention(m)) {
            // 含 @提及 的消息：保留原有的提及高亮逻辑（AI 流式文本基本不会带提及）
            SpannableString spannable = MessageUtils.formatMentionText(
                    t.getContent(), m.getMessage().getMentionInfo(), itemView.getContext());
            tvContent.setText(spannable);
        } else {
            // AI 流式文本支持 Markdown 渲染；流式过程中即使是不完整片段，Markwon 也会尽力渲染
            MarkdownRenderer.render(tvContent, t.getContent());
        }
    }

    private boolean hasMention(UiMessage m) {
        return m.getMessage().getMentionInfo() != null
                && m.getMessage().getMentionInfo().getTargetUsers() != null
                && !m.getMessage().getMentionInfo().getTargetUsers().isEmpty();
    }
}
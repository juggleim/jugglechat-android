package com.juggle.im.android.chat.provider;

import android.content.res.ColorStateList;
import android.graphics.Color;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;

import com.juggle.im.android.R;
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
        tvContent.setText(t.getContent());
    }
}

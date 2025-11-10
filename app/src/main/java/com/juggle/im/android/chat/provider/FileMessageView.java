package com.juggle.im.android.chat.provider;

import android.content.res.ColorStateList;
import android.graphics.Color;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;

import com.juggle.im.android.R;
import com.juggle.im.android.model.UiMessage;
import com.juggle.im.model.Message;
import com.juggle.im.model.messages.FileMessage;

/**
 * File message content view.
 */
public class FileMessageView extends MessageView<UiMessage, FileMessage> {
    public FileMessageView(@NonNull ViewGroup root) {
        super(root, R.layout.content_file);
    }

    @Override
    public void bindItem(UiMessage m, FileMessage f, boolean isGroup) {
        ImageView ivIcon = this.itemView.findViewById(R.id.image_file_icon);
        TextView tvName = this.itemView.findViewById(R.id.text_file_name);
        ImageView btnDownload = this.itemView.findViewById(R.id.button_download_file);
        ivIcon.setImageResource(R.drawable.ic_file);
        String name = f.getName();
        String url = f.getUrl();
        if (tvName != null) tvName.setText(name);
        if (btnDownload != null) {
            btnDownload.setOnClickListener(v -> {
                btnDownload.setImageResource(R.drawable.ic_download);
            });
        }
        if (tvName != null) {
            if (m.getMessage().getDirection() == Message.MessageDirection.SEND) {
                tvName.setTextColor(ColorStateList.valueOf(Color.WHITE));
            } else {
                tvName.setTextColor(ContextCompat.getColor(itemView.getContext(), R.color.black));
            }
        }
    }
}

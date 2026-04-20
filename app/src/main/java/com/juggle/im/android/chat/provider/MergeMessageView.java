package com.juggle.im.android.chat.provider;

import android.content.res.ColorStateList;
import android.graphics.Color;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;

import com.juggle.im.JIM;
import com.juggle.im.android.R;
import com.juggle.im.android.chat.MergeMessageActivity;
import com.juggle.im.android.model.UiMessage;
import com.juggle.im.model.MergeMessagePreviewUnit;
import com.juggle.im.model.Message;
import com.juggle.im.model.UserInfo;
import com.juggle.im.model.messages.MergeMessage;
import com.qiniu.android.utils.StringUtils;

import java.util.List;

/**
 * Provider view for MergeMessage preview (shows first up to 4 messages as a summary).
 */
public class MergeMessageView extends MessageView<UiMessage, MergeMessage> {
    public MergeMessageView(@NonNull ViewGroup root) {
        super(root, R.layout.content_merge);
    }

    @Override
    public void bindItem(UiMessage m, MergeMessage merge, boolean isGroup) {
        TextView tvPreview = this.itemView.findViewById(R.id.merge_preview_text);
        TextView tvTitle = this.itemView.findViewById(R.id.merge_msg_title);
        tvTitle.setText(merge.getTitle());

        List<MergeMessagePreviewUnit> msgs = merge.getPreviewList();
        StringBuilder sb = new StringBuilder();
        int show = Math.min(4, msgs.size());
        for (int i = 0; i < show; i++) {
            MergeMessagePreviewUnit previewUnit = msgs.get(i);
            String name = previewUnit.getSender() != null ? previewUnit.getSender().getUserName() : null;
            if (StringUtils.isBlank(name) && previewUnit.getSender() != null) {
                UserInfo ui = JIM.getInstance().getUserInfoManager().getUserInfo(previewUnit.getSender().getUserId());
                if (ui != null) {
                    name = ui.getUserName();
                }
            }
            if (StringUtils.isBlank(name)) {
                name = "未知用户";
            }
            String previewContent = previewUnit.getPreviewContent();
            if (StringUtils.isBlank(previewContent)) {
                previewContent = "[消息]";
            }
            sb.append(name)
                    .append("：")
                    .append(previewContent);
            if (i < show - 1) {
                sb.append('\n');
            }
        }
        if (msgs.size() > 4) {
            sb.append("\n...");
        }
        tvPreview.setText(sb.toString());

        this.itemView.setOnClickListener(v -> MergeMessageActivity.start(this.itemView.getContext(), m.getMessageId()));
        this.itemView.setOnLongClickListener(v -> {
            ((ViewGroup) this.itemView.getParent()).performLongClick();
            return false;
        });
    }
}

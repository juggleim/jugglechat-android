package com.juggle.im.android.chat;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.juggle.im.JIM;
import com.juggle.im.android.R;
import com.juggle.im.model.MergeMessagePreviewUnit;
import com.juggle.im.model.UserInfo;
import com.qiniu.android.utils.StringUtils;

import java.util.List;

public class MergeListAdapter extends RecyclerView.Adapter<MergeListAdapter.Holder> {
    private final List<MergeMessagePreviewUnit> items;

    public MergeListAdapter(List<MergeMessagePreviewUnit> items) {
        this.items = items;
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_merge_message_row, parent, false);
        return new Holder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position) {
        MergeMessagePreviewUnit item = items.get(position);
        String name = item.getSender().getUserName();
        if (StringUtils.isBlank(name)) {
            UserInfo ui = JIM.getInstance().getUserInfoManager().getUserInfo(item.getSender().getUserId());
            if (ui != null) name = ui.getUserName();
        }
        StringBuilder sb = new StringBuilder();
        sb.append(name)
                .append(": ")
                .append(item.getPreviewContent()).append('\n');
        holder.tvContent.setText(item.getPreviewContent());
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class Holder extends RecyclerView.ViewHolder {
        TextView tvContent;

        Holder(@NonNull View itemView) {
            super(itemView);
            tvContent = itemView.findViewById(R.id.merge_item_content);
        }
    }
}

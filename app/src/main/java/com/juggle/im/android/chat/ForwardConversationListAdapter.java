package com.juggle.im.android.chat;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.juggle.im.android.R;
import com.juggle.im.android.model.UiConversation;
import com.juggle.im.android.utils.AvatarUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * Minimal adapter used by forward UI: only avatar + name.
 */
public class ForwardConversationListAdapter extends RecyclerView.Adapter<ForwardConversationListAdapter.ViewHolder> {
    private final List<UiConversation> items = new ArrayList<>();
    private OnConversationClickListener listener;

    public interface OnConversationClickListener {
        void onConversationClick(UiConversation uiConversation);
    }

    public void setOnConversationClickListener(OnConversationClickListener l) {
        this.listener = l;
    }

    public void setConversations(List<UiConversation> list) {
        items.clear();
        if (list != null) items.addAll(list);
        notifyDataSetChanged();
    }

    /**
     * Incrementally upsert a list of conversations into the adapter.
     * New conversations will be inserted at the top according to sort order.
     */
    public void upsertConversations(List<UiConversation> newConversations) {
        if (newConversations == null || newConversations.isEmpty()) return;
        for (UiConversation newUi : newConversations) {
            if (newUi == null) continue;

            long newSortTime = newUi.getSortTime();

            int existingIndex = -1;
            for (int i = 0; i < items.size(); i++) {
                UiConversation exist = items.get(i);
                if (exist.getId() != null && exist.getId().equals(newUi.getId())) {
                    existingIndex = i;
                    break;
                }
            }

            if (existingIndex >= 0) {
                // update data at existing index
                items.set(existingIndex, newUi);

                UiConversation removed = items.remove(existingIndex);

                int insertIndex = ConversationListAdapter.findInsertIndex(newUi.isTop(), newSortTime, items);
                items.add(insertIndex, removed);

                if (existingIndex != insertIndex) {
                    notifyItemMoved(existingIndex, insertIndex);
                    notifyItemChanged(insertIndex);
                } else {
                    notifyItemChanged(insertIndex);
                }
            } else {
                int insertIndex = ConversationListAdapter.findInsertIndex(newUi.isTop(), newSortTime, items);
                items.add(insertIndex, newUi);
                notifyItemInserted(insertIndex);
            }
        }
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_forward_conversation_list, parent, false);
        return new ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        holder.bind(items.get(position));
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    class ViewHolder extends RecyclerView.ViewHolder {
        ImageView avatar;
        TextView name;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            avatar = itemView.findViewById(R.id.iv_avatar);
            name = itemView.findViewById(R.id.tv_name);
            itemView.setOnClickListener(v -> {
                int pos = getAbsoluteAdapterPosition();
                if (pos != RecyclerView.NO_POSITION && listener != null) {
                    listener.onConversationClick(items.get(pos));
                }
            });
        }

        void bind(UiConversation ui) {
            name.setText(ui.getName());
            AvatarUtils.loadAvatar(avatar, ui.getAvatar(), ui.getName(), ui.getId());
            // pinned background similar to ConversationListAdapter
            if (ui.isTop()) {
                itemView.setBackgroundResource(R.drawable.bg_pinned);
            } else {
                itemView.setBackgroundResource(android.R.color.transparent);
            }
        }
    }
}

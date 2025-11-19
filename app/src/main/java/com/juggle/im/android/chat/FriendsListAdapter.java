package com.juggle.im.android.chat;

import android.annotation.SuppressLint;
import android.view.View;
import android.view.LayoutInflater;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.juggle.im.android.R;
import com.juggle.im.android.server.beans.FriendBean;
import com.juggle.im.android.utils.AvatarUtils;
import com.juggle.im.JIM;
import com.juggle.im.model.Conversation;

import android.content.Intent;

import java.util.ArrayList;
import java.util.List;

public class FriendsListAdapter extends RecyclerView.Adapter<FriendsListAdapter.VH> {

    private List<FriendBean> items = new ArrayList<>();
    private boolean selectionMode = false;
    private java.util.Map<String, Boolean> selectedMap = new java.util.HashMap<>();
    private OnSelectionChanged selectionChanged;

    @SuppressLint("NotifyDataSetChanged")
    public void setItems(List<FriendBean> list) {
        items.clear();
        if (list != null) items.addAll(list);
        notifyDataSetChanged();
    }

    public void setSelectionMode(boolean mode) {
        this.selectionMode = mode;
    }

    public void setSelectionChangedListener(OnSelectionChanged l) {
        this.selectionChanged = l;
    }

    public void uncheckUser(String userId) {
        selectedMap.remove(userId);
        notifyDataSetChanged();
    }

    interface OnSelectionChanged {
        void onSelectionChanged(FriendBean item, boolean selected);
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_friend, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH holder, int position) {
        FriendBean f = items.get(position);
        holder.tv.setText(f.getNickname() != null ? f.getNickname() : f.getUser_id());
        AvatarUtils.loadAvatar(holder.iv, f.getAvatar(), f.getNickname());
        if (selectionMode) {
            // show a selectable behavior: toggle selection on click
            holder.itemView.setOnClickListener(v -> {
                boolean cur = selectedMap.containsKey(f.getUser_id());
                if (cur) selectedMap.remove(f.getUser_id());
                else selectedMap.put(f.getUser_id(), true);
                notifyItemChanged(position);
                if (selectionChanged != null) selectionChanged.onSelectionChanged(f, !cur);
            });
            // indicate selection state by overlaying a small check image on the right
            View overlay = holder.itemView.findViewById(R.id.iv_checkbox);
            if (overlay != null)
                overlay.setVisibility(selectedMap.containsKey(f.getUser_id()) ? View.VISIBLE : View.GONE);
        } else {
            holder.itemView.setOnClickListener(v -> {
                Conversation convo = new Conversation(Conversation.ConversationType.PRIVATE, f.getUser_id());
                JIM.getInstance().getConversationManager().clearUnreadCount(convo, null);
                Intent intent = ConversationActivity.intentFor(v.getContext(), f.getUser_id(), false, f.getNickname());
                v.getContext().startActivity(intent);
            });
        }
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class VH extends RecyclerView.ViewHolder {
        ImageView iv;
        TextView tv;

        VH(@NonNull View itemView) {
            super(itemView);
            iv = itemView.findViewById(R.id.iv_avatar);
            tv = itemView.findViewById(R.id.tv_nickname);
        }
    }
}

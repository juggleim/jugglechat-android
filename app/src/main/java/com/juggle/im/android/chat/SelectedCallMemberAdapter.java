package com.juggle.im.android.chat;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.juggle.im.android.R;
import com.juggle.im.android.server.beans.GroupMemberBean;
import com.juggle.im.android.utils.AvatarUtils;

import java.util.List;

public class SelectedCallMemberAdapter extends RecyclerView.Adapter<SelectedCallMemberAdapter.SelectedMemberViewHolder> {

    private Context context;
    private List<GroupMemberBean> selectedMemberList;

    public SelectedCallMemberAdapter(Context context, List<GroupMemberBean> selectedMemberList) {
        this.context = context;
        this.selectedMemberList = selectedMemberList;
    }

    @NonNull
    @Override
    public SelectedMemberViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_call_selected_member_avatar, parent, false);
        return new SelectedMemberViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull SelectedMemberViewHolder holder, int position) {
        GroupMemberBean member = selectedMemberList.get(position);
        AvatarUtils.loadAvatar(holder.ivSelectedAvatar, member.getAvatar(), member.getNickname());
    }

    @Override
    public int getItemCount() {
        return selectedMemberList.size();
    }

    static class SelectedMemberViewHolder extends RecyclerView.ViewHolder {
        ImageView ivSelectedAvatar;

        public SelectedMemberViewHolder(@NonNull View itemView) {
            super(itemView);
            ivSelectedAvatar = itemView.findViewById(R.id.iv_selected_avatar);
        }
    }
}
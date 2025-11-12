package com.juggle.im.android.chat;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.juggle.im.android.R;
import com.juggle.im.android.server.beans.GroupMemberBean;
import com.juggle.im.android.utils.AvatarUtils;

import java.util.List;

public class SelectCallMemberAdapter extends RecyclerView.Adapter<SelectCallMemberAdapter.MemberViewHolder> {

    private Context context;
    private List<GroupMemberBean> memberList;
    private OnMemberSelectListener listener;

    public interface OnMemberSelectListener {
        void onMemberSelect(GroupMemberBean member, boolean isSelected);
    }

    public SelectCallMemberAdapter(Context context, List<GroupMemberBean> memberList, OnMemberSelectListener listener) {
        this.context = context;
        this.memberList = memberList;
        this.listener = listener;
    }

    @NonNull
    @Override
    public MemberViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_call_select_member, parent, false);
        return new MemberViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull MemberViewHolder holder, int position) {
        GroupMemberBean member = memberList.get(position);
        holder.tvNickname.setText(member.getNickname());
        AvatarUtils.loadAvatar(holder.ivAvatar, member.getAvatar(), member.getNickname());

        holder.itemView.setOnClickListener(v -> {
            holder.checkboxSelect.setChecked(!holder.checkboxSelect.isChecked());
        });

        holder.checkboxSelect.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (listener != null) {
                listener.onMemberSelect(member, isChecked);
            }
        });
    }

    @Override
    public int getItemCount() {
        return memberList.size();
    }

    static class MemberViewHolder extends RecyclerView.ViewHolder {
        CheckBox checkboxSelect;
        ImageView ivAvatar;
        TextView tvNickname;

        public MemberViewHolder(@NonNull View itemView) {
            super(itemView);
            checkboxSelect = itemView.findViewById(R.id.checkbox_select);
            ivAvatar = itemView.findViewById(R.id.iv_avatar);
            tvNickname = itemView.findViewById(R.id.tv_nickname);
        }
    }
}
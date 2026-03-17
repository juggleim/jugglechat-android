package com.juggle.im.android.chat;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.juggle.im.android.R;
import com.juggle.im.android.utils.AvatarUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ContactListAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
    private static final int VIEW_TYPE_SECTION = 1;
    private static final int VIEW_TYPE_FRIEND = 2;

    private final List<RowItem> rows = new ArrayList<>();
    private final Map<String, Integer> sectionPositionMap = new HashMap<>();
    private final OnFriendClickListener onFriendClickListener;

    public ContactListAdapter(OnFriendClickListener onFriendClickListener) {
        this.onFriendClickListener = onFriendClickListener;
    }

    public void submit(List<RowItem> newRows) {
        rows.clear();
        if (newRows != null) {
            rows.addAll(newRows);
        }
        sectionPositionMap.clear();
        for (int i = 0; i < rows.size(); i++) {
            RowItem row = rows.get(i);
            if (row instanceof SectionRow) {
                sectionPositionMap.put(((SectionRow) row).section, i);
            }
        }
        notifyDataSetChanged();
    }

    public int findSectionPosition(String section) {
        Integer position = sectionPositionMap.get(section);
        return position == null ? -1 : position;
    }

    @Override
    public int getItemViewType(int position) {
        return rows.get(position) instanceof SectionRow ? VIEW_TYPE_SECTION : VIEW_TYPE_FRIEND;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        if (viewType == VIEW_TYPE_SECTION) {
            View view = inflater.inflate(R.layout.item_contact_section, parent, false);
            return new SectionViewHolder(view);
        }
        View view = inflater.inflate(R.layout.item_contact_friend, parent, false);
        return new FriendViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        RowItem row = rows.get(position);
        if (holder instanceof SectionViewHolder) {
            ((SectionViewHolder) holder).tvSection.setText(((SectionRow) row).section);
            return;
        }
        FriendRow item = (FriendRow) row;
        FriendViewHolder vh = (FriendViewHolder) holder;
        vh.tvName.setText(item.displayName);
        AvatarUtils.loadAvatar(vh.ivAvatar, item.avatar, item.displayName, item.userId);
        vh.vDivider.setVisibility(item.showDivider ? View.VISIBLE : View.GONE);
        vh.itemView.setOnClickListener(v -> {
            if (onFriendClickListener != null) {
                onFriendClickListener.onFriendClick(item);
            }
        });
    }

    @Override
    public int getItemCount() {
        return rows.size();
    }

    public interface OnFriendClickListener {
        void onFriendClick(FriendRow friendRow);
    }

    public abstract static class RowItem {
    }

    public static final class SectionRow extends RowItem {
        public final String section;

        public SectionRow(String section) {
            this.section = section;
        }
    }

    public static final class FriendRow extends RowItem {
        public final String userId;
        public final String displayName;
        public final String avatar;
        public final String section;
        public final boolean showDivider;

        public FriendRow(String userId, String displayName, String avatar, String section, boolean showDivider) {
            this.userId = userId;
            this.displayName = displayName;
            this.avatar = avatar;
            this.section = section;
            this.showDivider = showDivider;
        }
    }

    static class SectionViewHolder extends RecyclerView.ViewHolder {
        final TextView tvSection;

        SectionViewHolder(@NonNull View itemView) {
            super(itemView);
            tvSection = itemView.findViewById(R.id.tv_section);
        }
    }

    static class FriendViewHolder extends RecyclerView.ViewHolder {
        final ImageView ivAvatar;
        final TextView tvName;
        final View vDivider;

        FriendViewHolder(@NonNull View itemView) {
            super(itemView);
            ivAvatar = itemView.findViewById(R.id.iv_avatar);
            tvName = itemView.findViewById(R.id.tv_name);
            vDivider = itemView.findViewById(R.id.v_divider);
        }
    }
}

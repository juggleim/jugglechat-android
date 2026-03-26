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
    private static final int VIEW_TYPE_ACTION = 3;

    public static final int ACTION_GROUPS = 1;
    public static final int ACTION_NEW_FRIENDS = 2;
    public static final int ACTION_BLACKLIST = 3;

    private final List<RowItem> rows = new ArrayList<>();
    private final Map<String, Integer> sectionPositionMap = new HashMap<>();
    private final OnFriendClickListener onFriendClickListener;
    private final OnActionClickListener onActionClickListener;

    public ContactListAdapter(OnFriendClickListener onFriendClickListener, OnActionClickListener onActionClickListener) {
        this.onFriendClickListener = onFriendClickListener;
        this.onActionClickListener = onActionClickListener;
    }

    /**
     * 提交并刷新通讯录列表数据。
     *
     * @param newRows 新的列表行数据
     */
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

    /**
     * 根据分组字母查找对应 section 在列表中的位置。
     *
     * @param section 分组字母（如 A/B/#）
     * @return section 对应的 adapter 位置，不存在返回 -1
     */
    public int findSectionPosition(String section) {
        Integer position = sectionPositionMap.get(section);
        return position == null ? -1 : position;
    }

    /**
     * 更新功能入口行（如“新朋友”）的红点状态。
     *
     * @param actionType 功能入口类型
     * @param showTip 是否显示红点
     */
    public void updateActionTip(int actionType, boolean showTip) {
        for (int i = 0; i < rows.size(); i++) {
            RowItem row = rows.get(i);
            if (!(row instanceof ActionRow)) {
                continue;
            }
            ActionRow actionRow = (ActionRow) row;
            if (actionRow.actionType != actionType) {
                continue;
            }
            if (actionRow.showTip == showTip) {
                return;
            }
            actionRow.showTip = showTip;
            notifyItemChanged(i);
            return;
        }
    }

    @Override
    public int getItemViewType(int position) {
        RowItem row = rows.get(position);
        if (row instanceof SectionRow) {
            return VIEW_TYPE_SECTION;
        }
        if (row instanceof ActionRow) {
            return VIEW_TYPE_ACTION;
        }
        return VIEW_TYPE_FRIEND;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        if (viewType == VIEW_TYPE_SECTION) {
            View view = inflater.inflate(R.layout.item_contact_section, parent, false);
            return new SectionViewHolder(view);
        }
        if (viewType == VIEW_TYPE_ACTION) {
            View view = inflater.inflate(R.layout.item_contact_action, parent, false);
            return new ActionViewHolder(view);
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
        if (holder instanceof ActionViewHolder) {
            ActionRow item = (ActionRow) row;
            ActionViewHolder vh = (ActionViewHolder) holder;
            vh.ivIcon.setImageResource(item.iconRes);
            vh.tvTitle.setText(item.title);
            vh.vTip.setVisibility(item.showTip ? View.VISIBLE : View.GONE);
            vh.vDivider.setVisibility(item.showDivider ? View.VISIBLE : View.GONE);
            vh.itemView.setOnClickListener(v -> {
                if (onActionClickListener != null) {
                    onActionClickListener.onActionClick(item.actionType);
                }
            });
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
        /**
         * 好友列表点击回调。
         *
         * @param friendRow 被点击的好友行
         */
        void onFriendClick(FriendRow friendRow);
    }

    public interface OnActionClickListener {
        /**
         * 功能入口点击回调。
         *
         * @param actionType 功能入口类型，见 ACTION_* 常量
         */
        void onActionClick(int actionType);
    }

    public abstract static class RowItem {
    }

    public static final class ActionRow extends RowItem {
        public final int actionType;
        public final int iconRes;
        public final String title;
        public final boolean showDivider;
        public boolean showTip;

        public ActionRow(int actionType, int iconRes, String title, boolean showDivider, boolean showTip) {
            this.actionType = actionType;
            this.iconRes = iconRes;
            this.title = title;
            this.showDivider = showDivider;
            this.showTip = showTip;
        }
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

    static class ActionViewHolder extends RecyclerView.ViewHolder {
        final ImageView ivIcon;
        final TextView tvTitle;
        final View vTip;
        final View vDivider;

        ActionViewHolder(@NonNull View itemView) {
            super(itemView);
            ivIcon = itemView.findViewById(R.id.iv_action_icon);
            tvTitle = itemView.findViewById(R.id.tv_action_title);
            vTip = itemView.findViewById(R.id.new_friend_tip);
            vDivider = itemView.findViewById(R.id.v_action_divider);
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

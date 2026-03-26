package com.juggle.im.android.app;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;

import com.juggle.im.android.R;
import com.juggle.im.android.utils.AvatarUtils;
import com.juggle.im.android.widget.JuggleCheckBox;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 创建群聊成员列表适配器。
 * 对外通过 {@link #submit(List, Set, Set)} 提交”分组头 + 成员行”混合数据，
 * 并提供 {@link #findSectionPosition(String)} 供字母索引快速定位。
 */
public class CreateGroupListAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private static final int VIEW_TYPE_SECTION = 1;
    private static final int VIEW_TYPE_MEMBER = 2;

    private final List<RowItem> rows = new ArrayList<>();
    private final Set<String> selectedUserIds = new HashSet<>();
    private final Set<String> disabledUserIds = new HashSet<>();
    private final Map<String, Integer> sectionPositionMap = new HashMap<>();
    private final OnMemberClickListener onMemberClickListener;
    private boolean showCheckBox = true;

    public CreateGroupListAdapter(OnMemberClickListener onMemberClickListener) {
        this.onMemberClickListener = onMemberClickListener;
    }

    /**
     * 提交渲染数据。
     *
     * @param newRows          混合行数据（分组头 + 成员行）
     * @param newSelectedIds   当前选中的成员 userId 集合
     * @param newDisabledIds   禁用选择的成员 userId 集合
     */
    public void submit(List<RowItem> newRows, Set<String> newSelectedIds, Set<String> newDisabledIds) {
        rows.clear();
        if (newRows != null) {
            rows.addAll(newRows);
        }

        selectedUserIds.clear();
        if (newSelectedIds != null) {
            selectedUserIds.addAll(newSelectedIds);
        }

        disabledUserIds.clear();
        if (newDisabledIds != null) {
            disabledUserIds.addAll(newDisabledIds);
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
     * 更新单个成员的选中状态（避免整个列表重绘导致的闪烁）
     */
    public void updateMemberSelection(String userId, boolean selected) {
        if (selected) {
            selectedUserIds.add(userId);
        } else {
            selectedUserIds.remove(userId);
        }

        for (int i = 0; i < rows.size(); i++) {
            RowItem row = rows.get(i);
            if (row instanceof MemberRow && ((MemberRow) row).userId.equals(userId)) {
                notifyItemChanged(i, Boolean.valueOf(selected));
                return;
            }
        }
    }

    public void setShowCheckBox(boolean showCheckBox) {
        if (this.showCheckBox == showCheckBox) {
            return;
        }
        this.showCheckBox = showCheckBox;
        notifyDataSetChanged();
    }

    /**
     * 查询某个字母分组的起始位置，不存在时返回 -1。
     */
    public int findSectionPosition(String section) {
        Integer position = sectionPositionMap.get(section);
        return position == null ? -1 : position;
    }

    @Override
    public int getItemViewType(int position) {
        RowItem rowItem = rows.get(position);
        if (rowItem instanceof SectionRow) {
            return VIEW_TYPE_SECTION;
        }
        return VIEW_TYPE_MEMBER;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        if (viewType == VIEW_TYPE_SECTION) {
            View view = inflater.inflate(R.layout.item_create_group_section, parent, false);
            return new SectionViewHolder(view);
        }
        View view = inflater.inflate(R.layout.item_create_group_member, parent, false);
        return new MemberViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        RowItem rowItem = rows.get(position);
        if (holder instanceof SectionViewHolder) {
            SectionRow item = (SectionRow) rowItem;
            ((SectionViewHolder) holder).tvSection.setText(item.section);
            return;
        }

        MemberRow item = (MemberRow) rowItem;
        MemberViewHolder vh = (MemberViewHolder) holder;
        bindMemberViewHolder(vh, item, selectedUserIds.contains(item.userId));
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position, @NonNull List<Object> payloads) {
        if (payloads.isEmpty()) {
            onBindViewHolder(holder, position);
            return;
        }

        RowItem rowItem = rows.get(position);
        if (rowItem instanceof MemberRow && holder instanceof MemberViewHolder) {
            MemberRow item = (MemberRow) rowItem;
            MemberViewHolder vh = (MemberViewHolder) holder;
            boolean selected = payloads.get(0) instanceof Boolean && (Boolean) payloads.get(0);
            bindMemberViewHolder(vh, item, selected);
        }
    }

    private void bindMemberViewHolder(MemberViewHolder vh, MemberRow item, boolean selected) {
        vh.tvName.setText(item.displayName);
        AvatarUtils.loadAvatar(vh.ivAvatar, item.avatar, item.displayName);
        vh.vDivider.setVisibility(item.showDivider ? View.VISIBLE : View.GONE);

        boolean disabled = disabledUserIds.contains(item.userId);
        vh.checkBox.setVisibility(showCheckBox ? View.VISIBLE : View.GONE);
        if (showCheckBox) {
            vh.checkBox.setChecked(selected);
            vh.checkBox.setDisabled(disabled);
            vh.ivAvatar.setAlpha(disabled ? 0.3f : 1f);
            vh.tvName.setAlpha(disabled ? 0.3f : 1f);
        } else {
            vh.checkBox.setChecked(false);
            vh.checkBox.setDisabled(false);
            vh.ivAvatar.setAlpha(1f);
            vh.tvName.setAlpha(1f);
        }

        vh.itemView.setOnClickListener(v -> {
            if (disabled) {
                return;
            }
            if (onMemberClickListener != null) {
                onMemberClickListener.onMemberClick(item);
            }
        });
    }

    @Override
    public int getItemCount() {
        return rows.size();
    }

    /**
     * 成员点击事件接口，由页面层实现选中状态维护。
     */
    public interface OnMemberClickListener {
        void onMemberClick(MemberRow memberRow);
    }

    public abstract static class RowItem {
    }

    public static final class SectionRow extends RowItem {
        public final String section;

        public SectionRow(String section) {
            this.section = section;
        }
    }

    public static final class MemberRow extends RowItem {
        public final String userId;
        public final String displayName;
        public final String avatar;
        public final String section;
        public final boolean showDivider;

        public MemberRow(String userId, String displayName, String avatar, String section, boolean showDivider) {
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

    static class MemberViewHolder extends RecyclerView.ViewHolder {
        final JuggleCheckBox checkBox;
        final ImageView ivAvatar;
        final TextView tvName;
        final View vDivider;

        MemberViewHolder(@NonNull View itemView) {
            super(itemView);
            checkBox = itemView.findViewById(R.id.checkbox);
            ivAvatar = itemView.findViewById(R.id.iv_avatar);
            tvName = itemView.findViewById(R.id.tv_name);
            vDivider = itemView.findViewById(R.id.v_divider);
        }
    }
}

package com.juggle.im.android.chat;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.juggle.im.android.component.AbsAppActivity;
import androidx.appcompat.widget.PopupMenu;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.juggle.im.android.R;
import com.juggle.im.android.server.beans.GroupDetailBean;
import com.juggle.im.android.server.beans.GroupMemberBean;
import com.juggle.im.android.server.http.ApiCallback;
import com.juggle.im.android.server.http.ServiceManager;
import com.juggle.im.android.utils.AvatarUtils;
import com.juggle.im.android.widget.AppConfirmDialog;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class GroupMembersActivity extends AbsAppActivity {
    private static final String EXTRA_GROUP_ID = "extra_group_id";
    private static final String EXTRA_GROUP_NAME = "extra_group_name";
    private static final String EXTRA_MY_ROLE = "extra_my_role";

    private static final int ROLE_OWNER = 1;
    private static final int ROLE_ADMIN = 2;

    private String groupId;
    private String groupName;
    private int myRole;
    private String ownerId;

    private ProgressBar progressBar;
    private TextView emptyView;
    private RecyclerView recyclerView;
    private MemberAdapter adapter;

    public static Intent intentFor(Context context, String groupId, String groupName, int myRole) {
        Intent intent = new Intent(context, GroupMembersActivity.class);
        intent.putExtra(EXTRA_GROUP_ID, groupId);
        intent.putExtra(EXTRA_GROUP_NAME, groupName);
        intent.putExtra(EXTRA_MY_ROLE, myRole);
        return intent;
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_group_members);
        setupWindowStyle();

        groupId = getIntent().getStringExtra(EXTRA_GROUP_ID);
        groupName = getIntent().getStringExtra(EXTRA_GROUP_NAME);
        myRole = getIntent().getIntExtra(EXTRA_MY_ROLE, 0);

        TextView titleView = findViewById(R.id.tv_title);
        titleView.setText("群组成员");
        findViewById(R.id.iv_back).setOnClickListener(v -> finish());

        progressBar = findViewById(R.id.progress_bar);
        emptyView = findViewById(R.id.tv_empty);
        recyclerView = findViewById(R.id.rv_members);

        adapter = new MemberAdapter((anchor, member) -> showMemberActions(anchor, member));
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(adapter);

        loadGroupMembers();
    }

    private void setupWindowStyle() {
        Window window = getWindow();
        window.setStatusBarColor(getColor(R.color.white));
        window.setNavigationBarColor(getColor(R.color.white));
        WindowInsetsControllerCompat controller = new WindowInsetsControllerCompat(window, window.getDecorView());
        controller.setAppearanceLightStatusBars(true);
    }

    private void loadGroupMembers() {
        progressBar.setVisibility(View.VISIBLE);
        ServiceManager.getUserService().getGroupInfo(groupId, new ApiCallback<GroupDetailBean>() {
            @Override
            public void onSuccess(GroupDetailBean data) {
                progressBar.setVisibility(View.GONE);
                if (data == null) {
                    showEmpty(Collections.emptyList());
                    return;
                }
                if (!TextUtils.isEmpty(data.getGroupName())) {
                    groupName = data.getGroupName();
                }
                if (data.getOwner() != null) {
                    ownerId = data.getOwner().getUserId();
                }
                myRole = data.getMyRole();

                List<GroupMemberBean> members = data.getMembers() == null
                        ? new ArrayList<>()
                        : new ArrayList<>(data.getMembers());
                showEmpty(members);
            }

            @Override
            public void onError(int code, String message) {
                progressBar.setVisibility(View.GONE);
                showEmpty(Collections.emptyList());
                Toast.makeText(GroupMembersActivity.this,
                        "加载群成员失败：" + message,
                        Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void showEmpty(List<GroupMemberBean> members) {
        adapter.submit(members, ownerId);
        boolean isEmpty = members == null || members.isEmpty();
        emptyView.setVisibility(isEmpty ? View.VISIBLE : View.GONE);
        recyclerView.setVisibility(isEmpty ? View.GONE : View.VISIBLE);
    }

    private void showMemberActions(View anchor, GroupMemberBean member) {
        if (!canManageMember(member)) {
            return;
        }
        PopupMenu popupMenu = new PopupMenu(this, anchor);
        popupMenu.inflate(R.menu.menu_group_member_actions);
        popupMenu.setOnMenuItemClickListener(item -> onMemberActionClicked(item, member));
        popupMenu.show();
    }

    private boolean onMemberActionClicked(MenuItem item, GroupMemberBean member) {
        int itemId = item.getItemId();
        if (itemId == R.id.action_mute_member) {
            muteMember(member);
            return true;
        }
        if (itemId == R.id.action_remove_member) {
            removeMember(member);
            return true;
        }
        if (itemId == R.id.action_multi_select_member) {
            Toast.makeText(this, "多选功能暂未接入", Toast.LENGTH_SHORT).show();
            return true;
        }
        return false;
    }

    private boolean canManageMember(GroupMemberBean member) {
        boolean owner = myRole == ROLE_OWNER;
        boolean admin = myRole == ROLE_ADMIN;
        if (!owner && !admin) {
            return false;
        }
        int targetRole = resolveRole(member);
        if (targetRole == ROLE_OWNER) {
            return false;
        }
        return !admin || targetRole != ROLE_ADMIN;
    }

    private void muteMember(GroupMemberBean member) {
        List<String> memberIds = new ArrayList<>();
        memberIds.add(member.getUserId());
        ServiceManager.getUserService().setGroupMemberMute(groupId, memberIds, true, new ApiCallback<Void>() {
            @Override
            public void onSuccess(Void data) {
                Toast.makeText(GroupMembersActivity.this, "已禁言", Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onError(int code, String message) {
                Toast.makeText(GroupMembersActivity.this, "禁言失败：" + message, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void removeMember(GroupMemberBean member) {
        AppConfirmDialog.builder(this)
                .setTitle("移除成员")
                .setMessage("确定移除成员 " + safeName(member) + "？")
                .setNegativeText(getString(R.string.txt_cancel))
                .setPositiveText(getString(R.string.create_group_confirm))
                .setOnPositiveClick(() -> {
                    List<String> memberIds = new ArrayList<>();
                    memberIds.add(member.getUserId());
                    ServiceManager.getUserService().removeGroupMembers(groupId, memberIds, new ApiCallback<Void>() {
                        @Override
                        public void onSuccess(Void data) {
                            adapter.remove(member);
                            Toast.makeText(GroupMembersActivity.this, "移除成功", Toast.LENGTH_SHORT).show();
                            if (adapter.getItemCount() == 0) {
                                emptyView.setVisibility(View.VISIBLE);
                                recyclerView.setVisibility(View.GONE);
                            }
                        }

                        @Override
                        public void onError(int code, String message) {
                            Toast.makeText(GroupMembersActivity.this,
                                    "移除失败：" + message,
                                    Toast.LENGTH_SHORT).show();
                        }
                    });
                })
                .show();
    }

    private int resolveRole(GroupMemberBean member) {
        if (member == null) {
            return 0;
        }
        if (!TextUtils.isEmpty(ownerId) && TextUtils.equals(ownerId, member.getUserId())) {
            return ROLE_OWNER;
        }
        if (member.getRole() > 0) {
            return member.getRole();
        }
        if (member.getMemberType() > 0) {
            return member.getMemberType();
        }
        return 0;
    }

    private String safeName(GroupMemberBean member) {
        if (member == null) {
            return "";
        }
        if (!TextUtils.isEmpty(member.getNickname())) {
            return member.getNickname();
        }
        return member.getUserId();
    }

    private static class MemberAdapter extends RecyclerView.Adapter<MemberAdapter.MemberViewHolder> {
        interface OnMemberLongClickListener {
            void onMemberLongClick(View anchor, GroupMemberBean member);
        }

        private final List<GroupMemberBean> items = new ArrayList<>();
        private final OnMemberLongClickListener listener;
        private String ownerId;

        MemberAdapter(OnMemberLongClickListener listener) {
            this.listener = listener;
        }

        void submit(List<GroupMemberBean> members, String ownerId) {
            this.ownerId = ownerId;
            items.clear();
            if (members != null) {
                items.addAll(members);
            }
            notifyDataSetChanged();
        }

        void remove(GroupMemberBean bean) {
            int index = items.indexOf(bean);
            if (index >= 0) {
                items.remove(index);
                notifyItemRemoved(index);
                notifyItemRangeChanged(index, items.size() - index);
            }
        }

        @NonNull
        @Override
        public MemberViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_group_member_manage, parent, false);
            return new MemberViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull MemberViewHolder holder, int position) {
            GroupMemberBean item = items.get(position);
            String name = TextUtils.isEmpty(item.getNickname()) ? item.getUserId() : item.getNickname();
            AvatarUtils.loadAvatar(holder.avatarView, item.getAvatar(), name, item.getUserId());
            holder.nameView.setText(name);

            int role = item.getRole() > 0 ? item.getRole() : item.getMemberType();
            if (!TextUtils.isEmpty(ownerId) && TextUtils.equals(ownerId, item.getUserId())) {
                role = ROLE_OWNER;
            }
            if (role == ROLE_OWNER) {
                holder.roleView.setVisibility(View.VISIBLE);
                holder.roleView.setText("群主");
            } else if (role == ROLE_ADMIN) {
                holder.roleView.setVisibility(View.VISIBLE);
                holder.roleView.setText("管理员");
            } else {
                holder.roleView.setVisibility(View.GONE);
            }
            holder.dividerView.setVisibility(position == items.size() - 1 ? View.GONE : View.VISIBLE);

            holder.itemView.setOnLongClickListener(v -> {
                if (listener != null) {
                    listener.onMemberLongClick(v, item);
                }
                return true;
            });
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        static class MemberViewHolder extends RecyclerView.ViewHolder {
            final ImageView avatarView;
            final TextView nameView;
            final TextView roleView;
            final View dividerView;

            MemberViewHolder(@NonNull View itemView) {
                super(itemView);
                avatarView = itemView.findViewById(R.id.iv_avatar);
                nameView = itemView.findViewById(R.id.tv_name);
                roleView = itemView.findViewById(R.id.tv_role);
                dividerView = itemView.findViewById(R.id.v_divider);
            }
        }
    }
}

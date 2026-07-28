package com.juggle.im.android.chat;

import static com.juggle.im.android.chat.SelectMemberActivity.DISABLE_MEMBERS;
import static com.juggle.im.android.chat.SelectMemberActivity.GROUP_ID;
import static com.juggle.im.android.chat.SelectMemberActivity.SELECTED_MEMBERS;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
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
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.juggle.im.android.R;
import com.juggle.im.android.chat.component.UserListAdapter;
import com.juggle.im.android.server.beans.GroupMemberBean;
import com.juggle.im.android.server.beans.GroupMembersData;
import com.juggle.im.android.server.http.ApiCallback;
import com.juggle.im.android.server.http.ServiceManager;
import com.juggle.im.android.utils.AvatarUtils;
import com.juggle.im.android.widget.AppConfirmDialog;

import java.util.ArrayList;
import java.util.List;
import com.juggle.im.android.utils.LogUtils;

public class GroupAdminsActivity extends AbsAppActivity {
    private static final String EXTRA_GROUP_ID = "extra_group_id";
    private static final String EXTRA_OWNER_ID = "extra_owner_id";
    private static final int REQ_ADD_ADMINS = 3001;
    private static final int MAX_ADMIN_COUNT = 3;

    private String groupId;
    private String ownerId;

    private ProgressBar progressBar;
    private TextView emptyView;
    private TextView addAdminButton;
    private RecyclerView recyclerView;
    private AdminAdapter adapter;

    private final ArrayList<GroupMemberBean> admins = new ArrayList<>();
    private final ArrayList<String> adminIds = new ArrayList<>();

    public static Intent intentFor(Context context, String groupId, String ownerId) {
        Intent intent = new Intent(context, GroupAdminsActivity.class);
        intent.putExtra(EXTRA_GROUP_ID, groupId);
        intent.putExtra(EXTRA_OWNER_ID, ownerId);
        return intent;
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_group_admins);
        setupWindowStyle();

        groupId = getIntent().getStringExtra(EXTRA_GROUP_ID);
        ownerId = getIntent().getStringExtra(EXTRA_OWNER_ID);

        findViewById(R.id.iv_back).setOnClickListener(v -> finish());
        addAdminButton = findViewById(R.id.tv_add_admin);
        progressBar = findViewById(R.id.progress_bar);
        emptyView = findViewById(R.id.tv_empty);
        recyclerView = findViewById(R.id.rv_admins);

        adapter = new AdminAdapter(new AdminAdapter.ActionListener() {
            @Override
            public void onDelete(GroupMemberBean member) {
                removeAdmin(member);
            }
        });
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(adapter);

        addAdminButton.setOnClickListener(v -> addAdmins());

        loadAdmins();
    }

    private void setupWindowStyle() {
        Window window = getWindow();
        window.setStatusBarColor(getColor(R.color.white));
        window.setNavigationBarColor(getColor(R.color.white));
        WindowInsetsControllerCompat controller = new WindowInsetsControllerCompat(window, window.getDecorView());
        controller.setAppearanceLightStatusBars(true);
    }

    private void loadAdmins() {
        progressBar.setVisibility(View.VISIBLE);
        ServiceManager.getUserService().getGroupAdmins(groupId, new ApiCallback<GroupMembersData>() {
            @Override
            public void onSuccess(GroupMembersData data) {
                progressBar.setVisibility(View.GONE);
                admins.clear();
                adminIds.clear();
                if (data != null && data.getItems() != null) {
                    admins.addAll(data.getItems());
                    for (GroupMemberBean admin : admins) {
                        if (!TextUtils.isEmpty(admin.getUserId())) {
                            adminIds.add(admin.getUserId());
                        }
                    }
                }
                adapter.submit(admins);
                renderEmptyState();
            }

            @Override
            public void onError(int code, String message) {
                progressBar.setVisibility(View.GONE);
                renderEmptyState();
                LogUtils.serverError("group", "loadAdmins", code, message);
                Toast.makeText(GroupAdminsActivity.this,
                        R.string.group_admins_load_failed,
                        Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void renderEmptyState() {
        boolean isEmpty = admins.isEmpty();
        emptyView.setVisibility(isEmpty ? View.VISIBLE : View.GONE);
        recyclerView.setVisibility(isEmpty ? View.GONE : View.VISIBLE);
        addAdminButton.setVisibility(admins.size() < MAX_ADMIN_COUNT ? View.VISIBLE : View.GONE);
    }

    private void addAdmins() {
        if (admins.size() >= MAX_ADMIN_COUNT) {
            Toast.makeText(this, R.string.group_admins_limit, Toast.LENGTH_SHORT).show();
            return;
        }
        Intent intent = new Intent(this, SelectMemberActivity.class);
        intent.putExtra(GROUP_ID, groupId);
        intent.putExtra("mode", UserListAdapter.LIST_MODE_SELECT_MEMBER);
        ArrayList<String> disabled = new ArrayList<>(adminIds);
        if (!TextUtils.isEmpty(ownerId) && !disabled.contains(ownerId)) {
            disabled.add(ownerId);
        }
        intent.putStringArrayListExtra(DISABLE_MEMBERS, disabled);
        startActivityForResult(intent, REQ_ADD_ADMINS);
    }

    private void removeAdmin(GroupMemberBean member) {
        if (member == null || TextUtils.isEmpty(member.getUserId())) {
            return;
        }
        String nickname = TextUtils.isEmpty(member.getNickname()) ? member.getUserId() : member.getNickname();
        AppConfirmDialog.builder(this)
                .setTitle(getString(R.string.group_admins_remove_title))
                .setMessage(getString(R.string.group_admins_remove_message, nickname))
                .setNegativeText(getString(R.string.txt_cancel))
                .setPositiveText(getString(R.string.create_group_confirm))
                .setOnPositiveClick(() -> {
                    ArrayList<String> ids = new ArrayList<>();
                    ids.add(member.getUserId());
                    ServiceManager.getUserService().removeGroupAdmins(groupId, ids, new ApiCallback<Void>() {
                        @Override
                        public void onSuccess(Void data) {
                            Toast.makeText(GroupAdminsActivity.this, R.string.group_admins_remove_success, Toast.LENGTH_SHORT).show();
                            loadAdmins();
                        }

                        @Override
                        public void onError(int code, String message) {
                            LogUtils.serverError("group", "removeAdmin", code, message);
                            Toast.makeText(GroupAdminsActivity.this,
                                    R.string.group_admins_remove_failed,
                                    Toast.LENGTH_SHORT).show();
                        }
                    });
                })
                .show();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQ_ADD_ADMINS || resultCode != RESULT_OK || data == null) {
            return;
        }
        ArrayList<String> selected = data.getStringArrayListExtra(SELECTED_MEMBERS);
        if (selected == null || selected.isEmpty()) {
            return;
        }
        int available = MAX_ADMIN_COUNT - adminIds.size();
        if (available <= 0) {
            Toast.makeText(this, R.string.group_admins_limit, Toast.LENGTH_SHORT).show();
            return;
        }
        if (selected.size() > available) {
            selected = new ArrayList<>(selected.subList(0, available));
            Toast.makeText(this, R.string.group_admins_limit_truncated, Toast.LENGTH_SHORT).show();
        }
        ServiceManager.getUserService().addGroupAdmins(groupId, selected, new ApiCallback<Void>() {
            @Override
            public void onSuccess(Void data) {
                Toast.makeText(GroupAdminsActivity.this, R.string.group_admins_add_success, Toast.LENGTH_SHORT).show();
                loadAdmins();
            }

            @Override
            public void onError(int code, String message) {
                LogUtils.serverError("group", "addAdmin", code, message);
                Toast.makeText(GroupAdminsActivity.this,
                        R.string.group_admins_add_failed,
                        Toast.LENGTH_SHORT).show();
            }
        });
    }

    private static class AdminAdapter extends RecyclerView.Adapter<AdminAdapter.AdminViewHolder> {
        interface ActionListener {
            void onDelete(GroupMemberBean member);
        }

        private final List<GroupMemberBean> items = new ArrayList<>();
        private final ActionListener listener;

        AdminAdapter(ActionListener listener) {
            this.listener = listener;
        }

        void submit(List<GroupMemberBean> members) {
            items.clear();
            if (members != null) {
                items.addAll(members);
            }
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public AdminViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_group_admin, parent, false);
            return new AdminViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull AdminViewHolder holder, int position) {
            GroupMemberBean item = items.get(position);
            String name = TextUtils.isEmpty(item.getNickname()) ? item.getUserId() : item.getNickname();
            AvatarUtils.loadAvatar(holder.avatarView, item.getAvatar(), name, item.getUserId());
            holder.nameView.setText(name);
            holder.dividerView.setVisibility(position == items.size() - 1 ? View.GONE : View.VISIBLE);
            holder.removeView.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onDelete(item);
                }
            });
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        static class AdminViewHolder extends RecyclerView.ViewHolder {
            final ImageView avatarView;
            final TextView nameView;
            final TextView removeView;
            final View dividerView;

            AdminViewHolder(@NonNull View itemView) {
                super(itemView);
                avatarView = itemView.findViewById(R.id.iv_avatar);
                nameView = itemView.findViewById(R.id.tv_name);
                removeView = itemView.findViewById(R.id.tv_remove);
                dividerView = itemView.findViewById(R.id.v_divider);
            }
        }
    }
}

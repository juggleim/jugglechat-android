package com.juggle.im.android.app;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.juggle.im.JIM;
import com.juggle.im.android.R;
import com.juggle.im.android.chat.ConversationActivity;
import com.juggle.im.android.component.AbsAppActivity;
import com.juggle.im.android.server.beans.GroupBean;
import com.juggle.im.android.server.beans.GroupListData;
import com.juggle.im.android.server.http.ApiCallback;
import com.juggle.im.android.server.http.ServiceManager;
import com.juggle.im.android.utils.AvatarUtils;
import com.juggle.im.model.Conversation;

import java.text.Collator;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class MyGroupsActivity extends AbsAppActivity {
    private final Collator nameCollator = Collator.getInstance(Locale.CHINA);

    private RecyclerView recyclerView;
    private TextView emptyView;
    private ProgressBar progressBar;
    private GroupAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_my_groups);

        recyclerView = findViewById(R.id.rv_groups);
        emptyView = findViewById(R.id.tv_empty);
        progressBar = findViewById(R.id.progress_bar);
        findViewById(R.id.btn_back).setOnClickListener(v -> finish());

        adapter = new GroupAdapter(this::openGroupConversation);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(adapter);

        loadGroups();
    }

    private void loadGroups() {
        progressBar.setVisibility(View.VISIBLE);
        ServiceManager.getUserService().myGroups(new ApiCallback<GroupListData>() {
            @Override
            public void onSuccess(GroupListData data) {
                progressBar.setVisibility(View.GONE);
                List<GroupBean> items = data == null ? null : data.getItems();
                List<GroupBean> groups = new ArrayList<>();
                if (items != null) {
                    groups.addAll(items);
                }
                groups.sort((left, right) -> nameCollator.compare(
                        safeGroupName(left), safeGroupName(right)));
                adapter.submit(groups);
                boolean isEmpty = groups.isEmpty();
                emptyView.setVisibility(isEmpty ? View.VISIBLE : View.GONE);
                recyclerView.setVisibility(isEmpty ? View.GONE : View.VISIBLE);
            }

            @Override
            public void onError(int code, String message) {
                progressBar.setVisibility(View.GONE);
                emptyView.setVisibility(View.VISIBLE);
                recyclerView.setVisibility(View.GONE);
                Toast.makeText(MyGroupsActivity.this, "加载群组失败：" + message, Toast.LENGTH_SHORT).show();
            }
        });
    }

    @NonNull
    private String safeGroupName(@Nullable GroupBean bean) {
        if (bean == null || TextUtils.isEmpty(bean.getGroup_name())) {
            return "";
        }
        return bean.getGroup_name();
    }

    private void openGroupConversation(@NonNull GroupBean groupBean) {
        String groupId = groupBean.getGroup_id();
        if (TextUtils.isEmpty(groupId)) {
            return;
        }
        Conversation conversation = new Conversation(Conversation.ConversationType.GROUP, groupId);
        JIM.getInstance().getConversationManager().clearUnreadCount(conversation, null);
        startActivity(ConversationActivity.intentFor(
                this,
                groupId,
                true,
                TextUtils.isEmpty(groupBean.getGroup_name()) ? groupId : groupBean.getGroup_name()));
    }

    private static class GroupAdapter extends RecyclerView.Adapter<GroupAdapter.GroupViewHolder> {
        private final List<GroupBean> items = new ArrayList<>();
        private final OnGroupClickListener onGroupClickListener;

        GroupAdapter(OnGroupClickListener onGroupClickListener) {
            this.onGroupClickListener = onGroupClickListener;
        }

        void submit(List<GroupBean> groups) {
            items.clear();
            if (groups != null) {
                items.addAll(groups);
            }
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public GroupViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_my_group, parent, false);
            return new GroupViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull GroupViewHolder holder, int position) {
            GroupBean item = items.get(position);
            String groupName = TextUtils.isEmpty(item.getGroup_name()) ? item.getGroup_id() : item.getGroup_name();
            holder.tvName.setText(groupName);
            AvatarUtils.loadAvatar(holder.ivAvatar, item.getGroup_portrait(), groupName);
            holder.vDivider.setVisibility(position == items.size() - 1 ? View.GONE : View.VISIBLE);
            holder.itemView.setOnClickListener(v -> {
                if (onGroupClickListener != null) {
                    onGroupClickListener.onGroupClick(item);
                }
            });
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        interface OnGroupClickListener {
            void onGroupClick(GroupBean groupBean);
        }

        static class GroupViewHolder extends RecyclerView.ViewHolder {
            final ImageView ivAvatar;
            final TextView tvName;
            final View vDivider;

            GroupViewHolder(@NonNull View itemView) {
                super(itemView);
                ivAvatar = itemView.findViewById(R.id.iv_avatar);
                tvName = itemView.findViewById(R.id.tv_name);
                vDivider = itemView.findViewById(R.id.v_divider);
            }
        }
    }
}

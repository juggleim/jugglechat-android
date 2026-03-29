package com.juggle.im.android.app;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.juggle.im.JIM;
import com.juggle.im.android.R;
import com.juggle.im.android.component.AbsAppActivity;
import com.juggle.im.android.server.beans.FriendApplicationBean;
import com.juggle.im.android.server.beans.FriendApplicationsData;
import com.juggle.im.android.server.http.ApiCallback;
import com.juggle.im.android.server.http.ServiceManager;
import com.juggle.im.android.utils.AvatarUtils;
import com.juggle.im.model.Conversation;

import java.util.ArrayList;
import java.util.List;

public class FriendApplicationsActivity extends AbsAppActivity {
    private static final String FRIEND_APPLY = "friend_apply";
    private static final int STATUS_APPLYING = 0;
    private static final int STATUS_AGREED = 1;
    private static final int STATUS_REJECTED = 2;
    private static final int STATUS_EXPIRED = 3;

    private RecyclerView rvApplications;
    private ProgressBar progressBar;
    private TextView emptyView;
    private ApplicationsAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_friend_applications);

        rvApplications = findViewById(R.id.rv_applications);
        progressBar = findViewById(R.id.progress_bar);
        emptyView = findViewById(R.id.tv_empty);
        View btnBack = findViewById(R.id.btn_back);

        if (btnBack != null) {
            btnBack.setOnClickListener(v -> finish());
        }

        adapter = new ApplicationsAdapter(new ArrayList<>());
        rvApplications.setLayoutManager(new LinearLayoutManager(this));
        rvApplications.setAdapter(adapter);

        Conversation conversation = new Conversation(Conversation.ConversationType.SYSTEM, FRIEND_APPLY);
        JIM.getInstance().getConversationManager().clearUnreadCount(conversation, null);

        loadApplications();
    }

    private void loadApplications() {
        progressBar.setVisibility(View.VISIBLE);
        ServiceManager.getUserService().getFriendApplications(0, 50, new ApiCallback<FriendApplicationsData>() {
            @Override
            public void onSuccess(FriendApplicationsData data) {
                progressBar.setVisibility(View.GONE);
                List<FriendApplicationBean> items = data == null ? null : data.getItems();
                if (items == null) {
                    items = new ArrayList<>();
                }
                items.sort((left, right) -> Long.compare(right.getApplyTime(), left.getApplyTime()));
                adapter.setItems(items);
                boolean isEmpty = items.isEmpty();
                emptyView.setVisibility(isEmpty ? View.VISIBLE : View.GONE);
                rvApplications.setVisibility(isEmpty ? View.GONE : View.VISIBLE);
            }

            @Override
            public void onError(int code, String message) {
                progressBar.setVisibility(View.GONE);
                emptyView.setVisibility(View.VISIBLE);
                rvApplications.setVisibility(View.GONE);
                Toast.makeText(FriendApplicationsActivity.this, "加载新朋友失败：" + message, Toast.LENGTH_SHORT)
                        .show();
            }
        });
    }

    class ApplicationsAdapter extends RecyclerView.Adapter<ApplicationsAdapter.ViewHolder> {
        private List<FriendApplicationBean> items;

        ApplicationsAdapter(List<FriendApplicationBean> items) {
            this.items = items;
        }

        void setItems(List<FriendApplicationBean> newItems) {
            this.items = newItems;
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_friend_application, parent, false);
            return new ViewHolder(v);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            FriendApplicationBean app = items.get(position);
            boolean isSponsor = app.isSponsor();
            int status = app.getStatus();

            // 设置头像和昵称
            if (app.getUserInfo() != null) {
                holder.tvNickname.setText(app.getUserInfo().getNickname());
                AvatarUtils.loadAvatar(holder.ivAvatar, app.getUserInfo().getAvatar(), app.getUserInfo().getNickname());
            }

            // 设置描述文字
            if (isSponsor) {
                // 当前用户发起的申请
                holder.tvDescription.setText("申请添加对方为好友");
            } else {
                // 对方发起的申请
                holder.tvDescription.setText("申请添加你为好友");
            }

            // 根据是否发起者和状态设置右侧显示
            if (!isSponsor && status == STATUS_APPLYING) {
                // 对方发起，且申请中：显示接受和拒绝按钮
                holder.buttonsContainer.setVisibility(View.VISIBLE);
                holder.tvStatus.setVisibility(View.GONE);

                holder.btnAccept.setOnClickListener(v -> {
                    acceptApplication(app, holder.getAdapterPosition());
                });

                holder.btnRefuse.setOnClickListener(v -> {
                    refuseApplication(app, holder.getAdapterPosition());
                });
            } else {
                // 其他情况：显示状态文字
                holder.buttonsContainer.setVisibility(View.GONE);
                holder.tvStatus.setVisibility(View.VISIBLE);

                String statusText;
                if (isSponsor) {
                    // 当前用户发起的申请
                    switch (status) {
                        case STATUS_APPLYING:
                            statusText = "等待验证";
                            break;
                        case STATUS_AGREED:
                            statusText = "已添加";
                            break;
                        case STATUS_REJECTED:
                            statusText = "已被拒绝";
                            break;
                        case STATUS_EXPIRED:
                            statusText = "已过期";
                            break;
                        default:
                            statusText = "等待验证";
                            break;
                    }
                } else {
                    // 对方发起的申请
                    switch (status) {
                        case STATUS_AGREED:
                            statusText = "已添加";
                            break;
                        case STATUS_REJECTED:
                            statusText = "已拒绝";
                            break;
                        case STATUS_EXPIRED:
                            statusText = "已过期";
                            break;
                        default:
                            statusText = "";
                            break;
                    }
                }
                holder.tvStatus.setText(statusText);
            }
        }

        private void acceptApplication(FriendApplicationBean app, int position) {
            String targetUserId = app.getUserInfo() != null ? app.getUserInfo().getUser_id() : null;
            if (targetUserId == null) return;

            ServiceManager.getUserService().acceptFriendApplication(targetUserId, new ApiCallback<Void>() {
                @Override
                public void onSuccess(Void data) {
                    // 更新状态
                    app.setStatus(STATUS_AGREED);
                    notifyItemChanged(position);
                    Toast.makeText(FriendApplicationsActivity.this, "已添加好友", Toast.LENGTH_SHORT).show();
                }

                @Override
                public void onError(int code, String message) {
                    Toast.makeText(FriendApplicationsActivity.this, "添加失败：" + message, Toast.LENGTH_SHORT).show();
                }
            });
        }

        private void refuseApplication(FriendApplicationBean app, int position) {
            String targetUserId = app.getUserInfo() != null ? app.getUserInfo().getUser_id() : null;
            if (targetUserId == null) return;

            ServiceManager.getUserService().refuseFriendApplication(targetUserId, new ApiCallback<Void>() {
                @Override
                public void onSuccess(Void data) {
                    // 更新状态
                    app.setStatus(STATUS_REJECTED);
                    notifyItemChanged(position);
                    Toast.makeText(FriendApplicationsActivity.this, "已拒绝", Toast.LENGTH_SHORT).show();
                }

                @Override
                public void onError(int code, String message) {
                    Toast.makeText(FriendApplicationsActivity.this, "拒绝失败：" + message, Toast.LENGTH_SHORT).show();
                }
            });
        }

        @Override
        public int getItemCount() {
            return items == null ? 0 : items.size();
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            ImageView ivAvatar;
            TextView tvNickname;
            TextView tvDescription;
            View buttonsContainer;
            TextView btnAccept;
            TextView btnRefuse;
            TextView tvStatus;

            ViewHolder(@NonNull View v) {
                super(v);
                ivAvatar = v.findViewById(R.id.iv_avatar);
                tvNickname = v.findViewById(R.id.tv_nickname);
                tvDescription = v.findViewById(R.id.tv_description);
                buttonsContainer = v.findViewById(R.id.buttons_container);
                btnAccept = v.findViewById(R.id.btn_accept);
                btnRefuse = v.findViewById(R.id.btn_refuse);
                tvStatus = v.findViewById(R.id.tv_status);
            }
        }
    }
}
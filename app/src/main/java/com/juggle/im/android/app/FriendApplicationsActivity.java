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
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import com.juggle.im.android.utils.LogUtils;

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
                LogUtils.serverError("contact", "loadFriendApplications", code, message);
                Toast.makeText(FriendApplicationsActivity.this, R.string.friend_apply_load_failed, Toast.LENGTH_SHORT)
                        .show();
            }
        });
    }

    class ApplicationsAdapter extends RecyclerView.Adapter<ApplicationsAdapter.ViewHolder> {
        private List<FriendApplicationBean> items;
        /** 正在处理中的申请人 ID，用于给对应条目上处理态并防重复点击 */
        private final Set<String> pendingSponsorIds = new HashSet<>();

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
            String sponsorId = app.getUserInfo() == null ? null : app.getUserInfo().getUser_id();
            boolean pending = sponsorId != null && pendingSponsorIds.contains(sponsorId);

            // 设置头像和昵称
            if (app.getUserInfo() != null) {
                holder.tvNickname.setText(app.getUserInfo().getNickname());
                AvatarUtils.loadAvatar(holder.ivAvatar, app.getUserInfo().getAvatar(), app.getUserInfo().getNickname());
            }

            // 设置描述文字
            if (isSponsor) {
                // 当前用户发起的申请
                holder.tvDescription.setText(R.string.friend_apply_desc_outgoing);
            } else {
                // 对方发起的申请
                holder.tvDescription.setText(R.string.friend_apply_desc_incoming);
            }

            // 根据是否发起者和状态设置右侧显示
            if (!isSponsor && status == STATUS_APPLYING && !pending) {
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

                if (pending) {
                    // 请求在途：按钮收起、右侧改为处理中，天然屏蔽重复点击
                    holder.tvStatus.setText(R.string.common_processing);
                    return;
                }

                String statusText;
                if (isSponsor) {
                    // 当前用户发起的申请
                    switch (status) {
                        case STATUS_APPLYING:
                            statusText = getString(R.string.friend_apply_status_pending);
                            break;
                        case STATUS_AGREED:
                            statusText = getString(R.string.friend_apply_status_added);
                            break;
                        case STATUS_REJECTED:
                            statusText = getString(R.string.friend_apply_status_rejected_by_peer);
                            break;
                        case STATUS_EXPIRED:
                            statusText = getString(R.string.friend_apply_status_expired);
                            break;
                        default:
                            statusText = getString(R.string.friend_apply_status_pending);
                            break;
                    }
                } else {
                    // 对方发起的申请
                    switch (status) {
                        case STATUS_AGREED:
                            statusText = getString(R.string.friend_apply_status_added);
                            break;
                        case STATUS_REJECTED:
                            statusText = getString(R.string.friend_apply_status_rejected);
                            break;
                        case STATUS_EXPIRED:
                            statusText = getString(R.string.friend_apply_status_expired);
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
            String sponsorId = app.getUserInfo() != null ? app.getUserInfo().getUser_id() : null;
            if (sponsorId == null) return;
            if (!beginPending(sponsorId, position)) return;

            ServiceManager.getUserService().acceptFriendApplication(sponsorId, new ApiCallback<Void>() {
                @Override
                public void onSuccess(Void data) {
                    // 更新状态
                    endPending(sponsorId);
                    app.setStatus(STATUS_AGREED);
                    notifyItemChanged(position);
                    Toast.makeText(FriendApplicationsActivity.this, R.string.friend_apply_accept_success, Toast.LENGTH_SHORT).show();
                }

                @Override
                public void onError(int code, String message) {
                    endPending(sponsorId);
                    notifyItemChanged(position);
                    LogUtils.serverError("contact", "acceptFriendApplication", code, message);
                    Toast.makeText(FriendApplicationsActivity.this, R.string.friend_apply_accept_failed, Toast.LENGTH_SHORT).show();
                }
            });
        }

        private void refuseApplication(FriendApplicationBean app, int position) {
            String sponsorId = app.getUserInfo() != null ? app.getUserInfo().getUser_id() : null;
            if (sponsorId == null) return;
            if (!beginPending(sponsorId, position)) return;

            ServiceManager.getUserService().refuseFriendApplication(sponsorId, new ApiCallback<Void>() {
                @Override
                public void onSuccess(Void data) {
                    // 更新状态
                    endPending(sponsorId);
                    app.setStatus(STATUS_REJECTED);
                    notifyItemChanged(position);
                    Toast.makeText(FriendApplicationsActivity.this, R.string.friend_apply_reject_success, Toast.LENGTH_SHORT).show();
                }

                @Override
                public void onError(int code, String message) {
                    endPending(sponsorId);
                    notifyItemChanged(position);
                    LogUtils.serverError("contact", "rejectFriendApplication", code, message);
                    Toast.makeText(FriendApplicationsActivity.this, R.string.friend_apply_reject_failed, Toast.LENGTH_SHORT).show();
                }
            });
        }

        /**
         * 标记某条申请进入处理中。
         *
         * @param sponsorId 申请人 ID
         * @param position  条目位置
         * @return false 表示该条已有请求在途，调用方应直接返回
         */
        private boolean beginPending(String sponsorId, int position) {
            if (!pendingSponsorIds.add(sponsorId)) {
                return false;
            }
            notifyItemChanged(position);
            return true;
        }

        /**
         * 清除某条申请的处理中标记。
         *
         * @param sponsorId 申请人 ID
         */
        private void endPending(String sponsorId) {
            pendingSponsorIds.remove(sponsorId);
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
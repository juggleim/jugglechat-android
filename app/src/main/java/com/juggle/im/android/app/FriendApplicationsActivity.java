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
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.juggle.im.JIM;
import com.juggle.im.android.R;
import com.juggle.im.android.server.beans.FriendApplicationBean;
import com.juggle.im.android.server.beans.FriendApplicationsData;
import com.juggle.im.android.server.http.ApiCallback;
import com.juggle.im.android.server.http.ServiceManager;
import com.juggle.im.android.utils.AvatarUtils;
import com.juggle.im.model.Conversation;

import java.util.ArrayList;
import java.util.List;

public class FriendApplicationsActivity extends AppCompatActivity {
    private static final String FRIEND_APPLY = "friend_apply";
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

    static class ApplicationsAdapter extends RecyclerView.Adapter<ApplicationsAdapter.ViewHolder> {
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

            // Set nickname
            if (app.getUserInfo() != null) {
                holder.tvNickname.setText(app.getUserInfo().getNickname());
                AvatarUtils.loadAvatar(holder.ivAvatar, app.getUserInfo().getAvatar(), app.getUserInfo().getNickname());
            }

            // Set description based on is_sponsor
            if (app.isSponsor()) {
                holder.tvDescription.setText("你已申请");
            } else {
                holder.tvDescription.setText("申请添加你为好友");
            }

            // Set status text based on status code
            // 0: Applying, 1: Agreed, 2: Rejected, 3: Expired
            String statusText;
            switch (app.getStatus()) {
                case 1:
                    statusText = "已添加";
                    break;
                case 2:
                    statusText = "已拒绝";
                    break;
                case 3:
                    statusText = "已过期";
                    break;
                case 0:
                default:
                    statusText = "申请中";
                    break;
            }
            holder.tvStatus.setText(statusText);
        }

        @Override
        public int getItemCount() {
            return items == null ? 0 : items.size();
        }

        static class ViewHolder extends RecyclerView.ViewHolder {
            ImageView ivAvatar;
            TextView tvNickname;
            TextView tvDescription;
            TextView tvStatus;

            ViewHolder(@NonNull View v) {
                super(v);
                ivAvatar = v.findViewById(R.id.iv_avatar);
                tvNickname = v.findViewById(R.id.tv_nickname);
                tvDescription = v.findViewById(R.id.tv_description);
                tvStatus = v.findViewById(R.id.tv_status);
            }
        }
    }
}

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
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.juggle.im.android.R;
import com.juggle.im.android.component.AbsAppActivity;
import com.juggle.im.android.server.beans.BlockUsersData;
import com.juggle.im.android.server.beans.FriendBean;
import com.juggle.im.android.server.http.ApiCallback;
import com.juggle.im.android.server.http.ServiceManager;
import com.juggle.im.android.utils.AvatarUtils;

import java.text.Collator;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class BlockUsersActivity extends AbsAppActivity {
    private static final int PAGE_SIZE = 50;

    private final Collator nameCollator = Collator.getInstance(Locale.CHINA);
    private RecyclerView recyclerView;
    private TextView emptyView;
    private ProgressBar progressBar;
    private BlockUserAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_block_users);

        recyclerView = findViewById(R.id.rv_block_users);
        emptyView = findViewById(R.id.tv_empty);
        progressBar = findViewById(R.id.progress_bar);
        findViewById(R.id.btn_back).setOnClickListener(v -> finish());

        adapter = new BlockUserAdapter();
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(adapter);

        loadBlockUsers();
    }

    private void loadBlockUsers() {
        progressBar.setVisibility(View.VISIBLE);
        fetchBlockUsers("", new ArrayList<>());
    }

    private void fetchBlockUsers(@NonNull String offset, @NonNull List<FriendBean> container) {
        ServiceManager.getUserService().getBlockUsers(PAGE_SIZE, offset, new ApiCallback<BlockUsersData>() {
            @Override
            public void onSuccess(BlockUsersData data) {
                List<FriendBean> items = data == null ? null : data.getItems();
                if (items != null) {
                    container.addAll(items);
                }

                String nextOffset = data == null ? "" : data.getOffset();
                boolean hasMore = !TextUtils.isEmpty(nextOffset)
                        && !TextUtils.equals(nextOffset, offset)
                        && items != null
                        && !items.isEmpty();
                if (hasMore) {
                    fetchBlockUsers(nextOffset, container);
                    return;
                }

                progressBar.setVisibility(View.GONE);
                container.sort((left, right) -> nameCollator.compare(
                        safeName(left), safeName(right)));
                adapter.submit(container);
                boolean isEmpty = container.isEmpty();
                emptyView.setVisibility(isEmpty ? View.VISIBLE : View.GONE);
                recyclerView.setVisibility(isEmpty ? View.GONE : View.VISIBLE);
            }

            @Override
            public void onError(int code, String message) {
                progressBar.setVisibility(View.GONE);
                emptyView.setVisibility(View.VISIBLE);
                recyclerView.setVisibility(View.GONE);
                Toast.makeText(BlockUsersActivity.this, "加载黑名单失败：" + message, Toast.LENGTH_SHORT).show();
            }
        });
    }

    @NonNull
    private String safeName(FriendBean bean) {
        if (bean == null) {
            return "";
        }
        if (!TextUtils.isEmpty(bean.getNickname())) {
            return bean.getNickname();
        }
        return bean.getUser_id() == null ? "" : bean.getUser_id();
    }

    private static class BlockUserAdapter extends RecyclerView.Adapter<BlockUserAdapter.BlockUserViewHolder> {
        private final List<FriendBean> items = new ArrayList<>();

        void submit(List<FriendBean> list) {
            items.clear();
            if (list != null) {
                items.addAll(list);
            }
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public BlockUserViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_block_user, parent, false);
            return new BlockUserViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull BlockUserViewHolder holder, int position) {
            FriendBean item = items.get(position);
            String name = TextUtils.isEmpty(item.getNickname()) ? item.getUser_id() : item.getNickname();
            holder.tvName.setText(name);
            AvatarUtils.loadAvatar(holder.ivAvatar, item.getAvatar(), name);
            holder.vDivider.setVisibility(position == items.size() - 1 ? View.GONE : View.VISIBLE);
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        static class BlockUserViewHolder extends RecyclerView.ViewHolder {
            final ImageView ivAvatar;
            final TextView tvName;
            final View vDivider;

            BlockUserViewHolder(@NonNull View itemView) {
                super(itemView);
                ivAvatar = itemView.findViewById(R.id.iv_avatar);
                tvName = itemView.findViewById(R.id.tv_name);
                vDivider = itemView.findViewById(R.id.v_divider);
            }
        }
    }
}

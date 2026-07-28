package com.juggle.im.android.app;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.juggle.im.JIM;
import com.juggle.im.android.component.AbsAppActivity;
import com.juggle.im.interfaces.IMessageManager;
import com.juggle.im.android.R;
import com.juggle.im.android.chat.utils.MessageUtils;
import com.juggle.im.android.widget.JuggleCheckBox;
import com.juggle.im.model.Conversation;
import com.juggle.im.model.FavoriteMessage;
import com.juggle.im.model.GetFavoriteMessageOption;
import com.juggle.im.model.GroupInfo;
import com.juggle.im.model.Message;
import com.juggle.im.model.UserInfo;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * 我的收藏页面
 */
public class FavoritesActivity extends AbsAppActivity {
    private static final int PAGE_SIZE = 20;

    private final List<FavoriteRow> rows = new ArrayList<>();
    private final FavoriteAdapter adapter = new FavoriteAdapter();

    private RecyclerView recyclerView;
    private TextView emptyView;
    private TextView actionView;
    private View multiActionLayout;

    private String offset = "";
    private boolean isLoading;
    private boolean isFinished;
    private boolean multiMode;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_favorites);

        recyclerView = findViewById(R.id.rv_favorites);
        emptyView = findViewById(R.id.tv_empty);
        actionView = findViewById(R.id.tv_action);
        multiActionLayout = findViewById(R.id.layout_multi_actions);

        findViewById(R.id.iv_back).setOnClickListener(v -> finish());
        actionView.setOnClickListener(v -> toggleMultiMode());
        findViewById(R.id.btn_delete).setOnClickListener(v -> deleteSelected());

        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(adapter);
        recyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                if (dy <= 0 || isLoading || isFinished) {
                    return;
                }
                LinearLayoutManager manager = (LinearLayoutManager) recyclerView.getLayoutManager();
                if (manager == null) {
                    return;
                }
                int total = manager.getItemCount();
                int lastVisible = manager.findLastVisibleItemPosition();
                if (lastVisible >= total - 4) {
                    fetchFavorites(false);
                }
            }
        });

        fetchFavorites(true);
    }

    private void toggleMultiMode() {
        multiMode = !multiMode;
        actionView.setText(multiMode ? R.string.txt_cancel : R.string.favorites_multi_select);
        multiActionLayout.setVisibility(multiMode ? View.VISIBLE : View.GONE);
        if (!multiMode) {
            for (FavoriteRow row : rows) {
                row.selected = false;
            }
        }
        adapter.notifyDataSetChanged();
    }

    private void fetchFavorites(boolean reset) {
        if (isLoading) {
            return;
        }
        isLoading = true;
        if (reset) {
            offset = "";
            isFinished = false;
        }

        GetFavoriteMessageOption option = new GetFavoriteMessageOption();
        option.setCount(PAGE_SIZE);
        option.setOffset(offset);

        JIM.getInstance().getMessageManager().getFavorite(option, new IMessageManager.IGetFavoriteMessageCallback() {
            @Override
            public void onSuccess(List<FavoriteMessage> list, String nextOffset) {
                runOnUiThread(() -> {
                    isLoading = false;
                    if (reset) {
                        rows.clear();
                    }
                    if (list != null) {
                        for (FavoriteMessage item : list) {
                            FavoriteRow row = new FavoriteRow();
                            row.favorite = item;
                            row.conversationName = resolveConversationName(item.getMessage());
                            rows.add(row);
                        }
                    }

                    offset = nextOffset == null ? "" : nextOffset;
                    isFinished = list == null || list.size() < PAGE_SIZE || TextUtils.isEmpty(offset);
                    adapter.notifyDataSetChanged();
                    updateEmptyState();
                });
            }

            @Override
            public void onError(int code) {
                runOnUiThread(() -> {
                    isLoading = false;
                    updateEmptyState();
                    Toast.makeText(FavoritesActivity.this, R.string.favorites_load_failed, Toast.LENGTH_SHORT).show();
                });
            }
        });
    }

    private void deleteSelected() {
        List<String> messageIds = new ArrayList<>();
        for (FavoriteRow row : rows) {
            if (row.selected && row.favorite != null && row.favorite.getMessage() != null) {
                String messageId = row.favorite.getMessage().getMessageId();
                if (!TextUtils.isEmpty(messageId)) {
                    messageIds.add(messageId);
                }
            }
        }
        if (messageIds.isEmpty()) {
            Toast.makeText(this, R.string.favorites_select_required, Toast.LENGTH_SHORT).show();
            return;
        }

        JIM.getInstance().getMessageManager().removeFavorite(messageIds, new IMessageManager.ISimpleCallback() {
            @Override
            public void onSuccess() {
                runOnUiThread(() -> {
                    List<FavoriteRow> keep = new ArrayList<>();
                    for (FavoriteRow row : rows) {
                        if (!row.selected) {
                            keep.add(row);
                        }
                    }
                    rows.clear();
                    rows.addAll(keep);
                    adapter.notifyDataSetChanged();
                    updateEmptyState();
                    Toast.makeText(FavoritesActivity.this, R.string.favorites_delete_success, Toast.LENGTH_SHORT).show();
                });
            }

            @Override
            public void onError(int code) {
                runOnUiThread(() -> Toast.makeText(FavoritesActivity.this, R.string.favorites_delete_failed, Toast.LENGTH_SHORT).show());
            }
        });
    }

    private void updateEmptyState() {
        emptyView.setVisibility(rows.isEmpty() ? View.VISIBLE : View.GONE);
    }

    private String resolveConversationName(Message message) {
        if (message == null || message.getConversation() == null) {
            return "";
        }
        Conversation conversation = message.getConversation();
        String conversationId = conversation.getConversationId();
        if (conversation.getConversationType() == Conversation.ConversationType.GROUP) {
            GroupInfo groupInfo = JIM.getInstance().getUserInfoManager().getGroupInfo(conversationId);
            if (groupInfo != null && !TextUtils.isEmpty(groupInfo.getGroupName())) {
                return groupInfo.getGroupName();
            }
            return conversationId;
        }
        UserInfo userInfo = JIM.getInstance().getUserInfoManager().getUserInfo(conversationId);
        if (userInfo != null && !TextUtils.isEmpty(userInfo.getUserName())) {
            return userInfo.getUserName();
        }
        return conversationId;
    }

    private String formatTime(long timestamp) {
        return new SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(new Date(timestamp));
    }

    private final class FavoriteAdapter extends RecyclerView.Adapter<FavoriteAdapter.Holder> {
        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_favorite_message, parent, false);
            return new Holder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull Holder holder, int position) {
            FavoriteRow row = rows.get(position);
            Message message = row.favorite == null ? null : row.favorite.getMessage();

            holder.summary.setText(message == null ? "" : MessageUtils.getMessageSummary(FavoritesActivity.this, message));
            holder.conversation.setText(TextUtils.isEmpty(row.conversationName)
                    ? getString(R.string.favorites_unknown_conversation) : row.conversationName);
            holder.time.setText(row.favorite == null ? "" : formatTime(row.favorite.getCreatedTime()));

            holder.checkBox.setVisibility(multiMode ? View.VISIBLE : View.GONE);
            holder.checkBox.setChecked(row.selected);

            holder.itemView.setOnClickListener(v -> {
                if (!multiMode) {
                    // tips: 非多选模式下点击 item 跳转收藏详情预览页
                    Message msg = row.favorite == null ? null : row.favorite.getMessage();
                    if (msg != null && !TextUtils.isEmpty(msg.getMessageId())) {
                        FavoriteDetailActivity.start(FavoritesActivity.this, msg.getMessageId(), row.conversationName);
                    }
                    return;
                }
                row.selected = !row.selected;
                notifyItemChanged(holder.getBindingAdapterPosition());
            });

            holder.itemView.setOnLongClickListener(v -> {
                if (multiMode) {
                    return false;
                }
                multiMode = true;
                actionView.setText(R.string.txt_cancel);
                multiActionLayout.setVisibility(View.VISIBLE);
                row.selected = true;
                notifyDataSetChanged();
                return true;
            });
        }

        @Override
        public int getItemCount() {
            return rows.size();
        }

        private final class Holder extends RecyclerView.ViewHolder {
            private final JuggleCheckBox checkBox;
            private final TextView summary;
            private final TextView conversation;
            private final TextView time;

            private Holder(@NonNull View itemView) {
                super(itemView);
                checkBox = itemView.findViewById(R.id.checkbox);
                summary = itemView.findViewById(R.id.tv_summary);
                conversation = itemView.findViewById(R.id.tv_conversation);
                time = itemView.findViewById(R.id.tv_time);
            }
        }
    }

    private static final class FavoriteRow {
        private FavoriteMessage favorite;
        private boolean selected;
        private String conversationName;
    }
}

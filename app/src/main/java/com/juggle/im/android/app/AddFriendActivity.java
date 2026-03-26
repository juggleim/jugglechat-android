package com.juggle.im.android.app;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import com.juggle.im.android.component.AbsAppActivity;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.juggle.im.android.R;
import com.juggle.im.android.server.beans.FriendApplicationBean;
import com.juggle.im.android.server.beans.FriendBean;
import com.juggle.im.android.server.beans.FriendsListData;
import com.juggle.im.android.server.http.ApiCallback;
import com.juggle.im.android.server.http.ServiceManager;
import com.juggle.im.android.utils.AvatarUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class AddFriendActivity extends AbsAppActivity {

    private static final long SEARCH_DEBOUNCE_MS = 280L;

    private final Handler searchHandler = new Handler(Looper.getMainLooper());
    private Runnable pendingSearchTask;

    private EditText searchInput;
    private ImageView clearSearchView;
    private RecyclerView recyclerView;
    private ProgressBar progressBar;
    private View emptyLayout;
    private SearchAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_add_friend);

        setupWindowStyle();
        initViews();
        bindEvents();
    }

    private void setupWindowStyle() {
        Window window = getWindow();
        window.setStatusBarColor(getColor(R.color.white));
        window.setNavigationBarColor(getColor(R.color.white));
        WindowInsetsControllerCompat controller = new WindowInsetsControllerCompat(window, window.getDecorView());
        controller.setAppearanceLightStatusBars(true);
    }

    private void initViews() {
        searchInput = findViewById(R.id.edt_search);
        clearSearchView = findViewById(R.id.iv_clear_search);
        recyclerView = findViewById(R.id.rv_results);
        progressBar = findViewById(R.id.progress_bar);
        emptyLayout = findViewById(R.id.layout_empty);

        findViewById(R.id.btn_back).setOnClickListener(v -> finish());

        adapter = new SearchAdapter();
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(adapter);

        adapter.setOnAddClickListener(this::applyFriend);
        updateClearButton("");
        updateEmptyState(false);
    }

    private void bindEvents() {
        clearSearchView.setOnClickListener(v -> {
            if (pendingSearchTask != null) {
                searchHandler.removeCallbacks(pendingSearchTask);
                pendingSearchTask = null;
            }
            searchInput.setText("");
            searchInput.requestFocus();
        });

        searchInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                String keyword = s == null ? "" : s.toString();
                updateClearButton(keyword);
                scheduleSearch(keyword);
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });

        searchInput.setOnEditorActionListener((TextView v, int actionId, KeyEvent event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH || actionId == EditorInfo.IME_ACTION_DONE) {
                String keyword = normalizeKeyword(v.getText() == null ? "" : v.getText().toString());
                if (!keyword.isEmpty()) {
                    if (pendingSearchTask != null) {
                        searchHandler.removeCallbacks(pendingSearchTask);
                    }
                    doSearch(keyword);
                    hideKeyboard(v);
                }
                return true;
            }
            return false;
        });
    }

    /**
     * 输入阶段做轻量防抖，避免每个字符都发请求。
     */
    private void scheduleSearch(String rawKeyword) {
        String keyword = normalizeKeyword(rawKeyword);
        if (pendingSearchTask != null) {
            searchHandler.removeCallbacks(pendingSearchTask);
            pendingSearchTask = null;
        }

        if (keyword.isEmpty()) {
            progressBar.setVisibility(View.GONE);
            adapter.setItems(new ArrayList<>());
            updateEmptyState(false);
            return;
        }

        updateEmptyState(false);
        pendingSearchTask = () -> doSearch(keyword);
        searchHandler.postDelayed(pendingSearchTask, SEARCH_DEBOUNCE_MS);
    }

    private String normalizeKeyword(String text) {
        return text == null ? "" : text.trim();
    }

    private void doSearch(String keyword) {
        progressBar.setVisibility(View.VISIBLE);
        updateEmptyState(false);
        ServiceManager.getUserService().searchUsers(keyword, new ApiCallback<FriendsListData>() {
            @Override
            public void onSuccess(FriendsListData data) {
                // 忽略输入已变化的过期结果，避免列表闪回。
                String latest = normalizeKeyword(searchInput.getText() == null ? "" : searchInput.getText().toString());
                if (!TextUtils.equals(latest, keyword)) {
                    return;
                }

                progressBar.setVisibility(View.GONE);
                List<FriendBean> items = data == null || data.getItems() == null
                        ? new ArrayList<>()
                        : data.getItems();
                adapter.setItems(items);
                updateEmptyState(items.isEmpty());
            }

            @Override
            public void onError(int code, String message) {
                String latest = normalizeKeyword(searchInput.getText() == null ? "" : searchInput.getText().toString());
                if (!TextUtils.equals(latest, keyword)) {
                    return;
                }

                progressBar.setVisibility(View.GONE);
                updateEmptyState(false);
                Toast.makeText(AddFriendActivity.this,
                        getString(R.string.add_friend_search_failed, String.valueOf(message)),
                        Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void updateClearButton(String keyword) {
        if (clearSearchView == null) {
            return;
        }
        clearSearchView.setVisibility(normalizeKeyword(keyword).isEmpty() ? View.GONE : View.VISIBLE);
    }

    private void updateEmptyState(boolean show) {
        if (emptyLayout == null || recyclerView == null) {
            return;
        }
        emptyLayout.setVisibility(show ? View.VISIBLE : View.GONE);
        recyclerView.setVisibility(show ? View.INVISIBLE : View.VISIBLE);
    }

    private void applyFriend(FriendBean friendBean) {
        if (friendBean == null || TextUtils.isEmpty(friendBean.getUser_id())) {
            return;
        }
        ServiceManager.getUserService().applyFriend(friendBean.getUser_id(), new ApiCallback<FriendApplicationBean>() {
            @Override
            public void onSuccess(FriendApplicationBean data) {
                Toast.makeText(AddFriendActivity.this,
                        R.string.add_friend_request_sent,
                        Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onError(int code, String message) {
                Toast.makeText(AddFriendActivity.this,
                        getString(R.string.add_friend_request_failed, String.valueOf(message)),
                        Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void hideKeyboard(View target) {
        InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (imm != null && target != null) {
            imm.hideSoftInputFromWindow(target.getWindowToken(), 0);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (pendingSearchTask != null) {
            searchHandler.removeCallbacks(pendingSearchTask);
            pendingSearchTask = null;
        }
    }

    static class SearchAdapter extends RecyclerView.Adapter<SearchAdapter.ItemViewHolder> {

        private final List<FriendBean> items = new ArrayList<>();
        private OnAddClickListener onAddClickListener;

        void setItems(List<FriendBean> newItems) {
            items.clear();
            if (newItems != null) {
                items.addAll(newItems);
            }
            notifyDataSetChanged();
        }

        void setOnAddClickListener(OnAddClickListener onAddClickListener) {
            this.onAddClickListener = onAddClickListener;
        }

        @NonNull
        @Override
        public ItemViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_search_result, parent, false);
            return new ItemViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ItemViewHolder holder, int position) {
            FriendBean item = items.get(position);
            String displayName = resolveDisplayName(item);
            holder.nameView.setText(displayName);
            AvatarUtils.loadAvatar(holder.avatarView, item.getAvatar(), displayName);
            holder.addView.setOnClickListener(v -> {
                if (onAddClickListener != null) {
                    onAddClickListener.onAddClick(item);
                }
            });
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        private String resolveDisplayName(FriendBean item) {
            if (item == null) {
                return "";
            }
            if (!TextUtils.isEmpty(item.getNickname())) {
                return item.getNickname();
            }
            if (!TextUtils.isEmpty(item.getUser_id())) {
                return item.getUser_id();
            }
            if (!TextUtils.isEmpty(item.getPhone())) {
                return item.getPhone();
            }
            return Locale.getDefault().getLanguage().startsWith("zh") ? "未知用户" : "Unknown";
        }

        interface OnAddClickListener {
            void onAddClick(FriendBean friendBean);
        }

        static class ItemViewHolder extends RecyclerView.ViewHolder {
            final ImageView avatarView;
            final TextView nameView;
            final TextView addView;

            ItemViewHolder(@NonNull View itemView) {
                super(itemView);
                avatarView = itemView.findViewById(R.id.iv_avatar);
                nameView = itemView.findViewById(R.id.tv_name);
                addView = itemView.findViewById(R.id.friend_add);
            }
        }
    }
}

package com.juggle.im.android.app;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
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

public class AddFriendActivity extends AppCompatActivity {
    private EditText edtSearch;
    private TextView btnCancel;
    private RecyclerView rvResults;
    private ProgressBar progressBar;
    private SearchAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_add_friend);

        edtSearch = findViewById(R.id.edt_search);
        btnCancel = findViewById(R.id.btn_cancel);
        rvResults = findViewById(R.id.rv_results);
        progressBar = findViewById(R.id.progress_bar);
        View btnBack = findViewById(R.id.btn_back);
        if (btnBack != null) {
            btnBack.setOnClickListener(v -> finish());
        }

        adapter = new SearchAdapter(new ArrayList<>());
        rvResults.setLayoutManager(new LinearLayoutManager(this));
        rvResults.setAdapter(adapter);

        edtSearch.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) {
                showKeyboard(edtSearch);
            }
        });

        edtSearch.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                String t = s == null ? "" : s.toString();
                btnCancel.setVisibility(TextUtils.isEmpty(t) ? View.GONE : View.VISIBLE);
                // show a temporary search item at top
                adapter.setSearchPreview(t);
            }
            @Override public void afterTextChanged(Editable s) {}
        });

        btnCancel.setOnClickListener(v -> {
            edtSearch.setText("");
            edtSearch.clearFocus();
            hideKeyboard();
        });

        adapter.setOnSearchPreviewClick(keyword -> {
            // remove preview and perform search
            adapter.setSearchPreview(null);
            edtSearch.clearFocus();
            hideKeyboard();
            doSearch(keyword);
        });

        adapter.setOnItemClick(user -> {
            // apply friend
            ServiceManager.getUserService().applyFriend(user.getUser_id(), new ApiCallback<FriendApplicationBean>() {
                @Override
                public void onSuccess(FriendApplicationBean data) {
                    Toast.makeText(AddFriendActivity.this, "Request sent", Toast.LENGTH_SHORT).show();
                }

                @Override
                public void onError(int code, String message) {
                    Toast.makeText(AddFriendActivity.this, "Failed: " + message, Toast.LENGTH_SHORT).show();
                }
            });
        });
    }

    private void doSearch(String keyword) {
        if (TextUtils.isEmpty(keyword)) return;
        progressBar.setVisibility(View.VISIBLE);
        ServiceManager.getUserService().searchUsers(keyword, new ApiCallback<FriendsListData>() {
            @Override
            public void onSuccess(FriendsListData data) {
                progressBar.setVisibility(View.GONE);
                adapter.setItems(data.getItems());
            }

            @Override
            public void onError(int code, String message) {
                progressBar.setVisibility(View.GONE);
                Toast.makeText(AddFriendActivity.this, "Search failed: " + message, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void showKeyboard(View v) {
        v.post(() -> {
            InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
            if (imm != null) imm.showSoftInput(v, InputMethodManager.SHOW_IMPLICIT);
        });
    }

    private void hideKeyboard() {
        InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        View v = getCurrentFocus();
        if (v == null) v = new View(this);
        if (imm != null) imm.hideSoftInputFromWindow(v.getWindowToken(), 0);
    }

    // simple adapter
    static class SearchAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
        private static final int TYPE_PREVIEW = 0;
        private static final int TYPE_ITEM = 1;
        private String preview;
        private List<FriendBean> items;
        private OnSearchPreviewClick previewClick;
        private OnItemClick itemClick;

        interface OnSearchPreviewClick { void onClick(String keyword); }
        interface OnItemClick { void onClick(FriendBean user); }

        SearchAdapter(List<FriendBean> items) { this.items = items; }

        void setSearchPreview(String p) { this.preview = p; notifyDataSetChanged(); }
        void setItems(List<FriendBean> newItems) { this.items = newItems; notifyDataSetChanged(); }
        void setOnSearchPreviewClick(OnSearchPreviewClick l) { this.previewClick = l; }
        void setOnItemClick(OnItemClick l) { this.itemClick = l; }

        @Override public int getItemViewType(int position) {
            if (preview != null && !preview.isEmpty()) {
                return position == 0 ? TYPE_PREVIEW : TYPE_ITEM;
            }
            return TYPE_ITEM;
        }

        @Override public int getItemCount() {
            int base = items == null ? 0 : items.size();
            return (preview != null && !preview.isEmpty()) ? base + 1 : base;
        }

        @NonNull @Override public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            if (viewType == TYPE_PREVIEW) {
                View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_search_preview, parent, false);
                return new PreviewHolder(v);
            }
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_search_result, parent, false);
            return new ItemHolder(v);
        }

        @Override public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            if (getItemViewType(position) == TYPE_PREVIEW) {
                PreviewHolder h = (PreviewHolder) holder;
                h.tv.setText("Search: " + preview);
                h.itemView.setOnClickListener(v -> {
                    if (previewClick != null) previewClick.onClick(preview);
                });
                return;
            }
            int idx = (preview != null && !preview.isEmpty()) ? position - 1 : position;
            FriendBean user = items.get(idx);
            ItemHolder h = (ItemHolder) holder;
            h.tvName.setText(user.getNickname());
            AvatarUtils.loadAvatar(h.ivAvatar, user.getAvatar(), user.getNickname());
            h.tvFriendAdd.setOnClickListener(v -> { if (itemClick != null) itemClick.onClick(user); });
        }

        static class PreviewHolder extends RecyclerView.ViewHolder {
            TextView tv;
            PreviewHolder(@NonNull View v) { super(v); tv = v.findViewById(R.id.tv_preview); }
        }

        static class ItemHolder extends RecyclerView.ViewHolder {
            ImageView ivAvatar; TextView tvName, tvFriendAdd;
            ItemHolder(@NonNull View v) { super(v);
                ivAvatar = v.findViewById(R.id.iv_avatar);
                tvName = v.findViewById(R.id.tv_name);
                tvFriendAdd = v.findViewById(R.id.friend_add);
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        hideKeyboard();
    }
}

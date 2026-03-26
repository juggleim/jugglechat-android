package com.juggle.im.android.chat;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.KeyEvent;
import android.view.View;
import android.view.Window;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.juggle.im.JIM;
import com.juggle.im.android.R;
import com.juggle.im.android.component.AbsAppActivity;
import com.juggle.im.android.server.beans.FriendsListData;
import com.juggle.im.android.server.beans.GroupListData;
import com.juggle.im.android.server.http.ApiCallback;
import com.juggle.im.android.server.http.ServiceManager;
import com.juggle.im.model.Conversation;
import com.juggle.im.model.MessageQueryOptions;

import java.util.Arrays;
import java.util.List;

public class SearchActivity extends AbsAppActivity {

    private static final long SEARCH_DEBOUNCE_MS = 280L;
    private static final int PREVIEW_CONTACT_LIMIT = 3;
    private static final int PREVIEW_GROUP_LIMIT = 3;
    private static final int PREVIEW_RECORD_LIMIT = 2;

    public static final String SEARCH_TYPE_CONTACT = "联系人";
    public static final String SEARCH_TYPE_GROUP = "群聊";
    public static final String SEARCH_TYPE_RECORD = "聊天记录";

    private final Handler searchHandler = new Handler(Looper.getMainLooper());
    private Runnable pendingSearchTask;

    private long searchVersion = 0L;
    private String currentKeyword = "";

    private EditText searchInput;
    private ImageView clearView;
    private TextView cancelView;
    private RecyclerView recyclerView;
    private SearchAdapter adapter;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_search);

        setupWindowStyle();
        initViews();
        bindEvents();
    }

    private void setupWindowStyle() {
        Window window = getWindow();
        window.setStatusBarColor(getColor(R.color.conversation_page_bg));
        window.setNavigationBarColor(getColor(R.color.conversation_page_bg));
        WindowInsetsControllerCompat controller =
                new WindowInsetsControllerCompat(window, window.getDecorView());
        controller.setAppearanceLightStatusBars(true);
    }

    private void initViews() {
        searchInput = findViewById(R.id.search_input);
        clearView = findViewById(R.id.iv_search_clear);
        cancelView = findViewById(R.id.tv_cancel);
        recyclerView = findViewById(R.id.search_recycler_view);

        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new SearchAdapter();
        adapter.setOnMoreClickListener(type -> {
            if (TextUtils.isEmpty(currentKeyword)) {
                return;
            }
            Intent intent = SearchMoreResultsActivity.intentFor(this, type, currentKeyword);
            startActivity(intent);
        });
        recyclerView.setAdapter(adapter);

        updateClearButton("");
        searchInput.requestFocus();
    }

    private void bindEvents() {
        cancelView.setOnClickListener(v -> finish());
        clearView.setOnClickListener(v -> {
            cancelPendingSearch();
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
                searchNow(v.getText() == null ? "" : v.getText().toString());
                hideKeyboard();
                return true;
            }
            return false;
        });
    }

    private void scheduleSearch(String rawKeyword) {
        String keyword = normalizeKeyword(rawKeyword);
        cancelPendingSearch();

        if (keyword.isEmpty()) {
            currentKeyword = "";
            adapter.setKeyword("");
            adapter.clear();
            nextSearchVersion();
            return;
        }

        pendingSearchTask = () -> executeSearch(keyword);
        searchHandler.postDelayed(pendingSearchTask, SEARCH_DEBOUNCE_MS);
    }

    private void searchNow(String rawKeyword) {
        String keyword = normalizeKeyword(rawKeyword);
        cancelPendingSearch();
        if (keyword.isEmpty()) {
            return;
        }
        executeSearch(keyword);
    }

    private void executeSearch(String keyword) {
        currentKeyword = keyword;
        long token = nextSearchVersion();
        adapter.setKeyword(keyword);
        adapter.clear();

        requestContactPreview(keyword, token);
        requestGroupPreview(keyword, token);
        requestRecordPreview(keyword, token);
    }

    private void requestContactPreview(String keyword, long token) {
        ServiceManager.getUserService().searchFriends(keyword, 0, 5, new ApiCallback<FriendsListData>() {
            @Override
            public void onSuccess(FriendsListData data) {
                if (!isResultValid(token, keyword)) {
                    return;
                }
                List<SearchResult> results = SearchResultMapper.mapFriendResults(
                        data == null ? null : data.getItems(),
                        PREVIEW_CONTACT_LIMIT);
                adapter.addResults(results, SEARCH_TYPE_CONTACT);
            }

            @Override
            public void onError(int code, String message) {
            }
        });
    }

    private void requestGroupPreview(String keyword, long token) {
        ServiceManager.getUserService().searchMyGroups(keyword, 5, new ApiCallback<GroupListData>() {
            @Override
            public void onSuccess(GroupListData data) {
                if (!isResultValid(token, keyword)) {
                    return;
                }
                List<SearchResult> results = SearchResultMapper.mapGroupResults(
                        data == null ? null : data.getItems(),
                        PREVIEW_GROUP_LIMIT);
                adapter.addResults(results, SEARCH_TYPE_GROUP);
            }

            @Override
            public void onError(int code, String message) {
            }
        });
    }

    private void requestRecordPreview(String keyword, long token) {
        MessageQueryOptions.Builder builder = new MessageQueryOptions.Builder();
        builder.setSearchContent(keyword);
        builder.setConversationTypes(Arrays.asList(
                Conversation.ConversationType.GROUP,
                Conversation.ConversationType.PRIVATE));

        JIM.getInstance().getMessageManager().searchConversationsWithMessageContent(builder.build(), list -> {
            if (!isResultValid(token, keyword)) {
                return;
            }
            List<SearchResult> results = SearchResultMapper.mapRecordResults(list, PREVIEW_RECORD_LIMIT);
            adapter.addResults(results, SEARCH_TYPE_RECORD);
        });
    }

    private long nextSearchVersion() {
        searchVersion += 1L;
        return searchVersion;
    }

    private boolean isResultValid(long token, String keyword) {
        if (token != searchVersion) {
            return false;
        }
        String latest = normalizeKeyword(searchInput.getText() == null ? "" : searchInput.getText().toString());
        return TextUtils.equals(latest, keyword);
    }

    private String normalizeKeyword(String keyword) {
        return keyword == null ? "" : keyword.trim();
    }

    private void updateClearButton(String keyword) {
        if (clearView == null) {
            return;
        }
        clearView.setVisibility(normalizeKeyword(keyword).isEmpty() ? View.GONE : View.VISIBLE);
    }

    private void cancelPendingSearch() {
        if (pendingSearchTask != null) {
            searchHandler.removeCallbacks(pendingSearchTask);
            pendingSearchTask = null;
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        hideKeyboard();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        cancelPendingSearch();
    }

    private void hideKeyboard() {
        View view = getCurrentFocus();
        if (view != null) {
            InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) {
                imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
            }
        }
    }
}

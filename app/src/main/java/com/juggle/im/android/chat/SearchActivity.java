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
import androidx.recyclerview.widget.SimpleItemAnimator;

import com.juggle.im.JIM;
import com.juggle.im.android.R;
import com.juggle.im.android.component.AbsAppActivity;
import com.juggle.im.android.server.beans.FriendBean;
import com.juggle.im.android.server.beans.GroupListData;
import com.juggle.im.android.server.http.ApiCallback;
import com.juggle.im.android.server.http.ServiceManager;
import com.juggle.im.model.Conversation;
import com.juggle.im.model.MessageQueryOptions;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;

public class SearchActivity extends AbsAppActivity {

    private static final long SEARCH_DEBOUNCE_MS = 280L;
    private static final int PREVIEW_CONTACT_LIMIT = 3;
    private static final int PREVIEW_GROUP_LIMIT = 3;
    private static final int PREVIEW_RECORD_LIMIT = 2;
    private static final int SEARCH_RESULT_SOURCE_COUNT = 3;

    public static final String SEARCH_TYPE_CONTACT = "联系人";
    public static final String SEARCH_TYPE_GROUP = "群聊";
    public static final String SEARCH_TYPE_RECORD = "聊天记录";

    private final Handler searchHandler = new Handler(Looper.getMainLooper());
    private Runnable pendingSearchTask;

    private long searchVersion = 0L;
    private String currentKeyword = "";
    private String lastSearchedKeyword = "";

    private EditText searchInput;
    private ImageView clearView;
    private TextView cancelView;
    private View loadingView;
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
        loadingView = findViewById(R.id.layout_search_loading);
        recyclerView = findViewById(R.id.search_recycler_view);

        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new SearchAdapter();
        RecyclerView.ItemAnimator itemAnimator = recyclerView.getItemAnimator();
        if (itemAnimator instanceof SimpleItemAnimator) {
            ((SimpleItemAnimator) itemAnimator).setSupportsChangeAnimations(false);
        }
        recyclerView.setItemAnimator(null);
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
            lastSearchedKeyword = "";
            showLoading(false);
            adapter.clear();
            nextSearchVersion();
            return;
        }

        if (TextUtils.equals(keyword, lastSearchedKeyword)) {
            return;
        }

        pendingSearchTask = () -> executeSearch(keyword);
        searchHandler.postDelayed(pendingSearchTask, SEARCH_DEBOUNCE_MS);
    }

    private void searchNow(String rawKeyword) {
        String keyword = normalizeKeyword(rawKeyword);
        cancelPendingSearch();
        if (keyword.isEmpty()) {
            currentKeyword = "";
            lastSearchedKeyword = "";
            showLoading(false);
            adapter.clear();
            nextSearchVersion();
            return;
        }
        if (TextUtils.equals(keyword, lastSearchedKeyword)) {
            hideKeyboard();
            return;
        }
        executeSearch(keyword);
    }

    private void executeSearch(String keyword) {
        currentKeyword = keyword;
        lastSearchedKeyword = keyword;
        showLoading(true);
        long token = nextSearchVersion();
        SearchAccumulator accumulator = new SearchAccumulator(keyword, token);

        requestContactPreview(keyword, token, accumulator);
        requestGroupPreview(keyword, token, accumulator);
        requestRecordPreview(keyword, token, accumulator);
    }

    private void requestContactPreview(String keyword, long token, SearchAccumulator accumulator) {
        LocalUserSearchCoordinator.searchUsers(keyword, 5, new LocalUserSearchCoordinator.Callback() {
            @Override
            public void onSuccess(List<FriendBean> data) {
                if (!isResultValid(token, keyword)) {
                    return;
                }
                List<SearchResult> results = SearchResultMapper.mapFriendResults(
                        data,
                        PREVIEW_CONTACT_LIMIT);
                accumulator.accept(SEARCH_TYPE_CONTACT, results);
            }

            @Override
            public void onError(String message) {
                if (!isResultValid(token, keyword)) {
                    return;
                }
                accumulator.accept(SEARCH_TYPE_CONTACT, null);
            }
        });
    }

    private void requestGroupPreview(String keyword, long token, SearchAccumulator accumulator) {
        ServiceManager.getUserService().searchMyGroups(keyword, 5, new ApiCallback<GroupListData>() {
            @Override
            public void onSuccess(GroupListData data) {
                if (!isResultValid(token, keyword)) {
                    return;
                }
                List<SearchResult> results = SearchResultMapper.mapGroupResults(
                        data == null ? null : data.getItems(),
                        PREVIEW_GROUP_LIMIT);
                accumulator.accept(SEARCH_TYPE_GROUP, results);
            }

            @Override
            public void onError(int code, String message) {
                if (!isResultValid(token, keyword)) {
                    return;
                }
                accumulator.accept(SEARCH_TYPE_GROUP, null);
            }
        });
    }

    private void requestRecordPreview(String keyword, long token, SearchAccumulator accumulator) {
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
            accumulator.accept(SEARCH_TYPE_RECORD, results);
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

    /**
     * 控制搜索轻量加载提示显隐。
     *
     * <p>tips：loading 只作为过渡提示，不遮挡旧结果，避免页面大面积闪烁。</p>
     */
    private void showLoading(boolean show) {
        if (loadingView == null) {
            return;
        }
        loadingView.setVisibility(show ? View.VISIBLE : View.GONE);
    }

    private void cancelPendingSearch() {
        if (pendingSearchTask != null) {
            searchHandler.removeCallbacks(pendingSearchTask);
            pendingSearchTask = null;
        }
    }

    /**
     * 聚合搜索结果后统一刷新页面。
     *
     * <p>tips：等待三路搜索都返回后再一次性提交结果，避免列表先清空再分段回填造成闪烁。</p>
     */
    private final class SearchAccumulator {
        private final String keyword;
        private final long token;
        private final LinkedHashMap<String, List<SearchResult>> resultMap = new LinkedHashMap<>();
        private int completedCount = 0;

        SearchAccumulator(String keyword, long token) {
            this.keyword = keyword;
            this.token = token;
        }

        void accept(String type, List<SearchResult> results) {
            if (!isResultValid(token, keyword)) {
                return;
            }
            completedCount++;
            if (results != null && !results.isEmpty()) {
                resultMap.put(type, results);
            }
            if (completedCount >= SEARCH_RESULT_SOURCE_COUNT) {
                showLoading(false);
                adapter.submitResults(resultMap, keyword);
            }
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
        showLoading(false);
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

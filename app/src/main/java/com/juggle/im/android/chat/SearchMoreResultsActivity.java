package com.juggle.im.android.chat;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.view.Window;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

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
import java.util.Collections;
import java.util.List;

public class SearchMoreResultsActivity extends AbsAppActivity {

    private static final String EXTRA_SEARCH_TYPE = "extra_search_type";
    private static final String EXTRA_SEARCH_KEYWORD = "extra_search_keyword";

    private static final int FULL_CONTACT_LIMIT = 50;
    private static final int FULL_GROUP_LIMIT = 50;

    private String searchType;
    private String keyword;

    private TextView titleView;
    private TextView emptyView;
    private ProgressBar progressBar;
    private RecyclerView recyclerView;
    private SearchMoreResultAdapter adapter;

    public static Intent intentFor(Context context, String searchType, String keyword) {
        Intent intent = new Intent(context, SearchMoreResultsActivity.class);
        intent.putExtra(EXTRA_SEARCH_TYPE, searchType);
        intent.putExtra(EXTRA_SEARCH_KEYWORD, keyword);
        return intent;
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_search_more_results);

        setupWindowStyle();

        searchType = getIntent().getStringExtra(EXTRA_SEARCH_TYPE);
        keyword = normalizeKeyword(getIntent().getStringExtra(EXTRA_SEARCH_KEYWORD));
        if (TextUtils.isEmpty(searchType) || TextUtils.isEmpty(keyword)) {
            finish();
            return;
        }

        initViews();
        loadResults();
    }

    private void setupWindowStyle() {
        Window window = getWindow();
        window.setStatusBarColor(getColor(R.color.white));
        window.setNavigationBarColor(getColor(R.color.conversation_page_bg));
        WindowInsetsControllerCompat controller = new WindowInsetsControllerCompat(window, window.getDecorView());
        controller.setAppearanceLightStatusBars(true);
    }

    private void initViews() {
        titleView = findViewById(R.id.tv_title);
        emptyView = findViewById(R.id.tv_empty);
        progressBar = findViewById(R.id.progress_bar);
        recyclerView = findViewById(R.id.rv_more_results);

        findViewById(R.id.iv_back).setOnClickListener(v -> finish());
        titleView.setText(searchType);
        emptyView.setText(getString(R.string.search_more_empty, searchType));

        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new SearchMoreResultAdapter();
        adapter.setKeyword(keyword);
        recyclerView.setAdapter(adapter);
    }

    private void loadResults() {
        progressBar.setVisibility(View.VISIBLE);

        if (SearchActivity.SEARCH_TYPE_CONTACT.equals(searchType)) {
            LocalUserSearchCoordinator.searchUsers(keyword, FULL_CONTACT_LIMIT, new LocalUserSearchCoordinator.Callback() {
                @Override
                public void onSuccess(List<FriendBean> data) {
                    List<SearchResult> items = SearchResultMapper.mapFriendResults(
                            data,
                            FULL_CONTACT_LIMIT);
                    showResults(items);
                }

                @Override
                public void onError(String message) {
                    onLoadError(message);
                }
            });
            return;
        }

        if (SearchActivity.SEARCH_TYPE_GROUP.equals(searchType)) {
            ServiceManager.getUserService().searchMyGroups(keyword, FULL_GROUP_LIMIT, new ApiCallback<GroupListData>() {
                @Override
                public void onSuccess(GroupListData data) {
                    List<SearchResult> items = SearchResultMapper.mapGroupResults(
                            data == null ? null : data.getItems(),
                            FULL_GROUP_LIMIT);
                    showResults(items);
                }

                @Override
                public void onError(int code, String message) {
                    onLoadError(message);
                }
            });
            return;
        }

        if (SearchActivity.SEARCH_TYPE_RECORD.equals(searchType)) {
            MessageQueryOptions.Builder builder = new MessageQueryOptions.Builder();
            builder.setSearchContent(keyword);
            builder.setConversationTypes(Arrays.asList(
                    Conversation.ConversationType.GROUP,
                    Conversation.ConversationType.PRIVATE));

            JIM.getInstance().getMessageManager().searchConversationsWithMessageContent(builder.build(), list -> {
                List<SearchResult> items = SearchResultMapper.mapRecordResults(list, -1);
                showResults(items);
            });
            return;
        }

        showResults(Collections.emptyList());
    }

    private void showResults(List<SearchResult> items) {
        progressBar.setVisibility(View.GONE);
        adapter.submit(items);
        boolean empty = items == null || items.isEmpty();
        emptyView.setVisibility(empty ? View.VISIBLE : View.GONE);
        recyclerView.setVisibility(empty ? View.GONE : View.VISIBLE);
    }

    private void onLoadError(String message) {
        showResults(Collections.emptyList());
        Toast.makeText(this,
                getString(R.string.search_more_load_failed, String.valueOf(message)),
                Toast.LENGTH_SHORT).show();
    }

    private String normalizeKeyword(String text) {
        return text == null ? "" : text.trim();
    }
}

package com.juggle.im.android.chat;

import android.content.Context;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.juggle.im.JIM;
import com.juggle.im.android.R;
import com.juggle.im.android.server.beans.FriendBean;
import com.juggle.im.android.server.beans.FriendsListData;
import com.juggle.im.android.server.beans.GroupBean;
import com.juggle.im.android.server.beans.GroupListData;
import com.juggle.im.android.server.http.ApiCallback;
import com.juggle.im.android.server.http.ServiceManager;
import com.juggle.im.model.Conversation;
import com.juggle.im.model.MessageQueryOptions;
import com.juggle.im.model.SearchConversationsResult;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class SearchActivity extends AppCompatActivity {

    private EditText searchInput;
    private RecyclerView recyclerView;
    private SearchAdapter adapter;

    public final static String SEARCH_TYPE_CONTACT = "联系人";
    public final static String SEARCH_TYPE_GROUP = "群聊";
    public final static String SEARCH_TYPE_RECORD = "聊天记录";


    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_search);

        searchInput = findViewById(R.id.search_input);
        recyclerView = findViewById(R.id.search_recycler_view);
        findViewById(R.id.iv_back).setOnClickListener(v -> finish());
        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        adapter = new SearchAdapter();
        recyclerView.setAdapter(adapter);

        searchInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                search(s.toString());
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });
    }

    private void search(String keyword) {
        if (keyword.isEmpty()) {
            adapter.clear();
            return;
        }
        adapter.clear();
        // 搜索好友
        ServiceManager.getUserService().searchFriends(keyword, 0, 5, new ApiCallback<FriendsListData>() {
            @Override
            public void onSuccess(FriendsListData data) {
                List<SearchResult> results = new ArrayList<>();
                if (data != null && data.getItems() != null) {
                    for (FriendBean friend : data.getItems()) {
                        results.add(new SearchResult(friend.getUser_id(), friend.getNickname(), friend.getAvatar(), SEARCH_TYPE_CONTACT, null));
                    }
                }
                adapter.addResults(results, SEARCH_TYPE_CONTACT);
            }

            @Override
            public void onError(int code, String message) {
            }
        });

        // 搜索群组
        ServiceManager.getUserService().searchMyGroups(keyword, 5, new ApiCallback<GroupListData>() {
            @Override
            public void onSuccess(GroupListData data) {
                List<SearchResult> results = new ArrayList<>();
                if (data != null) {
                    for (GroupBean group : data.getItems()) {
                        results.add(new SearchResult(group.getGroup_id(), group.getGroup_name(), group.getGroup_portrait(), SEARCH_TYPE_GROUP, null));
                    }
                }
                adapter.addResults(results, SEARCH_TYPE_GROUP);
            }

            @Override
            public void onError(int code, String message) {

            }
        });

        // 搜索会话
        MessageQueryOptions.Builder builder = new MessageQueryOptions.Builder();
        builder.setSearchContent(keyword);
        builder.setConversationTypes(Arrays.asList(Conversation.ConversationType.GROUP, Conversation.ConversationType.PRIVATE));
        JIM.getInstance().getMessageManager().searchConversationsWithMessageContent(builder.build(), list -> {
            List<SearchResult> results = new ArrayList<>();
            int count = 0;
            for (SearchConversationsResult l : list) {
                SearchResult r = new SearchResult(l.getConversationInfo().getConversation().getConversationId(),
                        l.getConversationInfo().getConversation().getConversationId(), "",
                        SEARCH_TYPE_RECORD,
                        l.getMatchedCount() + "条匹配记录");
                r.setConversation(l.getConversationInfo().getConversation());
                results.add(r);
                count++;
                if (count >= 5) break;
            }
            adapter.addResults(results, SEARCH_TYPE_RECORD);
        });
    }
    @Override
    protected void onPause() {
        super.onPause();
        hideKeyboard();
    }
    private void hideKeyboard() {
        View view = getCurrentFocus();
        if (view != null) {
            InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
        }
    }

}
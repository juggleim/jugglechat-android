package com.juggle.im.android.chat;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
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
import com.juggle.im.interfaces.IMessageManager;
import com.juggle.im.model.MessageQueryOptions;
import com.juggle.im.model.SearchConversationsResult;

import java.util.ArrayList;
import java.util.List;

public class SearchActivity extends AppCompatActivity {

    private EditText searchInput;
    private RecyclerView recyclerView;
    private SearchAdapter adapter;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_search);

        searchInput = findViewById(R.id.search_input);
        recyclerView = findViewById(R.id.search_recycler_view);
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
        ServiceManager.getUserService().searchFriends(keyword, new ApiCallback<FriendsListData>() {
            @Override
            public void onSuccess(FriendsListData data) {
                List<SearchResult> results = new ArrayList<>();
                if (data != null && data.getItems() != null) {
                    for (FriendBean friend : data.getItems()) {
                        results.add(new SearchResult(friend.getNickname(), friend.getAvatar(), "联系人"));
                    }
                }
                adapter.addResults(results, "联系人");
            }

            @Override
            public void onError(int code, String message) {
            }
        });

        // 搜索群组
        ServiceManager.getUserService().searchMyGroups(keyword, 100, new ApiCallback<GroupListData>() {
            @Override
            public void onSuccess(GroupListData data) {
                List<SearchResult> results = new ArrayList<>();
                if (data != null) {
                    for (GroupBean group : data.getItems()) {
                        results.add(new SearchResult(group.getGroup_name(), group.getGroup_portrait(), "群聊"));
                    }
                }
                adapter.addResults(results, "群聊");
            }

            @Override
            public void onError(int code, String message) {

            }
        });

        // 搜索会话
        MessageQueryOptions.Builder builder = new MessageQueryOptions.Builder();
        builder.setSearchContent(keyword);
        JIM.getInstance().getMessageManager().searchConversationsWithMessageContent(builder.build(), new IMessageManager.ISearchConversationWithMessageContentCallback() {
            @Override
            public void onComplete(List<SearchConversationsResult> list) {
                List<SearchResult> results = new ArrayList<>();
                for (SearchConversationsResult r : list) {
                    results.add(new SearchResult(r.getConversationInfo().getConversation().getConversationId(), "", "聊天记录", r.getMatchedCount() + "条匹配记录"));
                }
                adapter.addResults(results, "聊天记录");
            }
        });
    }
}
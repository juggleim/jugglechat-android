package com.juggle.im.android.chat;

import android.content.Intent;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.juggle.im.JIM;
import com.juggle.im.JIMConst;
import com.juggle.im.android.R;
import com.juggle.im.android.model.UiConversation;
import com.juggle.im.model.Conversation;
import com.juggle.im.model.ConversationInfo;
import com.juggle.im.model.GroupInfo;
import com.juggle.im.model.UserInfo;

import java.util.ArrayList;
import java.util.List;

/**
 * Simplified conversation list used for forwarding: items only show avatar and name. Selecting
 * an item returns the chosen conversation via Activity result.
 */
public class ForwardConversationListFragment extends Fragment implements ForwardConversationListAdapter.OnConversationClickListener {
    private RecyclerView recyclerView;
    private ForwardConversationListAdapter adapter;
    private SwipeRefreshLayout swipeRefresh;

    // pagination state
    private long cursor = -1L;
    private boolean isLoading = false;
    private boolean noMore = false;
    private static final int PAGE_SIZE = 20;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_forward_conversation_list, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        recyclerView = view.findViewById(R.id.rv_forward_conversation_list);
        swipeRefresh = view.findViewById(R.id.swipe_refresh);
        adapter = new ForwardConversationListAdapter();
        adapter.setOnConversationClickListener(this);
        recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        recyclerView.setAdapter(adapter);

        // pull to refresh -> reload from initial cursor
        if (swipeRefresh != null) {
            swipeRefresh.setOnRefreshListener(() -> {
                cursor = -1L;
                noMore = false;
                loadConversations(true);
            });
        }

        // infinite scroll: load more when reaching near bottom
        recyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView rv, int dx, int dy) {
                super.onScrolled(rv, dx, dy);
                if (dy <= 0) return; // only when scrolling down
                LinearLayoutManager lm = (LinearLayoutManager) rv.getLayoutManager();
                if (lm == null) return;
                int total = lm.getItemCount();
                int lastVisible = lm.findLastVisibleItemPosition();
                // when within 5 items of the end, trigger load
                if (!isLoading && !noMore && total > 0 && lastVisible >= total - 5) {
                    loadConversations(false);
                }
            }
        });

        // initial load
        loadConversations(true);
    }
    @Override
    public void onDestroyView() {
        super.onDestroyView();
    }

    @Override
    public void onConversationClick(UiConversation uiConversation) {
        if (getActivity() == null) return;
        Intent data = new Intent();
        data.putExtra(ForwardConversationListActivity.EXTRA_CONVERSATION_ID, uiConversation.getConversationInfo().getConversation().getConversationId());
        data.putExtra(ForwardConversationListActivity.EXTRA_CONVERSATION_NAME, uiConversation.getName());
        data.putExtra(ForwardConversationListActivity.EXTRA_IS_GROUP, uiConversation.getConversationInfo().getConversation().getConversationType().equals(Conversation.ConversationType.GROUP));
        // propagate forward mode from the launching intent so caller can distinguish merge/single
        String mode = getActivity().getIntent().getStringExtra(ForwardConversationListActivity.EXTRA_FORWARD_MODE);
        if (mode != null) data.putExtra(ForwardConversationListActivity.EXTRA_FORWARD_MODE, mode);
        getActivity().setResult(getActivity().RESULT_OK, data);
        getActivity().finish();
    }

    /**
     * Load a page of conversations. If refresh==true then reset the list.
     */
    private void loadConversations(boolean refresh) {
        if (isLoading) return;
        isLoading = true;
        if (swipeRefresh != null && refresh) swipeRefresh.setRefreshing(true);

        // fetch on background thread
        new Thread(() -> {
            try {
                List<ConversationInfo> conversationInfoList = JIM.getInstance().getConversationManager().getConversationInfoList(PAGE_SIZE, cursor, JIMConst.PullDirection.NEWER);
                if (conversationInfoList == null || conversationInfoList.isEmpty()) {
                    // no data
                    noMore = true;
                    if (getActivity() != null) {
                        getActivity().runOnUiThread(() -> {
                            if (swipeRefresh != null) swipeRefresh.setRefreshing(false);
                            isLoading = false;
                        });
                    } else {
                        if (swipeRefresh != null) swipeRefresh.setRefreshing(false);
                        isLoading = false;
                    }
                    return;
                }

                List<UiConversation> uiList = new ArrayList<>();
                for (ConversationInfo info : conversationInfoList) {
                    UiConversation ui = UiConversation.fromConversationInfo(info);
                    // 不显示系统消息
                    if (info.getConversation().getConversationType().equals(Conversation.ConversationType.SYSTEM)) {
                        continue;
                    }
                    if (info.getConversation().getConversationType().equals(Conversation.ConversationType.GROUP)) {
                        GroupInfo groupInfo = JIM.getInstance().getUserInfoManager().getGroupInfo(ui.getConversationInfo().getConversation().getConversationId());
                        if (groupInfo != null) {
                            ui.setName(groupInfo.getGroupName());
                            ui.setAvatar(groupInfo.getPortrait());
                        }
                        UserInfo userInfo = ui.getLastMessage() != null
                                ? JIM.getInstance().getUserInfoManager().getUserInfo(ui.getLastMessage().getSenderUserId())
                                : null;
                        if (userInfo != null) {
                            ui.setLastMessageUserName(userInfo.getUserName());
                        }
                    } else if (info.getConversation().getConversationType().equals(Conversation.ConversationType.PRIVATE)) {
                        UserInfo userInfo = JIM.getInstance().getUserInfoManager().getUserInfo(ui.getConversationInfo().getConversation().getConversationId());
                        if (userInfo != null) {
                            ui.setName(userInfo.getUserName());
                            ui.setAvatar(userInfo.getPortrait());
                            ui.setLastMessageUserName(userInfo.getUserName());
                        }
                    }
                    uiList.add(ui);
                }

                // update cursor to last item's sortTime
                ConversationInfo last = conversationInfoList.get(conversationInfoList.size() - 1);
                cursor = last.getSortTime();
                if (conversationInfoList.size() < PAGE_SIZE) {
                    noMore = true;
                }

                // update adapter on UI thread
                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> {

                            adapter.upsertConversations(uiList);
                        if (swipeRefresh != null) swipeRefresh.setRefreshing(false);
                        isLoading = false;
                    });
                } else {
                    // fallback reset flags
                    isLoading = false;
                    if (swipeRefresh != null) swipeRefresh.setRefreshing(false);
                }
            } catch (Exception e) {
                // restore UI state on error
                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> {
                        if (swipeRefresh != null) swipeRefresh.setRefreshing(false);
                        isLoading = false;
                    });
                } else {
                    isLoading = false;
                }
            }
        }).start();
    }
}

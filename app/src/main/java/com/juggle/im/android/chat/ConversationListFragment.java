package com.juggle.im.android.chat;

import static com.juggle.im.android.chat.MessageListFragment.ARG_MENTION;

import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.PopupWindow;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.juggle.im.JIMConst;
import com.juggle.im.android.R;
import com.juggle.im.android.chat.domain.ConversationRepository;
import com.juggle.im.android.chat.state.ConversationListReducer;
import com.juggle.im.android.core.JIMChatCore;
import com.juggle.im.android.model.UiConversation;
import com.juggle.im.model.Conversation;
import com.juggle.im.model.ConversationInfo;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Conversation list as a Fragment so it can be hosted inside MainActivity.
 */
public class ConversationListFragment extends Fragment implements ConversationListAdapter.OnConversationClickListener {
    private static final String TAG = "ConvListFragment";
    private static final int PAGE_SIZE = 20;

    private RecyclerView conversationListView;
    private View conversationFocusOverlay;
    private ConversationListAdapter conversationListAdapter;
    private PopupWindow popupWindow;
    private ConversationRepository conversationRepository;
    private ConversationListReducer reducer;
    private ConversationListReducer.ConversationListState state = ConversationListReducer.ConversationListState.initial();

    // 追踪用户是否主动滚动离开顶部,用于决定是否自动滚动到新消息
    private boolean userScrolledAway = false;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_conversation_list, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        conversationRepository = new ConversationRepository();
        reducer = new ConversationListReducer(conversationRepository);

        conversationListView = view.findViewById(R.id.rv_conversation_list);
        conversationFocusOverlay = view.findViewById(R.id.v_conversation_focus_overlay);
        conversationListAdapter = new ConversationListAdapter();
        conversationListAdapter.setOnConversationClickListener(this);

        // 设置RecyclerView引用到Adapter,让Adapter可以直接控制滚动
        conversationListAdapter.setRecyclerView(conversationListView);

        // 设置自动滚动检查器,只有当用户在顶部且未主动滚动离开时才自动滚动
        conversationListAdapter.setShouldAutoScrollChecker(() -> {
            boolean atTop = isAtTop();
            boolean shouldScroll = atTop && !userScrolledAway;
            Log.d(TAG,
                    "[检查器] isAtTop=" + atTop + ", userScrolledAway=" + userScrolledAway + ", shouldScroll=" + shouldScroll);
            return shouldScroll;
        });

        // 设置新会话监听器,这里只打印日志,滚动逻辑由Adapter内部直接处理
        conversationListAdapter.setOnNewConversationListener(() -> Log.d(TAG, "[监听器] 新会话插入到顶部"));

        LinearLayoutManager layoutManager = new LinearLayoutManager(requireContext());
        conversationListView.setLayoutManager(layoutManager);
        conversationListView.setAdapter(conversationListAdapter);

        // 禁用默认动画，避免会话移动时出现白屏闪烁（和微信一样）
        conversationListView.setItemAnimator(null);

        // Add scroll listener for pagination and user scroll tracking
        conversationListView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                super.onScrolled(recyclerView, dx, dy);

                // 追踪用户滚动状态:如果用户向下滚动离开顶部,标记为已滚动离开
                // 如果用户滚动回到顶部,恢复自动滚动模式
                if (dy != 0) { // 有滚动发生
                    boolean wasAtTop = isAtTop();
                    Log.d(TAG,
                            "[滚动监听] dy=" + dy + ", isAtTop=" + wasAtTop + ", userScrolledAway=" + userScrolledAway);

                    if (wasAtTop) {
                        // 用户滚动回到顶部,恢复自动滚动模式
                        if (userScrolledAway) {
                            Log.i(TAG, "[滚动监听] 用户滚动回到顶部,恢复自动滚动模式");
                        }
                        userScrolledAway = false;
                    } else if (dy > 0) {
                        // 用户向下滚动离开顶部,标记为已滚动离开
                        if (!userScrolledAway) {
                            Log.i(TAG, "[滚动监听] 用户向下滚动离开顶部");
                        }
                        userScrolledAway = true;
                    }
                }

                // Only load more when scrolling down and not already loading
                if (dy > 0 && !state.isLoadingMore() && state.hasMore()) {
                    int visibleItemCount = layoutManager.getChildCount();
                    int totalItemCount = layoutManager.getItemCount();
                    int firstVisibleItemPosition = layoutManager.findFirstVisibleItemPosition();

                    // Load more when approaching the end (5 items before)
                    if ((visibleItemCount + firstVisibleItemPosition + 5) >= totalItemCount) {
                        loadMoreConversations();
                    }
                }
            }
        });
    }

    /**
     * 检查用户是否在顶部（第一个item完全可见）
     * 参考微信设计:只有在顶部时才自动滚动到新消息
     */
    private boolean isAtTop() {
        LinearLayoutManager layoutManager = (LinearLayoutManager) conversationListView.getLayoutManager();
        if (layoutManager == null)
            return false;

        int firstVisiblePosition = layoutManager.findFirstVisibleItemPosition();
        if (firstVisiblePosition != 0) {
            return false; // 第一个item不是位置0,肯定不在顶部
        }

        // 检查第一个item的偏移量,只有完全可见(偏移量为0)时才认为在顶部
        View firstView = layoutManager.findViewByPosition(0);
        if (firstView == null) {
            return false;
        }

        // 第一个item的top应该近似等于RecyclerView的paddingTop(允许极小误差)
        // 某些情况下可能有1像素的偏移
        int offset = Math.abs(firstView.getTop() - conversationListView.getPaddingTop());
        boolean atTop = offset <= 1;
        Log.v(TAG,
                "[isAtTop] firstPos=" + firstVisiblePosition + ", firstTop=" + firstView.getTop() + ", rvTop="
                        + conversationListView.getPaddingTop() + ", result=" + atTop);
        return atTop;
    }

    /**
     * Load more conversations when scrolling to bottom
     */
    private void loadMoreConversations() {
        if (state.isLoadingMore() || !state.hasMore()) {
            return;
        }
        dispatch(new ConversationListReducer.LoadMoreStarted());
        long cursor = state.getCursor();

        // Load more conversations in background thread
        new Thread(() -> {
            try {
                List<ConversationInfo> page = JIMChatCore.getInstance()
                        .fetchConversationPage(PAGE_SIZE, cursor, JIMConst.PullDirection.OLDER);
                List<UiConversation> mapped = conversationRepository.mapAndSort(page);
                if (getActivity() != null) {
                    getActivity().runOnUiThread(
                            () -> dispatch(new ConversationListReducer.LoadMoreSucceeded(mapped, PAGE_SIZE)));
                }
            } catch (Throwable throwable) {
                Log.w(TAG, "loadMoreConversations failed", throwable);
                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> dispatch(new ConversationListReducer.LoadMoreFailed()));
                }
            }
        }).start();
    }

    public void upsertConversations(List<UiConversation> dataSet) {
        dispatch(new ConversationListReducer.ConversationsMerged(dataSet));
    }

    @Override
    public void onConversationClick(UiConversation uiConversation) {
        int unreadCount = uiConversation.getUnreadCount();
        conversationRepository.clearUnread(uiConversation);
        if (unreadCount > 0) {
            UiConversation updated = copyConversation(uiConversation);
            updated.setUnreadCount(0);
            dispatch(new ConversationListReducer.ConversationsMerged(Collections.singletonList(updated)));
        }

        Conversation.ConversationType conversationType = uiConversation.getConversationType();
        String conversationId = uiConversation.getId();

        Intent intent = ConversationActivity.intentFor(this.getActivity(),
                conversationId,
                uiConversation.getName(),
                conversationType == Conversation.ConversationType.GROUP,
                uiConversation.isTop(),
                uiConversation.isMuted());
        intent.putExtra(ConversationActivity.EXTRA_UNREAD_COUNT, unreadCount);

        ConversationInfo info = uiConversation.getConversationInfo();
        intent.putExtra(ARG_MENTION, info != null && info.getMentionInfo() != null);
        startActivity(intent);
    }

    @Override
    public void onConversationLongClick(UiConversation uiConversation) {
        showPopupMenu(uiConversation);
    }

    private void showPopupMenu(UiConversation uiConversation) {
        // Inflate the popup menu layout
        View menuView = LayoutInflater.from(requireContext()).inflate(R.layout.layout_conversation_popup_menu, null);

        // Create the popup window
        popupWindow = new PopupWindow(menuView, ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, true);
        popupWindow.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        popupWindow.setOutsideTouchable(true);
        popupWindow.setElevation(10);

        // 设置PopupWindow消失监听器，用于清除选中状态
        popupWindow.setOnDismissListener(this::exitConversationContextMode);

        // Get references to menu items
        TextView deleteItem = menuView.findViewById(R.id.menu_delete);
        TextView topItem = menuView.findViewById(R.id.menu_top);
        TextView unreadItem = menuView.findViewById(R.id.menu_unread);
        TextView muteItem = menuView.findViewById(R.id.menu_mute);

        // Set dynamic text based on conversation state
        topItem.setText(uiConversation.isTop() ? "取消置顶" : "置顶");
        unreadItem.setText(uiConversation.getUnreadCount() > 0 ? "标为已读" : "标为未读");
        muteItem.setText(uiConversation.isMuted() ? "取消免打扰" : "免打扰");

        // Set click listeners
        deleteItem.setOnClickListener(v -> {
            deleteConversation(uiConversation);
            popupWindow.dismiss();
        });

        topItem.setOnClickListener(v -> {
            toggleTopConversation(uiConversation);
            popupWindow.dismiss();
        });

        unreadItem.setOnClickListener(v -> {
            toggleUnreadConversation(uiConversation);
            popupWindow.dismiss();
        });

        muteItem.setOnClickListener(v -> {
            toggleMuteConversation(uiConversation);
            popupWindow.dismiss();
        });

        RecyclerView.ViewHolder viewHolder = conversationListView.findViewHolderForAdapterPosition(
                conversationListAdapter.getPosition(uiConversation));
        if (viewHolder != null) {
            View anchorView = viewHolder.itemView;

            // 设置选中状态
            int selectedPosition = conversationListAdapter.getPosition(uiConversation);
            enterConversationContextMode(selectedPosition);

            int[] location = new int[2];
            anchorView.getLocationOnScreen(location); // 获取 item 在屏幕中的绝对坐标

            int anchorY = location[1];

            // 获取屏幕宽度
            int screenWidth = anchorView.getResources().getDisplayMetrics().widthPixels;

            // 获取 PopupWindow 的宽高（需提前测量）
            popupWindow.getContentView().measure(
                    View.MeasureSpec.UNSPECIFIED,
                    View.MeasureSpec.UNSPECIFIED);
            int popupWidth = popupWindow.getContentView().getMeasuredWidth();
            int popupHeight = popupWindow.getContentView().getMeasuredHeight();

            // 计算横向位置：居中到屏幕中间
            int x = (screenWidth - popupWidth) / 2;

            // 计算纵向位置：贴在 item 上方
            int y = anchorY - popupHeight;

            // 注意：Gravity.NO_GRAVITY 才能让 x、y 生效
            popupWindow.showAtLocation(anchorView, Gravity.NO_GRAVITY, x, y);
        } else {
            exitConversationContextMode();
        }
    }

    private void enterConversationContextMode(int selectedPosition) {
        conversationListAdapter.setSelectedPosition(selectedPosition);
        if (conversationFocusOverlay != null) {
            conversationFocusOverlay.setVisibility(View.VISIBLE);
        }
    }

    private void exitConversationContextMode() {
        conversationListAdapter.clearSelectedPosition();
        if (conversationFocusOverlay != null) {
            conversationFocusOverlay.setVisibility(View.GONE);
        }
    }

    private void deleteConversation(UiConversation uiConversation) {
        conversationRepository.delete(uiConversation);
        dispatch(new ConversationListReducer.ConversationRemoved(uiConversation.getId()));
    }

    private void toggleTopConversation(UiConversation uiConversation) {
        boolean newTopStatus = !uiConversation.isTop();
        conversationRepository.setTop(uiConversation, newTopStatus);

        UiConversation updated = copyConversation(uiConversation);
        updated.setTop(newTopStatus);
        if (updated.getConversationInfo() != null) {
            updated.getConversationInfo().setTop(newTopStatus);
        }
        dispatch(new ConversationListReducer.ConversationsMerged(Collections.singletonList(updated)));
    }

    private void toggleUnreadConversation(UiConversation uiConversation) {
        boolean hasUnread = uiConversation.getUnreadCount() > 0;
        if (hasUnread) {
            conversationRepository.clearUnread(uiConversation);
        }

        UiConversation updated = copyConversation(uiConversation);
        updated.setUnreadCount(hasUnread ? 0 : 1);
        dispatch(new ConversationListReducer.ConversationsMerged(Collections.singletonList(updated)));
    }

    private void toggleMuteConversation(UiConversation uiConversation) {
        boolean newMuteStatus = !uiConversation.isMuted();
        conversationRepository.setMute(uiConversation, newMuteStatus);

        UiConversation updated = copyConversation(uiConversation);
        updated.setMuted(newMuteStatus);
        dispatch(new ConversationListReducer.ConversationsMerged(Collections.singletonList(updated)));
    }

    private void dispatch(@NonNull ConversationListReducer.Action action) {
        ConversationListReducer.ConversationListState previous = state;
        ConversationListReducer.ConversationListState next = reducer.reduce(previous, action);
        state = next;
        render(previous, next);
    }

    private void render(@NonNull ConversationListReducer.ConversationListState previous,
            @NonNull ConversationListReducer.ConversationListState next) {
        // 仅在会话快照发生变化时触发 adapter 增量渲染，避免不必要抖动。
        if (previous.getConversations() != next.getConversations()) {
            conversationListAdapter.renderSnapshot(next.getConversations());
        }
    }

    @NonNull
    private UiConversation copyConversation(@NonNull UiConversation source) {
        UiConversation target = new UiConversation();
        target.setConversationInfo(source.getConversationInfo());
        target.setId(source.getId());
        target.setName(source.getName());
        target.setAvatar(source.getAvatar());
        target.setDraft(source.getDraft());
        target.setLastMessageUserName(source.getLastMessageUserName());
        target.setTop(source.isTop());
        target.setTopTime(source.getTopTime());
        target.setSortTime(source.getSortTime());
        target.setUnreadCount(source.getUnreadCount());
        target.setMuted(source.isMuted());
        target.setGroup(source.isGroup());
        target.setConversationTypeKey(source.getConversationTypeKey());
        for (Map.Entry<String, Object> entry : source.getExtensions().entrySet()) {
            target.putExtension(entry.getKey(), entry.getValue());
        }
        return target;
    }
}

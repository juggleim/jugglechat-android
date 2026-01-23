package com.juggle.im.android.chat;

import static com.juggle.im.android.chat.MessageListFragment.ARG_MENTION;

import android.content.Intent;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Gravity;
import android.widget.PopupWindow;
import android.widget.TextView;
import android.graphics.drawable.ColorDrawable;
import android.graphics.Color;

import com.juggle.im.JIM;
import com.juggle.im.android.R;
import com.juggle.im.android.model.UiConversation;
import com.juggle.im.model.Conversation;

import java.util.List;

/**
 * Conversation list as a Fragment so it can be hosted inside MainActivity.
 */
public class ConversationListFragment extends Fragment implements ConversationListAdapter.OnConversationClickListener {
    private static final String TAG = "ConvListFragment";
    private RecyclerView conversationListView;
    private ConversationListAdapter conversationListAdapter;
    private PopupWindow popupWindow;
    private boolean isLoadingMore = false;
    private boolean hasMore = true;
    private static final int PAGE_SIZE = 20;
    // 追踪用户是否主动滚动离开顶部,用于决定是否自动滚动到新消息
    private boolean userScrolledAway = false;
    // 记录在新item插入前是否在顶部,用于插入后的滚动决策
    private boolean wasAtTopBeforeInsert = false;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_conversation_list, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        conversationListView = view.findViewById(R.id.rv_conversation_list);
        conversationListAdapter = new ConversationListAdapter();
        conversationListAdapter.setOnConversationClickListener(this);
        
        // 设置RecyclerView引用到Adapter,让Adapter可以直接控制滚动
        conversationListAdapter.setRecyclerView(conversationListView);

        // 设置自动滚动检查器,只有当用户在顶部且未主动滚动离开时才自动滚动
        conversationListAdapter.setShouldAutoScrollChecker(() -> {
            boolean atTop = isAtTop();
            boolean shouldScroll = atTop && !userScrolledAway;
            Log.d(TAG, "[检查器] isAtTop=" + atTop + ", userScrolledAway=" + userScrolledAway + ", shouldScroll=" + shouldScroll);
            return shouldScroll;
        });

        // 设置新会话监听器,这里只打印日志,滚动逻辑由Adapter内部直接处理
        conversationListAdapter.setOnNewConversationListener(() -> {
            Log.d(TAG, "[监听器] 新会话插入到顶部");
        });
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
                    Log.d(TAG, "[滚动监听] dy=" + dy + ", isAtTop=" + wasAtTop + ", userScrolledAway=" + userScrolledAway);
                    
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
                if (dy > 0 && !isLoadingMore && hasMore) {
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
        if (layoutManager == null) return false;
        
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
        Log.v(TAG, "[isAtTop] firstPos=" + firstVisiblePosition + ", firstTop=" + firstView.getTop() + ", rvTop=" + conversationListView.getPaddingTop() + ", result=" + atTop);
        return atTop;
    }

    /**
     * 检查是否应该自动滚动到顶部
     * 只有当用户在顶部且未主动滚动离开时,才自动滚动
     */
    private boolean shouldAutoScrollToTop() {
        return isAtTop() && !userScrolledAway;
    }

    /**
     * 滚动到顶部
     * 参考微信设计:使用瞬时滚动,确保新消息立即可见
     */
    private void scrollToTop() {
        LinearLayoutManager layoutManager = (LinearLayoutManager) conversationListView.getLayoutManager();
        if (layoutManager != null) {
            // 使用scrollToPositionWithOffset确保第一个item完全对齐到顶部
            // 参数0表示滚动到位置0,参数0表示偏移量为0(完全对齐)
            layoutManager.scrollToPositionWithOffset(0, 0);
        }
    }

    /**
     * Load more conversations when scrolling to bottom
     */
    private void loadMoreConversations() {
        if (isLoadingMore || !hasMore) {
            return;
        }

        isLoadingMore = true;

        // Get the cursor (sortTime of the last item)
        long cursor = conversationListAdapter.getLastSortTime();

        // Load more conversations in background thread
        new Thread(() -> {
            int loadedCount = com.juggle.im.android.core.JIMChatCore.getInstance()
                    .loadMoreConversations(cursor, PAGE_SIZE);

            // Update UI on main thread
            if (getActivity() != null) {
                getActivity().runOnUiThread(() -> {
                    isLoadingMore = false;
                    if (loadedCount < PAGE_SIZE) {
                        hasMore = false;
                    }
                });
            }
        }).start();
    }

    public void upsertConversations(List<UiConversation> dataSet) {
        conversationListAdapter.upsertConversations(dataSet);
    }

    @Override
    public void onConversationClick(UiConversation uiConversation) {
        int unreadCount = uiConversation.getConversationInfo().getUnreadCount();
        JIM.getInstance().getConversationManager()
                .clearUnreadCount(uiConversation.getConversationInfo().getConversation(), null);
        Intent intent = ConversationActivity.intentFor(this.getActivity(),
                uiConversation.getConversationInfo().getConversation().getConversationId(),
                uiConversation.getName(),
                uiConversation.getConversationInfo().getConversation().getConversationType()
                        .equals(Conversation.ConversationType.GROUP),
                uiConversation.isTop(),
                uiConversation.isMuted());
        intent.putExtra(ConversationActivity.EXTRA_UNREAD_COUNT, unreadCount);
        intent.putExtra(ARG_MENTION, uiConversation.getConversationInfo().getMentionInfo() != null);
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
        popupWindow.setOnDismissListener(() -> {
            conversationListAdapter.clearSelectedPosition();
        });

        // Get references to menu items
        TextView deleteItem = menuView.findViewById(R.id.menu_delete);
        TextView topItem = menuView.findViewById(R.id.menu_top);
        TextView muteItem = menuView.findViewById(R.id.menu_mute);

        // Set dynamic text based on conversation state
        topItem.setText(uiConversation.isTop() ? "取消置顶" : "置顶");
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

        muteItem.setOnClickListener(v -> {
            toggleMuteConversation(uiConversation);
            popupWindow.dismiss();
        });

        RecyclerView.ViewHolder viewHolder = conversationListView.findViewHolderForAdapterPosition(
                conversationListAdapter.getPosition(uiConversation));
        if (viewHolder != null) {
            View anchorView = viewHolder.itemView;

            // 设置选中状态
            conversationListAdapter.setSelectedPosition(conversationListAdapter.getPosition(uiConversation));

            int[] location = new int[2];
            anchorView.getLocationOnScreen(location); // 获取 item 在屏幕中的绝对坐标

            int anchorX = location[0];
            int anchorY = location[1];

            // 获取 item 高度和屏幕宽度
            int itemHeight = anchorView.getHeight();
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
        }
    }

    private void deleteConversation(UiConversation uiConversation) {
        JIM.getInstance().getConversationManager().deleteConversationInfo(
                uiConversation.getConversationInfo().getConversation(),
                null);
        conversationListAdapter.removeConversation(uiConversation);
    }

    private void toggleTopConversation(UiConversation uiConversation) {
        boolean newTopStatus = !uiConversation.isTop();
        JIM.getInstance().getConversationManager().setTop(
                uiConversation.getConversationInfo().getConversation(),
                newTopStatus,
                null);
        uiConversation.getConversationInfo().setTop(newTopStatus);
        int idx = conversationListAdapter.getPosition(uiConversation);
        conversationListAdapter.notifyItemChanged(idx);
    }

    private void toggleMuteConversation(UiConversation uiConversation) {
        boolean newMuteStatus = !uiConversation.isMuted();
        JIM.getInstance().getConversationManager().setMute(
                uiConversation.getConversationInfo().getConversation(),
                newMuteStatus,
                null);
        int idx = conversationListAdapter.getPosition(uiConversation);
        conversationListAdapter.notifyItemChanged(idx);
    }
}

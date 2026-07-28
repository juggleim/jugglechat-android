package com.juggle.im.android.chat;

import static android.view.View.GONE;
import static android.view.View.INVISIBLE;
import static android.view.View.VISIBLE;

import static com.juggle.im.android.chat.ConversationActivity.EXTRA_TITLE;

import android.graphics.Rect;
import android.os.Build;
import android.os.Bundle;
import android.content.Intent;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowInsetsController;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.SimpleItemAnimator;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.juggle.im.JIM;
import com.juggle.im.JIMConst;
import com.juggle.im.android.R;
import com.juggle.im.android.app.ContactDetailActivity;
import com.juggle.im.android.chat.message.InsertTimeStatusMessage;
import com.juggle.im.android.chat.utils.MessageUtils;
import com.juggle.im.android.chat.view.ChatInputActionBar;
import com.juggle.im.android.model.UiMessage;
import com.juggle.im.android.utils.ToastUtils;
import com.juggle.im.android.widget.BottomActionSheet;
import com.juggle.im.interfaces.IMessageManager;
import com.juggle.im.model.Conversation;
import com.juggle.im.model.GetMessageOptions;
import com.juggle.im.model.Message;
import com.juggle.im.model.MessageContent;
import com.juggle.im.model.MessageReaction;
import com.juggle.im.model.MessageReactionItem;
import com.juggle.im.model.UserInfo;
import com.juggle.im.model.messages.TextMessage;
import com.juggle.im.model.messages.ImageMessage;
import com.juggle.im.model.messages.FileMessage;
import com.juggle.im.model.messages.MergeMessage;
import com.juggle.im.model.messages.VoiceMessage;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

/**
 * 会话消息流对外契约。
 *
 * <p>上层容器（Activity）仅依赖该接口即可分发消息事件与输入态事件，
 * 避免直接耦合具体 Fragment 实现，方便后续替换为 ViewModel/UDF 容器。</p>
 */
interface MessageStreamSink {
    void onNewMessage(Message message);

    void onUpdateMessage(List<Message> messages);

    /**
     * 移除当前可见窗口中已经到期的消息。
     *
     * @param currentTimeMillis 当前时间戳（毫秒）
     */
    void removeExpiredMessages(long currentTimeMillis);

    void scrollToBottomIfNeeded();

    void insertMention(ArrayList<String> userIds, ArrayList<String> userNames);

    void showKeyboardIfNeed();

    /**
     * 滚动到指定消息并高亮。
     *
     * @param messageId        目标消息 ID
     * @param fallbackTimestamp 目标消息时间戳，用于消息未命中时兜底定位
     */
    void scrollToMessage(@Nullable String messageId, long fallbackTimestamp);
}

public class MessageListFragment extends Fragment implements MessageStreamSink {
    private static final String TAG = "MessageListFragment";
    private static final String ARG_CONV_ID = "arg_conv_id";
    private static final String ARG_IS_GROUP = "arg_is_group";
    private static final String ARG_UNREAD_COUNT = "arg_unread_count";
    public static final String ARG_MENTION = "arg_mention";

    /**
     * 常规分页条数（用于未读跳转、定点拉取、向下续拉等场景）。
     */
    private static final int DEFAULT_PAGE_COUNT = 20;
    /**
     * 首屏加载条数（进入会话时优先拉取更多最近消息，减少首次翻页次数）。
     */
    private static final int INITIAL_PAGE_COUNT = 50;
    /**
     * 下拉刷新加载条数（对齐微信式体验：一次补齐较大批次历史消息）。
     */
    private static final int PULL_REFRESH_PAGE_COUNT = 50;

    private String conversationId;
    private boolean isGroup;
    private int unreadCount;
    // UI state
    private final List<UiMessage> uiMessages = new ArrayList<>();
    private androidx.recyclerview.widget.LinearLayoutManager layoutManager;
    private boolean isLoadingOlder = false;
    private boolean isLoadingNewer = false;
    private boolean hasMoreOlder = true;
    private boolean hasMoreNewer = true;
    private MessageListAdapter adapter;
    private RecyclerView recyclerView;
    private SwipeRefreshLayout swipeRefreshLayout;
    // queue for incoming messages that haven't been applied to adapter yet
    private final List<UiMessage> pendingMessages = new ArrayList<>();
    // whether a submitList call is in progress
    private boolean submitInProgress = false;
    // whether the list is currently scrolled to bottom (showing newest message)
    private boolean atBottom = true;
    // selection mode state
    private boolean selectionMode = false;
    private final Set<String> selectedIds = new HashSet<>();
    private View selectionOptionBar;
    private ImageView btnForwardSelected;
    private ImageView btnDeleteSelected;
    private FrameLayout overlayForwardContainer;
    private FrameLayout overlayMessageContextContainer;
    private View messageContextOverlayView;
    private View messageContextPopupView;
    private FrameLayout pinnedMessageContainer;
    private UiMessage contextPinnedMessage;
    private String contextPinnedMessageId = "";
    private View layoutUnreadBubble;
    private boolean unreadJumpInProgress = false;
    // New Message Bubble
    private View layoutNewMessageBubble;
    private TextView tvNewMessageCount;
    private View layoutTopLoading;
    private View layoutBottomLoading;
    private int newMessageCount = 0;
    private long mentionTargetTimestamp = 0L;
    private String mentionTargetMessageId = "";
    private Runnable clearHighlightRunnable;
    /**
     * 简要描述：记录一次下拉刷新开始时的可视锚点，刷新完成后恢复该锚点，
     * 保证“当前看到的消息”不被拉到最旧位置。
     */
    @Nullable
    private ViewportAnchor pullRefreshAnchor;

    private static final class ViewportAnchor {
        final int firstVisiblePosition;
        final int firstTopOffset;
        final String stableKey;

        ViewportAnchor(int firstVisiblePosition, int firstTopOffset, String stableKey) {
            this.firstVisiblePosition = firstVisiblePosition;
            this.firstTopOffset = firstTopOffset;
            this.stableKey = stableKey == null ? "" : stableKey;
        }
    }

    private static final class DataWindow {
        final int size;
        final long newestTimestamp;
        final long oldestTimestamp;

        DataWindow(int size, long newestTimestamp, long oldestTimestamp) {
            this.size = size;
            this.newestTimestamp = newestTimestamp;
            this.oldestTimestamp = oldestTimestamp;
        }
    }

    public static MessageListFragment newInstance(String convId, boolean isGroup, int unreadCount, boolean mentioned) {
        MessageListFragment f = new MessageListFragment();
        Bundle b = new Bundle();
        b.putString(ARG_CONV_ID, convId);
        b.putBoolean(ARG_IS_GROUP, isGroup);
        b.putInt(ARG_UNREAD_COUNT, unreadCount);
        b.putBoolean(ARG_MENTION, mentioned);
        f.setArguments(b);
        return f;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_message_list, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        if (getArguments() != null) {
            conversationId = getArguments().getString(ARG_CONV_ID);
            isGroup = getArguments().getBoolean(ARG_IS_GROUP, false);
            unreadCount = getArguments().getInt(ARG_UNREAD_COUNT, 0);
        }

        swipeRefreshLayout = view.findViewById(R.id.swipe_refresh_messages);
        recyclerView = view.findViewById(R.id.recycler_view_messages);
        layoutManager = new LinearLayoutManager(requireContext());
        recyclerView.setLayoutManager(layoutManager);
        RecyclerView.ItemAnimator animator = recyclerView.getItemAnimator();
        if (animator instanceof SimpleItemAnimator) {
            SimpleItemAnimator simpleItemAnimator = (SimpleItemAnimator) animator;
            simpleItemAnimator.setSupportsChangeAnimations(false);
            simpleItemAnimator.setChangeDuration(0);
        }
        adapter = new MessageListAdapter(isGroup, (message, action) -> {
            // handle message actions here on UI thread (position is adapter/display
            // position)
            requireActivity().runOnUiThread(() -> onMessageAction(message, action));
        }, (message, anchor, position) -> requireActivity()
                .runOnUiThread(() -> showMessageContextMenu(message, anchor, position)),
                new MessageListAdapter.OnAvatarInteractionListener() {
                    @Override
                    public void onAvatarClick(UiMessage message, String userId, String displayName) {
                        requireActivity().runOnUiThread(() -> openContactDetail(userId));
                    }

                    @Override
                    public void onAvatarLongClick(UiMessage message, String userId, String displayName) {
                        requireActivity().runOnUiThread(() -> insertMentionFromAvatar(userId, displayName));
                    }
                },
                message -> requireActivity().runOnUiThread(() -> resendFailedMessage(message)));
        adapter.setSelectionChangeListener(new MessageListAdapter.OnSelectionChangeListener() {
            @Override
            public void onSelectionModeChanged(boolean inSelectionMode) {
                selectionMode = inSelectionMode;
            }

            @Override
            public void onSelectionChanged(List<UiMessage> selectedMsg) {
                updateOptionBarState(selectedMsg.size());
            }
        });
        recyclerView.setAdapter(adapter);
        setupPullToRefresh();

        layoutUnreadBubble = view.findViewById(R.id.layout_unread_bubble);
        TextView tvUnread = view.findViewById(R.id.tv_unread_count);
        if (layoutUnreadBubble != null && tvUnread != null) {
            layoutUnreadBubble.setVisibility(GONE);
            if (unreadCount >= 6) {
                layoutUnreadBubble.setVisibility(VISIBLE);
                tvUnread.setText(getString(R.string.msg_new_count, unreadCount >= 99 ? "99+" : String.valueOf(unreadCount)));
                layoutUnreadBubble.animate().translationX(0).setDuration(320).start();
                layoutUnreadBubble.setOnClickListener(v -> {
                    Log.d(TAG, "[未读气泡] click, unreadCount=" + unreadCount);
                    hideUnreadBubble();
                    jumpToUnreadBoundary();
                });
            }
        }

        View mentionBubble = view.findViewById(R.id.layout_mention_bubble);
        TextView mentionText = view.findViewById(R.id.tv_mention);
        if (mentionBubble != null) {
            mentionBubble.setVisibility(GONE);
        }
        if (getArguments().getBoolean(ARG_MENTION, false)) {
            JIM.getInstance().getMessageManager().getMentionMessageList(
                    getCurrentConversation(),
                    5,
                    0,
                    JIMConst.PullDirection.OLDER,
                    new IMessageManager.IGetMessagesWithFinishCallback() {
                        @Override
                        public void onSuccess(List<Message> list, boolean b) {
                            updateMentionTargetFromList(list);
                            if (getActivity() == null) {
                                return;
                            }
                            getActivity().runOnUiThread(() -> {
                                if (mentionBubble == null || mentionText == null) {
                                    return;
                                }
                                if (mentionTargetTimestamp <= 0L) {
                                    mentionBubble.setVisibility(GONE);
                                    return;
                                }
                                mentionBubble.setVisibility(VISIBLE);
                                mentionText.setText(R.string.msg_mention_me);
                                mentionBubble.animate().translationX(0).setDuration(320).start();
                                mentionBubble.setOnClickListener(v -> {
                                    mentionBubble.setVisibility(GONE);
                                    if (mentionTargetTimestamp > 0L) {
                                        loadAroundTimestamp(mentionTargetTimestamp, mentionTargetMessageId);
                                    }
                                });
                            });
                        }

                        @Override
                        public void onError(int i) {
                        }
                    });
        }
        // New Message Bubble Initialization
        layoutNewMessageBubble = view.findViewById(R.id.layout_new_message_bubble);
        tvNewMessageCount = view.findViewById(R.id.tv_new_message_count);
        layoutTopLoading = view.findViewById(R.id.layout_loading_top);
        layoutBottomLoading = view.findViewById(R.id.layout_loading_bottom);
        if (layoutTopLoading != null) {
            layoutTopLoading.setVisibility(GONE);
        }
        if (layoutBottomLoading != null) {
            layoutBottomLoading.setVisibility(GONE);
        }
        if (layoutNewMessageBubble != null) {
            layoutNewMessageBubble.setOnClickListener(v -> {
                layoutNewMessageBubble.setVisibility(GONE);
                newMessageCount = 0;
                scrollToBottomIfNeeded();
            });
        }

        // Ensure RecyclerView preserves space for the input bar by default so messages
        // are not hidden
        int left = recyclerView.getPaddingLeft();
        int top = recyclerView.getPaddingTop();
        int right = recyclerView.getPaddingRight();
        int bottom = getResources().getDimensionPixelSize(com.juggle.im.android.R.dimen.chat_input_height);
        recyclerView.setPadding(left, top, right, bottom);
        recyclerView.setClipToPadding(false);

        // track scroll position to know if we're at bottom; also collapse input panels
        // when user scrolls
        recyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView rv, int dx, int dy) {
                super.onScrolled(rv, dx, dy);
                // 使用 findLastVisibleItemPosition 判断是否在底部，比 canScrollVertically 更宽松
                // 只要最后一个item可见，就认为在底部
                int lastVisiblePos = layoutManager.findLastVisibleItemPosition();
                int itemCount = layoutManager.getItemCount();
                
                // 如果最后一个可见item是列表的最后一个(或倒数第二个，容错)，认为在底部
                boolean nowAtBottom = (lastVisiblePos >= itemCount - 1) || !recyclerView.canScrollVertically(1);
                
                // Add logging to debug scroll behavior
                if (atBottom != nowAtBottom) {
                    Log.d("MessageListFragment", "atBottom changed: " + atBottom + " -> " + nowAtBottom + 
                          " (lastVis=" + lastVisiblePos + ", count=" + itemCount + ")");
                }
                
                atBottom = nowAtBottom;
                if (isMessageContextVisible()) {
                    dismissMessageContextMenu();
                }
                maybeDismissUnreadBubbleWhenTargetVisible();
                if (atBottom) {
                    if (layoutNewMessageBubble != null && layoutNewMessageBubble.getVisibility() == VISIBLE) {
                        layoutNewMessageBubble.setVisibility(GONE);
                        newMessageCount = 0;
                    }
                }
                if (dy > 0 && itemCount > 0 && lastVisiblePos >= itemCount - 1) {
                    loadNewerMessages(DEFAULT_PAGE_COUNT, null);
                }
            }

            @Override
            public void onScrollStateChanged(@NonNull RecyclerView recyclerView, int newState) {
                super.onScrollStateChanged(recyclerView, newState);

                // If user scrolls (dy != 0) and an input panel is visible, collapse it
                if (getActivity() != null && newState == RecyclerView.SCROLL_STATE_DRAGGING) {
                    ConversationActivity act = (ConversationActivity) getActivity();
                    ChatInputActionBar input = act.findViewById(R.id.input_bar);
                    if (input != null && input.isPanelVisible()) {
                        input.collapsePanel();
                    }
                }
            }
        });

        // submit empty list initially
        adapter.submitList(new ArrayList<UiMessage>());

        // find selection UI
        selectionOptionBar = getActivity().findViewById(R.id.selection_option_bar);
        btnForwardSelected = getActivity().findViewById(R.id.btn_forward_selected);
        btnDeleteSelected = getActivity().findViewById(R.id.btn_delete_selected);
        overlayForwardContainer = getActivity().findViewById(R.id.overlay_forward_container);
        overlayMessageContextContainer = getActivity().findViewById(R.id.overlay_message_context_container);
        initMessageContextOverlay();

        // option bar actions
        if (btnForwardSelected != null) {
            btnForwardSelected.setOnClickListener(v -> {
                if (adapter.getSelectionCount() > 0) {
                    // show forward overlay menu
                    showForwardMenu();
                }
            });
        }
        if (btnDeleteSelected != null) {
            btnDeleteSelected.setOnClickListener(v -> {
                if (adapter.getSelectionCount() > 0) {
                    // delete selected messages from current list
                    List<UiMessage> current = new ArrayList<>(adapter.getCurrentList());
                    Iterator<UiMessage> it = current.iterator();
                    List<UiMessage> willDelete = new ArrayList<>();
                    while (it.hasNext()) {
                        UiMessage um = it.next();
                        if (um.getMessageId() != null && selectedIds.contains(um.getMessageId())) {
                            it.remove();
                            willDelete.add(um);
                        }
                    }
                    selectedIds.clear();
                    deleteMessages(willDelete, current);
                    updateOptionBarState(selectedIds.size());
                }
            });
        }

        // initial load: msgTime = 0 -> SDK should return latest page
        loadOlderMessages(INITIAL_PAGE_COUNT, false, null);
    }

    private void updateOptionBarState(int selectedCount) {
        if (selectionOptionBar == null || btnForwardSelected == null || btnDeleteSelected == null)
            return;
        if (selectedCount > 0) {
            btnForwardSelected.setAlpha(1f);
            btnDeleteSelected.setAlpha(1f);
            btnForwardSelected.setEnabled(true);
            btnDeleteSelected.setEnabled(true);
        } else {
            btnForwardSelected.setAlpha(0.4f);
            btnDeleteSelected.setAlpha(0.4f);
            btnForwardSelected.setEnabled(false);
            btnDeleteSelected.setEnabled(false);
        }
        // update title
        if (getActivity() != null) {
            TextView tv = getActivity().findViewById(R.id.tv_title);
            ImageView ivBack = getActivity().findViewById(R.id.iv_back);
            if (selectionMode) {
                if (ivBack != null) {
                    ivBack.setImageResource(R.drawable.ic_back);
                    ivBack.setOnClickListener(v -> exitSelectionMode());
                }
                if (tv != null) {
                    tv.setText(getString(R.string.msg_selected_count, selectedCount));
                }
                getActivity().findViewById(R.id.iv_settings).setVisibility(GONE);
            } else {
                if (ivBack != null) {
                    ivBack.setImageResource(R.drawable.ic_back);
                    ivBack.setOnClickListener(v -> requireActivity().finish());
                }
            }
        }
    }

    private void showSelectionUi() {
        if (selectionOptionBar != null)
            selectionOptionBar.setVisibility(VISIBLE);
        // hide input bar
        ConversationActivity act = (ConversationActivity) getActivity();
        if (act != null) {
            ChatInputActionBar input = act.findViewById(R.id.input_bar);
            if (input != null)
                input.setVisibility(GONE);
        }
    }

    private void hideSelectionUi() {
        if (selectionOptionBar != null)
            selectionOptionBar.setVisibility(GONE);
        dismissMessageContextMenu();
        // show input bar
        ConversationActivity act = (ConversationActivity) getActivity();
        if (act != null) {
            ChatInputActionBar input = act.findViewById(R.id.input_bar);
            if (input != null)
                input.setVisibility(VISIBLE);
        }
        // reset title/back
        updateOptionBarState(0);
    }

    private void enterSelectionMode(UiMessage initial) {
        if (initial == null)
            return;
        selectedIds.clear();
        if (initial.getMessageId() != null)
            selectedIds.add(initial.getMessageId());
        adapter.enterSelectionMode(initial);
        showSelectionUi();
        updateOptionBarState(selectedIds.size());
    }

    private void exitSelectionMode() {
        selectedIds.clear();
        adapter.exitSelectionMode();
        hideSelectionUi();
        // restore title from intent
        if (getActivity() != null) {
            TextView tv = getActivity().findViewById(R.id.tv_title);
            String title = getActivity().getIntent().getStringExtra(EXTRA_TITLE);
            if (tv != null && title != null)
                tv.setText(title);
            getActivity().findViewById(R.id.iv_settings).setVisibility(VISIBLE);
        }
    }

    private void initMessageContextOverlay() {
        if (overlayMessageContextContainer == null || getActivity() == null) {
            return;
        }
        overlayMessageContextContainer.removeAllViews();
        overlayMessageContextContainer.setClickable(true);
        overlayMessageContextContainer.setFocusable(true);
        messageContextOverlayView = new View(requireContext());
        messageContextOverlayView.setClickable(true);
        messageContextOverlayView.setFocusable(true);
        messageContextOverlayView.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        messageContextOverlayView.setBackgroundColor(0x4D0F172A);
        messageContextOverlayView.setOnClickListener(v -> dismissMessageContextMenu());
        overlayMessageContextContainer.addView(messageContextOverlayView);

        messageContextPopupView = LayoutInflater.from(requireContext())
                .inflate(R.layout.layout_message_popup, overlayMessageContextContainer, false);
        messageContextPopupView.setClickable(true);
        messageContextPopupView.setFocusable(true);
        FrameLayout.LayoutParams popupLayoutParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        messageContextPopupView.setLayoutParams(popupLayoutParams);
        messageContextPopupView.setOnClickListener(v -> {
        });
        messageContextPopupView.setVisibility(GONE);
        pinnedMessageContainer = messageContextPopupView.findViewById(R.id.pinned_message_container);
        overlayMessageContextContainer.addView(messageContextPopupView);
        overlayMessageContextContainer.setVisibility(GONE);
    }

    /**
     * 展示消息长按浮层。
     *
     * <p>tips：浮层中的中间消息使用镜像渲染，原列表 cell 只做透明占位，避免 RecyclerView 因摘取 View 导致复用错乱。</p>
     *
     * @param message 被长按消息
     * @param anchor 锚点 view
     * @param position 当前列表位置
     */
    private void showMessageContextMenu(@NonNull UiMessage message, @NonNull View anchor, int position) {
        if (getActivity() == null || overlayForwardContainer == null || overlayMessageContextContainer == null
                || messageContextPopupView == null || pinnedMessageContainer == null) {
            return;
        }
        if (selectionMode) {
            return;
        }
        dismissMessageContextMenu();
        contextPinnedMessage = message;
        contextPinnedMessageId = message.getMessageId();
        adapter.setContextPinnedMessageId(contextPinnedMessageId);
        pinnedMessageContainer.removeAllViews();
        pinnedMessageContainer.addView(adapter.createContextPinnedMessageView(pinnedMessageContainer, message));
        adapter.bindContextMenu(messageContextPopupView, message);

        overlayForwardContainer.setVisibility(VISIBLE);
        overlayForwardContainer.bringToFront();
        overlayForwardContainer.setClickable(true);
        overlayForwardContainer.setFocusable(true);
        overlayMessageContextContainer.setVisibility(VISIBLE);
        overlayMessageContextContainer.bringToFront();
        overlayMessageContextContainer.setOnTouchListener((v, event) -> handleMessageContextTouch(event));
        messageContextPopupView.setVisibility(INVISIBLE);
        applyMessageContextBackgroundEffect(true);
        messageContextPopupView.post(() -> positionMessageContextPopup(anchor));
    }

    private void positionMessageContextPopup(@NonNull View anchor) {
        if (messageContextPopupView == null || overlayMessageContextContainer == null) {
            return;
        }
        int[] anchorLocation = new int[2];
        int[] overlayLocation = new int[2];
        anchor.getLocationOnScreen(anchorLocation);
        overlayMessageContextContainer.getLocationOnScreen(overlayLocation);
        Rect anchorRect = new Rect(
                anchorLocation[0] - overlayLocation[0],
                anchorLocation[1] - overlayLocation[1],
                anchorLocation[0] - overlayLocation[0] + anchor.getWidth(),
                anchorLocation[1] - overlayLocation[1] + anchor.getHeight());

        int popupHeight = messageContextPopupView.getMeasuredHeight();
        int overlayWidth = overlayMessageContextContainer.getWidth();
        int overlayHeight = overlayMessageContextContainer.getHeight();
        int horizontalMargin = dp(10);
        int verticalMargin = dp(8);
        int targetWidth = Math.max(0, overlayWidth - horizontalMargin * 2);

        int left = horizontalMargin;

        int top = anchorRect.top - messageContextPopupView.findViewById(R.id.layout_reaction_bar).getMeasuredHeight() - verticalMargin;
        int minTop = verticalMargin;
        int maxTop = Math.max(verticalMargin, overlayHeight - popupHeight - verticalMargin);
        top = Math.max(minTop, Math.min(top, maxTop));

        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) messageContextPopupView.getLayoutParams();
        lp.width = targetWidth;
        lp.height = ViewGroup.LayoutParams.WRAP_CONTENT;
        lp.leftMargin = left;
        lp.topMargin = top;
        messageContextPopupView.setLayoutParams(lp);
        messageContextPopupView.setVisibility(VISIBLE);
    }

    private void dismissMessageContextMenu() {
        if (!isMessageContextVisible()) {
            contextPinnedMessage = null;
            contextPinnedMessageId = "";
            if (adapter != null) {
                adapter.setContextPinnedMessageId("");
            }
            return;
        }
        contextPinnedMessage = null;
        contextPinnedMessageId = "";
        if (adapter != null) {
            adapter.setContextPinnedMessageId("");
        }
        if (pinnedMessageContainer != null) {
            pinnedMessageContainer.removeAllViews();
        }
        if (messageContextPopupView != null) {
            messageContextPopupView.setVisibility(GONE);
        }
        if (overlayMessageContextContainer != null) {
            overlayMessageContextContainer.setOnTouchListener(null);
            overlayMessageContextContainer.setVisibility(GONE);
        }
        if (overlayForwardContainer != null && !selectionMode) {
            overlayForwardContainer.setVisibility(GONE);
            overlayForwardContainer.setClickable(false);
            overlayForwardContainer.setFocusable(false);
        }
        applyMessageContextBackgroundEffect(false);
    }

    private boolean isMessageContextVisible() {
        return overlayMessageContextContainer != null && overlayMessageContextContainer.getVisibility() == VISIBLE;
    }

    private void applyMessageContextBackgroundEffect(boolean active) {
        if (recyclerView != null) {
            recyclerView.setAlpha(active ? 0.35f : 1f);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                recyclerView.setRenderEffect(active
                        ? android.graphics.RenderEffect.createBlurEffect(6f, 6f, android.graphics.Shader.TileMode.CLAMP)
                        : null);
            }
        }
        if (messageContextOverlayView == null) {
            return;
        }
        if (!active) {
            if (getActivity() != null) {
                Window window = getActivity().getWindow();
                if (window != null) {
                    window.setStatusBarColor(requireContext().getColor(R.color.white));
                    window.setNavigationBarColor(requireContext().getColor(R.color.input_bg_light));
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        WindowInsetsController controller = window.getInsetsController();
                        if (controller != null) {
                            controller.setSystemBarsAppearance(
                                    WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
                                            | WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS,
                                    WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
                                            | WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS);
                        }
                    }
                }
            }
            messageContextOverlayView.setAlpha(1f);
            messageContextOverlayView.setBackgroundColor(0x730F172A);
            FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) messageContextOverlayView.getLayoutParams();
            lp.topMargin = 0;
            lp.height = ViewGroup.LayoutParams.MATCH_PARENT;
            messageContextOverlayView.setLayoutParams(lp);
            return;
        }
        messageContextOverlayView.setAlpha(1f);
        messageContextOverlayView.setBackgroundColor(0x730F172A);
        if (getActivity() != null) {
            Window window = getActivity().getWindow();
            if (window != null) {
                window.setStatusBarColor(0x730F172A);
                window.setNavigationBarColor(0x730F172A);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    window.setNavigationBarContrastEnforced(false);
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    WindowInsetsController controller = window.getInsetsController();
                    if (controller != null) {
                        controller.setSystemBarsAppearance(
                                0,
                                WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
                                        | WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS);
                    }
                }
                View decorView = window.getDecorView();
                messageContextOverlayView.post(() -> {
                    int[] decorLocation = new int[2];
                    int[] overlayLocation = new int[2];
                    decorView.getLocationOnScreen(decorLocation);
                    overlayMessageContextContainer.getLocationOnScreen(overlayLocation);
                    int decorHeight = decorView.getHeight();
                    int topInset = Math.max(0, overlayLocation[1] - decorLocation[1]);
                    FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) messageContextOverlayView.getLayoutParams();
                    lp.topMargin = -topInset;
                    lp.height = decorHeight;
                    messageContextOverlayView.setLayoutParams(lp);
                });
            }
        }
    }

    private int dp(int value) {
        return Math.round(value * requireContext().getResources().getDisplayMetrics().density);
    }

    private boolean handleMessageContextTouch(@NonNull MotionEvent event) {
        if (!isMessageContextVisible()) {
            return false;
        }
        if (event.getAction() == MotionEvent.ACTION_DOWN && messageContextPopupView != null) {
            float x = event.getX();
            float y = event.getY();
            if (x < messageContextPopupView.getLeft() || x > messageContextPopupView.getRight()
                    || y < messageContextPopupView.getTop() || y > messageContextPopupView.getBottom()) {
                dismissMessageContextMenu();
            }
            return true;
        }
        return true;
    }

    private void showForwardMenu() {
        BottomActionSheet.builder(requireContext())
                .addItem(getString(R.string.forward_single_message), () -> {
                    if (getActivity() != null) {
                        Intent it = ForwardConversationListActivity.createIntent(getActivity(), "single");
                        getActivity().startActivityForResult(it, ConversationActivity.REQ_FORWARD);
                    }
                })
                .addItem(getString(R.string.forward_merge_msg), () -> {
                    if (getActivity() != null) {
                        Intent it = ForwardConversationListActivity.createIntent(getActivity(), "merge");
                        getActivity().startActivityForResult(it, ConversationActivity.REQ_FORWARD);
                    }
                })
                .show();
    }


    /**
     * Return currently selected messages (used by ConversationActivity when
     * handling forward result).
     */
    public List<UiMessage> getSelectedMessagesForForward() {
        if (adapter == null)
            return new ArrayList<>();
        return adapter.getSelectedMessages();
    }

    /**
     * Clear selection UI after a forward operation completes.
     */
    public void clearSelectionAfterForward() {
        selectedIds.clear();
        if (adapter != null)
            adapter.exitSelectionMode();
        hideSelectionUi();
    }

    public void insertMention(ArrayList<String> userIds, ArrayList<String> userNames) {
        ChatInputActionBar input = getActivity().findViewById(R.id.input_bar);
        if (input == null)
            return;
        input.insertMention(userIds, userNames);
    }

    /**
     * 跳转联系人详情页。
     *
     * @param userId 目标用户 ID
     */
    private void openContactDetail(@Nullable String userId) {
        if (getContext() == null || TextUtils.isEmpty(userId)) {
            return;
        }
        Intent intent = new Intent(requireContext(), ContactDetailActivity.class);
        intent.putExtra(ContactDetailActivity.EXTRA_USER_ID, userId);
        startActivity(intent);
    }

    /**
     * 通过头像长按插入 @ 提及。
     *
     * @param userId 目标用户 ID
     * @param displayName 目标用户展示名
     */
    private void insertMentionFromAvatar(@Nullable String userId, @Nullable String displayName) {
        if (!isGroup || TextUtils.isEmpty(userId) || TextUtils.isEmpty(displayName)) {
            return;
        }
        ChatInputActionBar input = getActivity().findViewById(R.id.input_bar);
        if (input == null) {
            return;
        }
        ArrayList<String> userIds = new ArrayList<>();
        userIds.add(userId);
        ArrayList<String> userNames = new ArrayList<>();
        userNames.add(displayName);
        input.insertMention(userIds, userNames);
    }

    @Override
    public void showKeyboardIfNeed() {
        ChatInputActionBar input = getActivity().findViewById(R.id.input_bar);
        if (input != null) {
            input.showKeyboardIfNeed();
        }
    }

    /**
     * 滚动到指定消息并高亮。
     *
     * <p>tips：优先使用 messageId 精确命中；若当前列表未命中，则按时间戳重新拉取附近消息，保证置顶消息可点击跳转。</p>
     *
     * @param messageId          目标消息 ID
     * @param fallbackTimestamp  目标消息时间戳
     */
    @Override
    public void scrollToMessage(@Nullable String messageId, long fallbackTimestamp) {
        if (adapter == null) {
            return;
        }
        List<UiMessage> display = adapter.getCurrentList();
        int target = findTargetPosition(display, messageId, fallbackTimestamp);
        if (target >= 0) {
            highlightTargetMessage(display, target);
            return;
        }
        if (fallbackTimestamp > 0L) {
            loadAroundTimestamp(fallbackTimestamp, messageId);
        }
    }

    private Conversation getCurrentConversation() {
        return new Conversation(
                isGroup ? Conversation.ConversationType.GROUP : Conversation.ConversationType.PRIVATE,
                conversationId);
    }

    /**
     * 初始化下拉刷新。
     *
     * <p>简要描述：仅在消息列表滚动到顶部时允许触发刷新，刷新中保持 progress，并阻止重复触发。</p>
     */
    private void setupPullToRefresh() {
        if (swipeRefreshLayout == null) {
            return;
        }
        swipeRefreshLayout.setColorSchemeResources(R.color.app_primary);
        swipeRefreshLayout.setProgressBackgroundColorSchemeResource(R.color.white);
        swipeRefreshLayout.setOnChildScrollUpCallback((parent, child) -> recyclerView != null
                && recyclerView.canScrollVertically(-1));
        swipeRefreshLayout.setOnRefreshListener(this::onPullToRefresh);
    }

    /**
     * 响应用户下拉刷新请求并加载历史消息。
     *
     * <p>简要描述：当刷新已在进行中时直接忽略本次触发，确保同一时刻只存在一个刷新任务。</p>
     */
    private void onPullToRefresh() {
        if (swipeRefreshLayout == null) {
            return;
        }
        if (isLoadingOlder) {
            swipeRefreshLayout.setRefreshing(true);
            return;
        }
        if (!hasMoreOlder) {
            swipeRefreshLayout.setRefreshing(false);
            return;
        }
        pullRefreshAnchor = captureViewportAnchor();
        swipeRefreshLayout.setRefreshing(true);
        loadOlderMessages(PULL_REFRESH_PAGE_COUNT, false, () -> {
            setPullRefreshing(false);
            restorePullRefreshAnchorIfNeed();
        }, false);
    }

    /**
     * 更新下拉刷新 progress 状态。
     *
     * @param refreshing true 表示显示 progress，false 表示隐藏 progress
     */
    private void setPullRefreshing(boolean refreshing) {
        if (swipeRefreshLayout == null) {
            return;
        }
        swipeRefreshLayout.setRefreshing(refreshing);
    }

    /**
     * 恢复下拉刷新前的可视锚点。
     *
     * <p>简要描述：以“刷新开始时”记录的锚点作为唯一恢复基准，避免刷新结束时错误取到顶部项导致跳到最旧消息。</p>
     */
    private void restorePullRefreshAnchorIfNeed() {
        if (pullRefreshAnchor == null || recyclerView == null || adapter == null) {
            pullRefreshAnchor = null;
            return;
        }
        ViewportAnchor anchor = pullRefreshAnchor;
        pullRefreshAnchor = null;
        recyclerView.post(() -> {
            restoreViewportAnchor(anchor, adapter.getCurrentList());
            recyclerView.post(() -> restoreViewportAnchor(anchor, adapter.getCurrentList()));
        });
    }

    private void updateLoadingIndicator(@NonNull JIMConst.PullDirection direction, boolean loading) {
        View target = direction == JIMConst.PullDirection.OLDER ? layoutTopLoading : layoutBottomLoading;
        if (target == null) {
            return;
        }
        if (loading) {
            if (target.getVisibility() == VISIBLE) {
                return;
            }
            target.setAlpha(0f);
            target.setVisibility(VISIBLE);
            target.animate().alpha(1f).setDuration(160L).start();
            return;
        }
        if (target.getVisibility() == GONE) {
            return;
        }
        target.animate().cancel();
        target.setAlpha(1f);
        target.setVisibility(GONE);
    }

    private void loadOlderMessages(int count, boolean scrollTop, @Nullable Runnable onDone) {
        loadOlderMessages(count, scrollTop, onDone, true);
    }

    /**
     * 加载更早历史消息。
     *
     * @param count 加载条数
     * @param scrollTop 是否强制滚动到顶部
     * @param onDone 加载结束回调
     * @param showTopLoading 是否显示顶部浮层 loading（下拉刷新时由 SwipeRefreshLayout 承担 progress）
     */
    private void loadOlderMessages(int count, boolean scrollTop, @Nullable Runnable onDone, boolean showTopLoading) {
        long cursor = 0L;
        if (!uiMessages.isEmpty()) {
            cursor = uiMessages.get(uiMessages.size() - 1).getTimestamp();
        }
        loadMessages(count, cursor, JIMConst.PullDirection.OLDER, scrollTop, showTopLoading, onDone);
    }

    private void loadNewerMessages(int count, @Nullable Runnable onDone) {
        long cursor = 0L;
        if (!uiMessages.isEmpty()) {
            cursor = uiMessages.get(0).getTimestamp();
        }
        loadMessages(count, cursor, JIMConst.PullDirection.NEWER, false, true, onDone);
    }

    private void loadAroundTimestamp(long targetTimestamp, @Nullable String targetMessageId) {
        if (targetTimestamp <= 0L) {
            return;
        }
        uiMessages.clear();
        hasMoreOlder = true;
        hasMoreNewer = true;
        adapter.submitList(new ArrayList<>(), () -> loadMessages(
                DEFAULT_PAGE_COUNT,
                targetTimestamp,
                JIMConst.PullDirection.OLDER,
                false,
                true,
                () -> loadMessages(
                        DEFAULT_PAGE_COUNT,
                        targetTimestamp,
                        JIMConst.PullDirection.NEWER,
                        false,
                        true,
                        () -> scrollToTargetMessage(targetMessageId, targetTimestamp))));
    }

    private void loadMessages(int count, long cursor, JIMConst.PullDirection direction, boolean scrollTop,
            boolean showLoadingIndicator, @Nullable Runnable onDone) {
        if (count <= 0) {
            if (onDone != null) {
                onDone.run();
            }
            return;
        }
        if (direction == JIMConst.PullDirection.OLDER) {
            if (isLoadingOlder || !hasMoreOlder) {
                if (onDone != null) {
                    onDone.run();
                }
                return;
            }
            isLoadingOlder = true;
        } else {
            if (isLoadingNewer || !hasMoreNewer) {
                if (onDone != null) {
                    onDone.run();
                }
                return;
            }
            if (cursor <= 0L && !uiMessages.isEmpty()) {
                if (onDone != null) {
                    onDone.run();
                }
                return;
            }
            isLoadingNewer = true;
        }
        if (showLoadingIndicator) {
            updateLoadingIndicator(direction, true);
        }

        final long requestStartAt = System.currentTimeMillis();
        final String requestId = requestStartAt + "_" + direction.name();
        final DataWindow requestWindow = captureDataWindow();
        Log.d(TAG, "[分页拉取-请求] id=" + requestId
                + ", dir=" + direction.name()
                + ", count=" + count
                + ", cursor=" + cursor
                + ", scrollTop=" + scrollTop
                + ", showLoading=" + showLoadingIndicator
                + ", unreadJump=" + unreadJumpInProgress
                + ", window=" + formatDataWindow(requestWindow));

        ViewportAnchor anchor = captureViewportAnchor();
        GetMessageOptions options = new GetMessageOptions();
        options.setCount(count);
        options.setStartTime(Math.max(cursor, 0L));

        JIM.getInstance().getMessageManager().getMessages(
                getCurrentConversation(),
                direction,
                options,
                (list, timestamp, hasMore, code) -> {
                    long costMs = System.currentTimeMillis() - requestStartAt;
                    if (getActivity() == null) {
                        Log.d(TAG, "[分页拉取-丢弃] id=" + requestId
                                + ", reason=activity_null"
                                + ", dir=" + direction.name()
                                + ", costMs=" + costMs
                                + ", code=" + code
                                + ", hasMore=" + hasMore);
                        if (direction == JIMConst.PullDirection.OLDER) {
                            isLoadingOlder = false;
                        } else {
                            isLoadingNewer = false;
                        }
                        return;
                    }
                    getActivity().runOnUiThread(() -> {
                        if (direction == JIMConst.PullDirection.OLDER) {
                            isLoadingOlder = false;
                            hasMoreOlder = hasMore;
                            // 简要描述：兜底关闭非下拉场景残留的 SwipeRefresh progress，避免转圈常驻。
                            if (onDone == null) {
                                setPullRefreshing(false);
                            }
                        } else {
                            isLoadingNewer = false;
                            hasMoreNewer = hasMore;
                        }
                        if (showLoadingIndicator) {
                            updateLoadingIndicator(direction, false);
                        }

                        DataWindow beforeMergeWindow = captureDataWindow();
                        List<UiMessage> incoming = mapToUiMessages(list);
                        String incomingRange = formatIncomingRange(incoming);
                        if (!incoming.isEmpty()) {
                            setMessageRead(incoming);
                        }
                        boolean changed = upsertUiMessages(incoming);
                        DataWindow afterMergeWindow = captureDataWindow();
                        Log.d(TAG, "[分页拉取-结果] id=" + requestId
                                + ", dir=" + direction.name()
                                + ", costMs=" + costMs
                                + ", code=" + code
                                + ", hasMore=" + hasMore
                                + ", sdkTimestamp=" + timestamp
                                + ", incomingCount=" + incoming.size()
                                + ", incomingRange=" + incomingRange
                                + ", requestWindow=" + formatDataWindow(requestWindow)
                                + ", beforeMergeWindow=" + formatDataWindow(beforeMergeWindow)
                                + ", afterMergeWindow=" + formatDataWindow(afterMergeWindow)
                                + ", changed=" + changed
                                + ", continuity=" + buildContinuityEvidence(
                                        direction, cursor, incoming, beforeMergeWindow, afterMergeWindow, changed));
                        if (!changed) {
                            if (onDone != null) {
                                onDone.run();
                            }
                            return;
                        }

                        List<UiMessage> displayWithTimes = buildDisplayMessages();
                        boolean firstScreen = adapter.getCurrentList().isEmpty()
                                && direction == JIMConst.PullDirection.OLDER
                                && cursor <= 0L;
                        adapter.submitList(displayWithTimes, () -> {
                            if (scrollTop) {
                                layoutManager.scrollToPositionWithOffset(0, 0);
                            } else if (firstScreen) {
                                scrollToBottomIfNeeded();
                            } else if (direction == JIMConst.PullDirection.NEWER && atBottom) {
                                int target = Math.max(adapter.getItemCount() - 1, 0);
                                recyclerView.scrollToPosition(target);
                            } else {
                                restoreViewportAnchor(anchor, displayWithTimes);
                            }
                            maybeDismissUnreadBubbleWhenTargetVisible();
                            if (onDone != null) {
                                onDone.run();
                            }
                        });
                    });
                });
    }

    private ViewportAnchor captureViewportAnchor() {
        if (layoutManager == null || adapter == null) {
            return null;
        }
        int first = layoutManager.findFirstVisibleItemPosition();
        if (first < 0) {
            return null;
        }
        List<UiMessage> current = adapter.getCurrentList();
        int last = layoutManager.findLastVisibleItemPosition();
        int anchorPos = first;
        /**
         * 简要描述：时间分割项是临时构建的 UI 消息，稳定 key 在刷新后可能变化；
         * 锚点优先使用首个可见“真实消息”，可避免恢复时丢锚造成页面偏移。
         */
        for (int i = Math.max(first, 0); i <= last && i < current.size(); i++) {
            UiMessage candidate = current.get(i);
            if (!isTimeStatus(candidate)) {
                anchorPos = i;
                break;
            }
        }
        View anchorView = layoutManager.findViewByPosition(anchorPos);
        if (anchorView == null) {
            anchorPos = first;
            anchorView = layoutManager.findViewByPosition(anchorPos);
        }
        int topOffset = anchorView == null ? 0 : (anchorView.getTop() - recyclerView.getPaddingTop());
        String stableKey = "";
        if (anchorPos >= 0 && anchorPos < current.size()) {
            stableKey = current.get(anchorPos).getStableKey();
        }
        return new ViewportAnchor(anchorPos, topOffset, stableKey);
    }

    private void restoreViewportAnchor(@Nullable ViewportAnchor anchor, @NonNull List<UiMessage> newDisplay) {
        if (anchor == null || newDisplay.isEmpty()) {
            return;
        }
        int target = -1;
        if (!anchor.stableKey.isEmpty()) {
            for (int i = 0; i < newDisplay.size(); i++) {
                if (anchor.stableKey.equals(newDisplay.get(i).getStableKey())) {
                    target = i;
                    break;
                }
            }
        }
        if (target < 0) {
            target = Math.min(anchor.firstVisiblePosition, newDisplay.size() - 1);
        }
        if (target >= 0) {
            layoutManager.scrollToPositionWithOffset(target, anchor.firstTopOffset);
        }
    }

    private List<UiMessage> buildDisplayMessages() {
        List<UiMessage> oldestFirst = new ArrayList<>(uiMessages);
        Collections.reverse(oldestFirst);
        List<UiMessage> displayWithTimes = new ArrayList<>();
        UiMessage prev = null;
        for (UiMessage cur : oldestFirst) {
            if (MessageUtils.shouldInsertTimeBefore(prev, cur)) {
                UiMessage timeMsg = MessageUtils.createInsertTimeUiMessage(cur.getTimestamp());
                if (timeMsg != null) {
                    displayWithTimes.add(timeMsg);
                }
            }
            displayWithTimes.add(cur);
            prev = cur;
        }
        return displayWithTimes;
    }

    private List<UiMessage> mapToUiMessages(@Nullable List<Message> list) {
        List<UiMessage> mapped = new ArrayList<>();
        if (list == null || list.isEmpty()) {
            return mapped;
        }
        for (Message message : list) {
            UiMessage uiMessage = UiMessage.fromMessage(message);
            if (uiMessage != null) {
                mapped.add(uiMessage);
            }
        }
        mapped.sort((a, b) -> Long.compare(b.getTimestamp(), a.getTimestamp()));
        return mapped;
    }

    /**
     * 采集当前消息窗口边界（仅真实消息，不含时间分割项）。
     */
    @NonNull
    private DataWindow captureDataWindow() {
        if (uiMessages.isEmpty()) {
            return new DataWindow(0, 0L, 0L);
        }
        long newest = uiMessages.get(0).getTimestamp();
        long oldest = uiMessages.get(uiMessages.size() - 1).getTimestamp();
        return new DataWindow(uiMessages.size(), newest, oldest);
    }

    /**
     * 格式化窗口日志。
     *
     * @param window 窗口快照
     * @return 日志字符串
     */
    @NonNull
    private String formatDataWindow(@Nullable DataWindow window) {
        if (window == null) {
            return "{size=0,newest=0,oldest=0}";
        }
        return "{size=" + window.size
                + ",newest=" + window.newestTimestamp
                + ",oldest=" + window.oldestTimestamp + "}";
    }

    /**
     * 格式化本次返回消息边界日志。
     *
     * @param incoming SDK 返回并转换后的消息
     * @return 日志字符串
     */
    @NonNull
    private String formatIncomingRange(@NonNull List<UiMessage> incoming) {
        if (incoming.isEmpty()) {
            return "{count=0}";
        }
        long newest = incoming.get(0).getTimestamp();
        long oldest = incoming.get(incoming.size() - 1).getTimestamp();
        return "{count=" + incoming.size() + ",newest=" + newest + ",oldest=" + oldest + "}";
    }

    /**
     * 构建分页连续性校验证据。
     *
     * <p>简要描述：OLDER 校验返回上界是否不大于 cursor；NEWER 校验返回下界是否不小于 cursor，并同步输出合并前后窗口边界。</p>
     *
     * @param direction 拉取方向
     * @param cursor 本次请求游标
     * @param incoming 本次拉取消息
     * @param beforeMerge 合并前窗口
     * @param afterMerge 合并后窗口
     * @param changed 本次是否写入了新数据
     * @return 连续性日志证据
     */
    @NonNull
    private String buildContinuityEvidence(@NonNull JIMConst.PullDirection direction,
            long cursor,
            @NonNull List<UiMessage> incoming,
            @NonNull DataWindow beforeMerge,
            @NonNull DataWindow afterMerge,
            boolean changed) {
        if (incoming.isEmpty()) {
            return "skip(empty_incoming)";
        }
        long incomingNewest = incoming.get(0).getTimestamp();
        long incomingOldest = incoming.get(incoming.size() - 1).getTimestamp();
        if (direction == JIMConst.PullDirection.OLDER) {
            boolean cursorAligned = cursor <= 0L || incomingNewest <= cursor;
            boolean boundaryExtendedOrKeep = beforeMerge.size == 0
                    || afterMerge.oldestTimestamp <= beforeMerge.oldestTimestamp;
            return "older{cursorAligned=" + cursorAligned
                    + ",incomingNewest=" + incomingNewest
                    + ",incomingOldest=" + incomingOldest
                    + ",cursor=" + cursor
                    + ",oldestBefore=" + beforeMerge.oldestTimestamp
                    + ",oldestAfter=" + afterMerge.oldestTimestamp
                    + ",boundaryExtendedOrKeep=" + boundaryExtendedOrKeep
                    + ",changed=" + changed + "}";
        }
        boolean cursorAligned = cursor <= 0L || incomingOldest >= cursor;
        boolean boundaryExtendedOrKeep = beforeMerge.size == 0
                || afterMerge.newestTimestamp >= beforeMerge.newestTimestamp;
        return "newer{cursorAligned=" + cursorAligned
                + ",incomingNewest=" + incomingNewest
                + ",incomingOldest=" + incomingOldest
                + ",cursor=" + cursor
                + ",newestBefore=" + beforeMerge.newestTimestamp
                + ",newestAfter=" + afterMerge.newestTimestamp
                + ",boundaryExtendedOrKeep=" + boundaryExtendedOrKeep
                + ",changed=" + changed + "}";
    }

    private boolean upsertUiMessages(@Nullable List<UiMessage> incoming) {
        if (incoming == null || incoming.isEmpty()) {
            return false;
        }
        boolean changed = false;
        for (UiMessage uiMessage : incoming) {
            int index = indexOfUiMessage(uiMessage);
            if (index >= 0) {
                uiMessages.set(index, uiMessage);
                changed = true;
            } else {
                uiMessages.add(uiMessage);
                changed = true;
            }
        }
        if (changed) {
            uiMessages.sort((a, b) -> Long.compare(b.getTimestamp(), a.getTimestamp()));
        }
        return changed;
    }

    private int indexOfUiMessage(@NonNull UiMessage target) {
        String stableKey = target.getStableKey();
        for (int i = 0; i < uiMessages.size(); i++) {
            if (stableKey.equals(uiMessages.get(i).getStableKey())) {
                return i;
            }
        }
        return -1;
    }

    /**
     * 点击“新消息”气泡后按分页定位到首条未读消息。
     *
     * <p>简要描述：点击后只拉取“首条未读附近的一页消息”并跳转，不做循环补齐；后续由用户上滑/下滑再按分页按需拉取，避免一次性迭代拉全量。</p>
     */
    private void jumpToUnreadBoundary() {
        if (adapter == null || layoutManager == null || recyclerView == null || unreadCount <= 0) {
            Log.d(TAG, "[未读跳转] skip, reason=invalid_state"
                    + ", unreadCount=" + unreadCount
                    + ", adapterNull=" + (adapter == null)
                    + ", layoutManagerNull=" + (layoutManager == null)
                    + ", recyclerNull=" + (recyclerView == null));
            return;
        }
        if (unreadJumpInProgress) {
            Log.d(TAG, "[未读跳转] skip, reason=in_progress");
            return;
        }
        unreadJumpInProgress = true;
        Log.d(TAG, "[未读跳转] start"
                + ", unreadCount=" + unreadCount
                + ", window=" + formatDataWindow(captureDataWindow()));

        JIM.getInstance().getMessageManager().getFirstUnreadMessage(
                getCurrentConversation(),
                new IMessageManager.IGetMessagesCallback() {
                    @Override
                    public void onSuccess(List<Message> messages) {
                        if (getActivity() == null) {
                            unreadJumpInProgress = false;
                            Log.d(TAG, "[未读跳转] stop, reason=activity_null_after_first_unread");
                            return;
                        }
                        requireActivity().runOnUiThread(() -> {
                            Message anchor = findEarliestMessage(messages);
                            if (anchor == null || anchor.getTimestamp() <= 0L) {
                                unreadJumpInProgress = false;
                                Log.d(TAG, "[未读跳转] first_unread_empty, fallback=local_position");
                                jumpToLoadedUnreadOrTop();
                                return;
                            }
                            String anchorMessageId = anchor.getMessageId() == null ? "" : anchor.getMessageId();
                            long anchorTimestamp = anchor.getTimestamp();
                            Log.d(TAG, "[未读跳转] first_unread_anchor"
                                    + ", messageId=" + anchorMessageId
                                    + ", timestamp=" + anchorTimestamp);
                            loadUnreadFirstPageAndJump(anchorTimestamp, anchorMessageId);
                        });
                    }

                    @Override
                    public void onError(int code) {
                        if (getActivity() == null) {
                            unreadJumpInProgress = false;
                            Log.d(TAG, "[未读跳转] stop, reason=activity_null_after_first_unread_error, code=" + code);
                            return;
                        }
                        requireActivity().runOnUiThread(() -> {
                            unreadJumpInProgress = false;
                            Log.d(TAG, "[未读跳转] first_unread_error, code=" + code + ", fallback=local_position");
                            jumpToLoadedUnreadOrTop();
                        });
                    }
                });
    }

    /**
     * 从候选消息中挑选时间最早的一条，作为首条未读锚点。
     *
     * @param messages SDK 返回的候选消息
     * @return 时间最早的消息；无可用数据时返回 null
     */
    @Nullable
    private Message findEarliestMessage(@Nullable List<Message> messages) {
        if (messages == null || messages.isEmpty()) {
            Log.d(TAG, "findEarliestMessage empty");
            return null;
        }
        Message earliest = null;
        for (Message message : messages) {
            if (message == null) {
                continue;
            }
            if (earliest == null || message.getTimestamp() < earliest.getTimestamp()) {
                earliest = message;
            }
        }
        return earliest;
    }

    /**
     * 拉取首条未读所在页并跳转到目标消息。
     *
     * <p>简要描述：用“首条未读时间戳 - 1”作为 NEWER 游标，确保该首条未读落入返回页；仅请求一页，避免循环加载。</p>
     */
    private void loadUnreadFirstPageAndJump(long firstUnreadTimestamp, @Nullable String firstUnreadMessageId) {
        if (adapter == null || layoutManager == null || recyclerView == null || firstUnreadTimestamp <= 0L) {
            unreadJumpInProgress = false;
            Log.d(TAG, "[未读跳转] stop, reason=invalid_anchor_or_component"
                    + ", timestamp=" + firstUnreadTimestamp);
            return;
        }
        long startTime = Math.max(firstUnreadTimestamp - 1L, 0L);
        Log.d(TAG, "[未读跳转] load_first_page"
                + ", startTime=" + startTime
                + ", anchorTimestamp=" + firstUnreadTimestamp
                + ", anchorMessageId=" + (firstUnreadMessageId == null ? "" : firstUnreadMessageId));
        uiMessages.clear();
        hasMoreOlder = true;
        hasMoreNewer = true;
        adapter.submitList(new ArrayList<>(), () -> loadMessages(
                DEFAULT_PAGE_COUNT,
                startTime,
                JIMConst.PullDirection.NEWER,
                false,
                true,
                () -> {
                    unreadJumpInProgress = false;
                    scrollToTargetMessage(firstUnreadMessageId, firstUnreadTimestamp);
                    maybeDismissUnreadBubbleWhenTargetVisible();
                    Log.d(TAG, "[未读跳转] first_page_loaded"
                            + ", window=" + formatDataWindow(captureDataWindow())
                            + ", hasMoreOlder=" + hasMoreOlder
                            + ", hasMoreNewer=" + hasMoreNewer);
                }));
    }

    /**
     * 本地兜底跳转：优先跳到当前已加载数据中的首条未读，否则回到列表顶部。
     */
    private void jumpToLoadedUnreadOrTop() {
        if (adapter == null || layoutManager == null || recyclerView == null) {
            return;
        }
        int target = resolveFirstUnreadPosition(adapter.getCurrentList());
        if (target >= 0) {
            int offset = recyclerView.getHeight() > 0 ? recyclerView.getHeight() / 4 : 0;
            layoutManager.scrollToPositionWithOffset(target, offset);
            Log.d(TAG, "[未读跳转] fallback_hit_target"
                    + ", targetPos=" + target
                    + ", offset=" + offset);
            return;
        }
        if (adapter.getItemCount() > 0) {
            layoutManager.scrollToPositionWithOffset(0, 0);
            Log.d(TAG, "[未读跳转] fallback_to_top");
        }
    }

    /**
     * 计算当前列表中“首条未读消息”对应的位置。
     *
     * <p>简要描述：从最新消息向前按“真实消息条目”计数，跳过时间分割项，保证定位稳定。</p>
     *
     * @param displayList 当前展示列表（旧 -> 新）
     * @return 首条未读位置；若当前窗口尚未加载到该消息，返回 -1
     */
    private int resolveFirstUnreadPosition(@NonNull List<UiMessage> displayList) {
        if (unreadCount <= 0 || displayList.isEmpty()) {
            return -1;
        }
        int remain = unreadCount;
        for (int i = displayList.size() - 1; i >= 0; i--) {
            if (isTimeStatus(displayList.get(i))) {
                continue;
            }
            remain--;
            if (remain == 0) {
                return i;
            }
        }
        return -1;
    }

    /**
     * 当首条未读进入可视区域时自动隐藏右侧未读气泡。
     */
    private void maybeDismissUnreadBubbleWhenTargetVisible() {
        if (layoutUnreadBubble == null || layoutUnreadBubble.getVisibility() != VISIBLE
                || layoutManager == null || adapter == null) {
            return;
        }
        int target = resolveFirstUnreadPosition(adapter.getCurrentList());
        if (target < 0) {
            return;
        }
        int firstVisible = layoutManager.findFirstVisibleItemPosition();
        int lastVisible = layoutManager.findLastVisibleItemPosition();
        if (firstVisible < 0 || lastVisible < firstVisible) {
            return;
        }
        if (target >= firstVisible && target <= lastVisible) {
            Log.d(TAG, "[未读气泡] dismiss_by_visible_target"
                    + ", target=" + target
                    + ", firstVisible=" + firstVisible
                    + ", lastVisible=" + lastVisible);
            hideUnreadBubble();
        }
    }

    /**
     * 隐藏未读气泡。
     */
    private void hideUnreadBubble() {
        if (layoutUnreadBubble != null) {
            if (layoutUnreadBubble.getVisibility() == VISIBLE) {
                Log.d(TAG, "[未读气泡] hide");
            }
            layoutUnreadBubble.setVisibility(GONE);
        }
    }

    private void scrollToTargetMessage(@Nullable String targetMessageId, long targetTimestamp) {
        recyclerView.post(() -> {
            List<UiMessage> display = adapter.getCurrentList();
            int target = findTargetPosition(display, targetMessageId, targetTimestamp);
            if (target < 0) {
                return;
            }
            highlightTargetMessage(display, target);
        });
    }

    /**
     * 滚动并高亮目标消息。
     *
     * <p>tips：高亮状态写入 UiMessage extension，不改动 SDK Message，避免影响消息实体同步逻辑。</p>
     *
     * @param displayList 当前展示列表
     * @param target      目标位置
     */
    private void highlightTargetMessage(@NonNull List<UiMessage> displayList, int target) {
        if (target < 0 || target >= displayList.size()) {
            return;
        }
        int offset = recyclerView.getHeight() > 0 ? recyclerView.getHeight() / 4 : 0;
        layoutManager.scrollToPositionWithOffset(target, offset);
        UiMessage targetMessage = displayList.get(target);
        targetMessage.putExtension("highlight", true);
        adapter.notifyItemChanged(target);
        recyclerView.removeCallbacks(clearHighlightRunnable);
        clearHighlightRunnable = () -> {
            targetMessage.putExtension("highlight", false);
            int latestIndex = adapter.getCurrentList().indexOf(targetMessage);
            if (latestIndex >= 0) {
                adapter.notifyItemChanged(latestIndex);
            }
        };
        recyclerView.postDelayed(clearHighlightRunnable, 10_000L);
    }

    private int findTargetPosition(@NonNull List<UiMessage> displayList, @Nullable String targetMessageId,
            long targetTimestamp) {
        if (displayList.isEmpty()) {
            return -1;
        }
        if (targetMessageId != null && !targetMessageId.isEmpty()) {
            for (int i = 0; i < displayList.size(); i++) {
                if (targetMessageId.equals(displayList.get(i).getMessageId())) {
                    return i;
                }
            }
        }
        int bestIndex = -1;
        long bestDiff = Long.MAX_VALUE;
        for (int i = 0; i < displayList.size(); i++) {
            UiMessage uiMessage = displayList.get(i);
            if (isTimeStatus(uiMessage)) {
                continue;
            }
            long diff = Math.abs(uiMessage.getTimestamp() - targetTimestamp);
            if (diff < bestDiff) {
                bestDiff = diff;
                bestIndex = i;
            }
        }
        return bestIndex;
    }

    private boolean isTimeStatus(@Nullable UiMessage uiMessage) {
        return uiMessage != null
                && uiMessage.getMessage() != null
                && uiMessage.getMessage().getContent() instanceof InsertTimeStatusMessage;
    }

    private void updateMentionTargetFromList(@Nullable List<Message> list) {
        mentionTargetTimestamp = 0L;
        mentionTargetMessageId = "";
        if (list == null || list.isEmpty()) {
            return;
        }
        Message target = null;
        for (Message message : list) {
            if (message == null) {
                continue;
            }
            if (target == null || message.getTimestamp() > target.getTimestamp()) {
                target = message;
            }
        }
        if (target == null) {
            return;
        }
        mentionTargetTimestamp = target.getTimestamp();
        mentionTargetMessageId = target.getMessageId() == null ? "" : target.getMessageId();
    }

    /**
     * Called when an input panel is shown. If the list is not at bottom, scroll to
     * bottom so
     * the newest messages remain visible above the panel.
     */
    public void scrollToBottomIfNeeded() {
        if (recyclerView == null || layoutManager == null)
            return;
        int total = layoutManager.getItemCount();
        if (total <= 0) {
            return;
        }
        recyclerView.postDelayed(() -> {
            recyclerView.scrollToPosition(total - 1);
        }, 120);
    }

    public void onNewMessage(Message message) {
        UiMessage um = UiMessage.fromMessage(message);
        if (um == null)
            return;
        if (!message.getConversation().getConversationId().equals(conversationId))
            return;

        // Ensure we update UI on main thread and avoid concurrent submitList races.
        if (getActivity() == null)
            return;
        requireActivity().runOnUiThread(() -> {
            // make sure adapter and recyclerView references are available
            if (adapter == null || recyclerView == null) {
                RecyclerView rv = getView() != null ? getView().findViewById(R.id.recycler_view_messages) : null;
                if (rv != null && rv.getAdapter() instanceof MessageListAdapter) {
                    adapter = (MessageListAdapter) rv.getAdapter();
                    recyclerView = rv;
                }
            }

            // if adapter still not ready, enqueue and return; processPendingMessages will
            // run later
            hasMoreNewer = true;
            upsertUiMessages(Collections.singletonList(um));
            pendingMessages.add(um);
            processPendingMessages();

            if (!atBottom) {
                newMessageCount++;
                if (layoutNewMessageBubble != null && tvNewMessageCount != null) {
                    if (layoutNewMessageBubble.getVisibility() != VISIBLE) {
                        layoutNewMessageBubble.setVisibility(VISIBLE);
                        layoutNewMessageBubble.animate().translationX(0).setDuration(500).start();
                    }
                    tvNewMessageCount.setText(getString(R.string.msg_new_count,
                            newMessageCount >= 99 ? "99+" : String.valueOf(newMessageCount)));
                }
            }
        });
    }

    /**
     * Process queued incoming messages sequentially.
     * This makes sure we don't call submitList while a previous Diff is still in
     * progress.
     */
    private void processPendingMessages() {
        // must be on UI thread
        if (submitInProgress)
            return;
        if (pendingMessages.isEmpty())
            return;
        if (adapter == null || recyclerView == null)
            return;

        submitInProgress = true;
        // build new list from current adapter list + queued messages
        List<UiMessage> current = new ArrayList<>(adapter.getCurrentList());
        current.addAll(pendingMessages);
        this.setMessageRead(pendingMessages);
        pendingMessages.clear();

        adapter.submitList(current, () -> {
            // after commit, scroll to bottom and handle next batch
            if (atBottom) {
                // 使用 scrollToPosition 而非 smoothScrollToPosition，确保新消息立即显示
                // 平滑滚动需要时间，如果新消息来得快可能还没滚动完
                recyclerView.scrollToPosition(adapter.getItemCount() - 1);
            }
            maybeDismissUnreadBubbleWhenTargetVisible();
            submitInProgress = false;
            // continue processing any messages that arrived during the diff
            processPendingMessages();
        });
    }

    private void setMessageRead(List<UiMessage> uiMessages) {
        List<String> msgIds = new ArrayList<>();
        for (UiMessage um : uiMessages) {
            if (!um.getMessageId().isEmpty()
                    && !um.getMessage().isHasRead()
                    && um.getMessage().getDirection().getValue() == Message.MessageDirection.RECEIVE.getValue()) {
                msgIds.add(um.getMessageId());
            }
        }
        if (msgIds.isEmpty())
            return;
        Conversation conversation = new Conversation(
                isGroup ? Conversation.ConversationType.GROUP : Conversation.ConversationType.PRIVATE, conversationId);
        JIM.getInstance().getMessageManager().sendReadReceipt(conversation, msgIds, null);
    }

    public void onUpdateMessage(List<Message> messages) {
        List<UiMessage> current = new ArrayList<>(adapter.getCurrentList());
        List<UiMessage> updated = new ArrayList<>();
        for (Message message : messages) {
            UiMessage um = UiMessage.fromMessage(message);
            if (um == null)
                continue;
            if (!message.getConversation().getConversationId().equals(conversationId))
                continue;
            updated.add(um);
            int idx = -1;
            if (!TextUtils.isEmpty(um.getMessageId())) {
                idx = adapter.getIndexByMessageId(um.getMessageId());
            }
            if (idx < 0 && um.getMessage().getClientMsgNo() > 0L) {
                idx = adapter.getIndexByMessageNo(um.getMessage().getClientMsgNo());
            }
            if (idx < 0)
                continue;
            current.set(idx, um);
        }
        // 流式内容持续变长时，若用户停在底部则跟随滚动，避免新增内容被输入栏遮挡而看起来"显示不全"。
        adapter.submitList(current, () -> {
            if (atBottom) {
                recyclerView.post(this::pinLastItemBottom);
            }
        });
        upsertUiMessages(updated);
    }

    /**
     * 移除当前可见窗口中已经到期的消息，并重新生成时间分割项。
     *
     * @param currentTimeMillis 当前时间戳（毫秒）
     */
    @Override
    public void removeExpiredMessages(long currentTimeMillis) {
        if (adapter == null || currentTimeMillis <= 0L) {
            return;
        }
        boolean removed = false;
        Iterator<UiMessage> iterator = uiMessages.iterator();
        while (iterator.hasNext()) {
            Message message = iterator.next().getMessage();
            if (message != null && message.getDestroyTime() > 0L
                    && message.getDestroyTime() <= currentTimeMillis) {
                iterator.remove();
                removed = true;
            }
        }
        pendingMessages.removeIf(uiMessage -> {
            Message message = uiMessage.getMessage();
            return message != null && message.getDestroyTime() > 0L
                    && message.getDestroyTime() <= currentTimeMillis;
        });
        if (!removed) {
            return;
        }
        ViewportAnchor anchor = captureViewportAnchor();
        List<UiMessage> display = buildDisplayMessages();
        adapter.submitList(display, () -> restoreViewportAnchor(anchor, display));
    }

    /**
     * 将最后一条消息的底边钉在可视区底边。
     *
     * <p>tips：流式消息可能高于整屏，直接用 scrollToPosition 会把该项顶部对齐到屏幕顶部（跳到消息开头）；
     * 这里改为按底边对齐，保证持续输出时始终贴着屏幕底部滚动。</p>
     */
    private void pinLastItemBottom() {
        if (recyclerView == null || layoutManager == null || adapter == null) {
            return;
        }
        int last = adapter.getItemCount() - 1;
        if (last < 0) {
            return;
        }
        View lastView = layoutManager.findViewByPosition(last);
        if (lastView == null) {
            // 最后一项尚未布局，先粗定位再于下一帧精确对齐底边
            layoutManager.scrollToPositionWithOffset(last, 0);
            recyclerView.post(this::pinLastItemBottom);
            return;
        }
        int viewportBottom = recyclerView.getHeight() - recyclerView.getPaddingBottom();
        int delta = lastView.getBottom() - viewportBottom;
        if (delta > 0) {
            recyclerView.scrollBy(0, delta);
        }
    }

    private void onMessageAction(UiMessage message, String action) {
        if (message == null || action == null)
            return;
        dismissMessageContextMenu();
        if (action.startsWith(MessageListAdapter.Action.REACTION_PREFIX)) {
            String reactionPayload = action.substring(MessageListAdapter.Action.REACTION_PREFIX.length());
            addReactionToMessage(message, reactionPayload);
            return;
        }
        switch (action) {
            case MessageListAdapter.Action.TRANSLATE:
                String source = extractTextMessage(message);
                if (source == null || source.trim().isEmpty()) {
                    ToastUtils.show(requireContext(), R.string.operation_failed);
                    return;
                }
                ToastUtils.show(requireContext(), R.string.msg_action_translation_pending);
                break;
            case MessageListAdapter.Action.COPY:
                String text = extractTextMessage(message);
                if (text == null) return;
                android.content.ClipboardManager cm = (android.content.ClipboardManager) requireContext()
                        .getSystemService(android.content.Context.CLIPBOARD_SERVICE);
                if (text != null) {
                    android.content.ClipData clip = android.content.ClipData.newPlainText("message", text);
                    if (cm != null)
                        cm.setPrimaryClip(clip);
                }
                ToastUtils.show(requireContext(), R.string.msg_action_copied);
                break;
            case MessageListAdapter.Action.TOP:
                Conversation conversation = message.getMessage().getConversation();
                JIM.getInstance().getMessageManager().setTop(message.getMessageId(), conversation, true,
                        new IMessageManager.ISimpleCallback() {
                            @Override
                            public void onSuccess() {
                                Log.d("MessageListFragment", "set top success");
                            }

                            @Override
                            public void onError(int i) {
                                Log.d("MessageListFragment", "set top failed: " + i);
                            }
                        });
                break;
            case MessageListAdapter.Action.RECALL:
                this.recallMessage(message);
                break;
            case MessageListAdapter.Action.FAVORITE:
                String messageId = message.getMessageId();
                if (messageId == null || messageId.trim().isEmpty()) {
                    ToastUtils.show(requireContext(), R.string.operation_failed);
                    return;
                }
                List<String> messageIds = new ArrayList<>();
                messageIds.add(messageId);
                JIM.getInstance().getMessageManager().addFavorite(messageIds, new IMessageManager.ISimpleCallback() {
                    @Override
                    public void onSuccess() {
                        if (getActivity() == null) {
                            return;
                        }
                        getActivity().runOnUiThread(() -> ToastUtils.show(requireContext(), R.string.msg_action_favorited));
                    }

                    @Override
                    public void onError(int i) {
                        if (getActivity() == null) {
                            return;
                        }
                        getActivity().runOnUiThread(() -> ToastUtils.show(requireContext(), R.string.operation_failed));
                    }
                });
                break;
            case MessageListAdapter.Action.FORWARD:
                if (!selectionMode) {
                    enterSelectionMode(message);
                } else {
                    toggleSelectionForMessage(message);
                }
                if (adapter.getSelectionCount() > 0) {
                    showForwardMenu();
                }
                break;
            case MessageListAdapter.Action.MULTI_SELECT:
                if (!selectionMode) {
                    enterSelectionMode(message);
                } else {
                    toggleSelectionForMessage(message);
                }
                break;
            case MessageListAdapter.Action.REPORT:
                ToastUtils.show(requireContext(), R.string.msg_action_reported);
                break;
            case "toggle_select":
                toggleSelectionForMessage(message);
                break;
            case MessageListAdapter.Action.EDIT:
                ChatInputActionBar input = getActivity().findViewById(R.id.input_bar);
                if (input == null) return;
                input.showReferMsgPanel(message.getSenderName(),
                        MessageUtils.getMessageSummary(getContext(), message.getMessage()), message.getMessageId(),
                        R.id.tag_edit_msg);
                break;
            case MessageListAdapter.Action.REPLY:
                input = getActivity().findViewById(R.id.input_bar);
                if (input == null) return;
                input.showReferMsgPanel(message.getSenderName(),
                        MessageUtils.getMessageSummary(getContext(), message.getMessage()), message.getMessageId(),
                        R.id.tag_reply_msg);
                break;
            case MessageListAdapter.Action.DELETE:
                if (message.getDirection() == Message.MessageDirection.SEND) {
                    BottomActionSheet.builder(requireContext())
                            .addItem(getString(R.string.msg_action_delete_self), () -> deleteSingleMessage(message))
                            .addItem(getString(R.string.msg_action_delete_both), () -> {
                                ToastUtils.show(requireContext(), R.string.msg_action_delete_remote_unsupported);
                                deleteSingleMessage(message);
                            })
                            .show();
                } else {
                    deleteSingleMessage(message);
                }
                break;
            default:
                break;
        }
    }

    private void toggleSelectionForMessage(UiMessage message) {
        if (message.getMessageId() == null) return;
        adapter.toggleSelect(message);
        selectedIds.clear();
        for (UiMessage um : adapter.getSelectedMessages()) {
            if (um.getMessageId() != null) {
                selectedIds.add(um.getMessageId());
            }
        }
        updateOptionBarState(selectedIds.size());
    }

    @Nullable
    private String extractTextMessage(UiMessage message) {
        MessageContent content = message.getMessage().getContent();
        if (content instanceof TextMessage) {
            return ((TextMessage) content).getContent();
        }
        return null;
    }

    @Override
    public void onPause() {
        super.onPause();
        dismissMessageContextMenu();
    }

    private void deleteSingleMessage(UiMessage message) {
        List<UiMessage> current = new ArrayList<>(adapter.getCurrentList());
        final int idx = adapter.getIndexByMessageNo(message.getMessage().getClientMsgNo());
        if (idx >= 0) {
            current.remove(idx);
        }
        this.deleteMessages(Arrays.asList(message), current);
    }

    /**
     * 对消息添加/取消 Reaction。
     * <p>
     * 简要描述：
     * 统一使用 stakerReactionId 发送，兼容历史旧 ID 的“查重 + 取消”逻辑，防止跨端 ID 不一致导致重复回应。
     */
    private void addReactionToMessage(UiMessage message, String reactionPayload) {
        if (message.getMessageId() == null || message.getMessageId().trim().isEmpty()) {
            ToastUtils.show(requireContext(), R.string.operation_failed);
            return;
        }
        Conversation conv = message.getMessage().getConversation();
        if (conv == null) {
            conv = new Conversation(
                    isGroup ? Conversation.ConversationType.GROUP : Conversation.ConversationType.PRIVATE,
                    conversationId);
        }
        final String reactionId = ReactionStakerMapper.toCanonicalReactionId(reactionPayload);
        if (reactionId.isEmpty()) {
            ToastUtils.show(requireContext(), R.string.operation_failed);
            return;
        }
        final Conversation finalConv = conv;

        // Check if current user already reacted with this emoji (toggle logic)
        String currentUserId = JIM.getInstance().getCurrentUserId();
        List<String> messageIdList = new ArrayList<>();
        messageIdList.add(message.getMessageId());

        // First get cached reactions for quick check
        List<MessageReaction> cachedReactions = JIM.getInstance().getMessageManager()
                .getCachedMessagesReaction(messageIdList);

        boolean alreadyReacted = false;
        String removeReactionId = reactionId;
        if (cachedReactions != null && !cachedReactions.isEmpty()) {
            for (MessageReaction reaction : cachedReactions) {
                if (reaction.getItemList() != null) {
                    for (MessageReactionItem item : reaction.getItemList()) {
                        String itemReactionId = item.getReactionId();
                        String itemCanonicalId = ReactionStakerMapper.toCanonicalReactionId(itemReactionId);
                        if (reactionId.equals(itemCanonicalId)) {
                            if (item.getUserInfoList() != null) {
                                for (UserInfo user : item.getUserInfoList()) {
                                    if (currentUserId != null && currentUserId.equals(user.getUserId())) {
                                        alreadyReacted = true;
                                        if (!TextUtils.isEmpty(itemReactionId)) {
                                            removeReactionId = itemReactionId;
                                        }
                                        break;
                                    }
                                }
                            }
                        }
                        if (alreadyReacted) break;
                    }
                }
                if (alreadyReacted) break;
            }
        }

        if (alreadyReacted) {
            // Remove reaction
            JIM.getInstance().getMessageManager().removeMessageReaction(
                    message.getMessageId(),
                    finalConv,
                    removeReactionId,
                    new IMessageManager.ISimpleCallback() {
                        @Override
                        public void onSuccess() {
                            // Refresh the message to update reaction display
                            refreshMessageById(message.getMessageId());
                        }

                        @Override
                        public void onError(int i) {
                            Log.e("MessageListFragment", "removeMessageReaction failed, code=" + i
                                    + ", messageId=" + message.getMessageId()
                                    + ", reactionId=");
                            ToastUtils.show(requireContext(), R.string.operation_failed);
                        }
                    });
        } else {
            // Add reaction
            JIM.getInstance().getMessageManager().addMessageReaction(
                    message.getMessageId(),
                    finalConv,
                    reactionId,
                    new IMessageManager.ISimpleCallback() {
                        @Override
                        public void onSuccess() {
                            String emoji = ReactionStakerMapper.toEmoji(reactionId);
                            ToastUtils.show(requireContext(), getString(R.string.msg_action_reaction_added, emoji));
                            // Refresh the message to update reaction display
                            refreshMessageById(message.getMessageId());
                        }

                        @Override
                        public void onError(int i) {
                            Log.e("MessageListFragment", "addMessageReaction failed, code=" + i
                                    + ", messageId=" + message.getMessageId()
                                    + ", reactionId=" + reactionId);
                            ToastUtils.show(requireContext(), R.string.operation_failed);
                        }
                    });
        }
    }

    /**
     * 主线程刷新消息状态。
     *
     * @param messages 待刷新的消息列表
     */
    private void updateMessagesOnMainThread(@NonNull List<Message> messages) {
        if (messages.isEmpty() || getActivity() == null) {
            return;
        }
        requireActivity().runOnUiThread(() -> onUpdateMessage(messages));
    }

    private void recallMessage(UiMessage message) {
        JIM.getInstance().getMessageManager().recallMessage(message.getMessageId(), null,
                new IMessageManager.IRecallMessageCallback() {
                    @Override
                    public void onSuccess(Message recalledMsg) {
                        List<UiMessage> current = new ArrayList<>(adapter.getCurrentList());
                        final int idx = adapter.getIndexByMessageNo(message.getMessage().getClientMsgNo());
                        current.set(idx, UiMessage.fromMessage(recalledMsg));
                        adapter.submitList(current);
                    }

                    @Override
                    public void onError(int i) {
                        Toast.makeText(getActivity(), "Recall failed: " + i, Toast.LENGTH_SHORT).show();
                    }
                });
    }

    /**
     * 重发发送失败的消息。
     * tips: 文本/合并消息走 resendMessage，多媒体消息走 resendMediaMessage，保持和 SDK 能力一致。
     *
     * @param uiMessage 当前失败消息
     */
    private void resendFailedMessage(@Nullable UiMessage uiMessage) {
        if (uiMessage == null || uiMessage.getMessage() == null) {
            return;
        }
        Message message = uiMessage.getMessage();
        if (message.getDirection() != Message.MessageDirection.SEND
                || message.getState() == null
                || message.getState().getValue() != Message.MessageState.FAIL.getValue()) {
            return;
        }
        MessageContent content = message.getContent();
        if (content instanceof ImageMessage || content instanceof VoiceMessage || content instanceof FileMessage) {
            JIM.getInstance().getMessageManager().resendMediaMessage(message,
                    new IMessageManager.ISendMediaMessageCallback() {
                        @Override
                        public void onProgress(int progress, Message message) {
                            updateMessagesOnMainThread(Arrays.asList(message));
                        }

                        @Override
                        public void onSuccess(Message message) {
                            updateMessagesOnMainThread(Arrays.asList(message));
                        }

                        @Override
                        public void onError(Message message, int errorCode) {
                            updateMessagesOnMainThread(Arrays.asList(message));
                        }

                        @Override
                        public void onCancel(Message message) {
                            updateMessagesOnMainThread(Arrays.asList(message));
                        }
                    });
            return;
        }
        if (content instanceof TextMessage || content instanceof MergeMessage) {
            JIM.getInstance().getMessageManager().resendMessage(message,
                    new IMessageManager.ISendMessageCallback() {
                        @Override
                        public void onSuccess(Message message) {
                            updateMessagesOnMainThread(Arrays.asList(message));
                        }

                        @Override
                        public void onError(Message message, int errorCode) {
                            updateMessagesOnMainThread(Arrays.asList(message));
                        }
                    });
            return;
        }
        ToastUtils.show(requireContext(), R.string.operation_failed);
    }

    private void deleteMessages(List<UiMessage> messages, List<UiMessage> newDataSet) {
        Conversation conversation = messages.get(0).getMessage().getConversation();
        List<Long> msgNos = new ArrayList<>();
        for (UiMessage message : messages) {
            msgNos.add(message.getMessage().getClientMsgNo());
        }
        JIM.getInstance().getMessageManager().deleteMessagesByClientMsgNoList(conversation, msgNos,
                new IMessageManager.ISimpleCallback() {
                    @Override
                    public void onSuccess() {
                        adapter.submitList(newDataSet);
                    }

                    @Override
                    public void onError(int i) {
                        ToastUtils.show(requireContext(), R.string.operation_failed);
                    }
                });
    }

    /**
     * Refresh a specific message by its ID (used for reaction updates)
     */
    public void refreshMessageById(String messageId) {
        if (adapter == null || messageId == null) return;
        int idx = adapter.getIndexByMessageId(messageId);
        if (idx >= 0) {
            adapter.notifyItemChanged(idx);
        }
    }

    @Override
    public void onDestroyView() {
        if (recyclerView != null && clearHighlightRunnable != null) {
            recyclerView.removeCallbacks(clearHighlightRunnable);
        }
        clearHighlightRunnable = null;
        layoutUnreadBubble = null;
        unreadJumpInProgress = false;
        super.onDestroyView();
    }
}

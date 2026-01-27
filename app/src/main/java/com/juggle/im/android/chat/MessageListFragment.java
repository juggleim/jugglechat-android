package com.juggle.im.android.chat;

import static android.view.View.GONE;
import static android.view.View.VISIBLE;

import static com.juggle.im.android.chat.ConversationActivity.EXTRA_TITLE;

import android.os.Bundle;
import android.content.Intent;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.juggle.im.JIM;
import com.juggle.im.JIMConst;
import com.juggle.im.android.R;
import com.juggle.im.android.chat.utils.MessageUtils;
import com.juggle.im.android.chat.view.ChatInputActionBar;
import com.juggle.im.android.core.JIMChatCore;
import com.juggle.im.android.model.UiMessage;
import com.juggle.im.interfaces.IMessageManager;
import com.juggle.im.model.Conversation;
import com.juggle.im.model.Message;
import com.juggle.im.model.MessageContent;
import com.juggle.im.model.messages.TextMessage;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

public class MessageListFragment extends Fragment {
    private static final String ARG_CONV_ID = "arg_conv_id";
    private static final String ARG_IS_GROUP = "arg_is_group";
    private static final String ARG_UNREAD_COUNT = "arg_unread_count";
    public static final String ARG_MENTION = "arg_mention";

    private static final int msgPageCount = 20;

    private String conversationId;
    private boolean isGroup;
    private int unreadCount;
    // UI state
    private final List<UiMessage> uiMessages = new ArrayList<>();
    private androidx.recyclerview.widget.LinearLayoutManager layoutManager;
    private boolean isLoadingMore = false;
    private MessageListAdapter adapter;
    private RecyclerView recyclerView;
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
    // New Message Bubble
    private View layoutNewMessageBubble;
    private TextView tvNewMessageCount;
    private TextView tvMention;
    private int newMessageCount = 0;

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

        recyclerView = view.findViewById(R.id.recycler_view_messages);
        layoutManager = new LinearLayoutManager(requireContext());
        recyclerView.setLayoutManager(layoutManager);
        adapter = new MessageListAdapter(isGroup, (message, action) -> {
            // handle message actions here on UI thread (position is adapter/display
            // position)
            requireActivity().runOnUiThread(() -> onMessageAction(message, action));
        });
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

        if (unreadCount >= 6) {
            View bubble = view.findViewById(R.id.layout_unread_bubble);
            TextView tvUnread = view.findViewById(R.id.tv_unread_count);
            if (bubble != null && tvUnread != null) {
                bubble.setVisibility(VISIBLE);
                tvUnread.setText((unreadCount >= 99 ? "99+" : unreadCount) + "条新消息");
                bubble.animate().translationX(0).setDuration(500).start();
                bubble.setOnClickListener(v -> {
                    bubble.setVisibility(GONE);
                    loadMoreMessages(unreadCount - adapter.getItemCount(), true);
                });
            }
        }

        if (getArguments().getBoolean(ARG_MENTION, false)) {
            Conversation conversation = new Conversation(
                    isGroup ? Conversation.ConversationType.GROUP : Conversation.ConversationType.PRIVATE, conversationId);
            JIM.getInstance().getMessageManager().getMentionMessageList(conversation, 5, 0, JIMConst.PullDirection.OLDER, new IMessageManager.IGetMessagesWithFinishCallback() {
                @Override
                public void onSuccess(List<Message> list, boolean b) {
                    View bubble = view.findViewById(R.id.layout_mention_bubble);
                    TextView tvMention = view.findViewById(R.id.tv_mention);
                    if (bubble != null && tvMention != null) {
                        bubble.setVisibility(VISIBLE);
                        tvMention.setText("有人@我");
                        bubble.animate().translationX(0).setDuration(500).start();
                        bubble.setOnClickListener(v -> {
                            bubble.setVisibility(GONE);
                            int target = adapter.getItemCount() - unreadCount;
                            if (target < 0)
                                target = 0;
                            recyclerView.smoothScrollToPosition(target);
                        });
                    }
                }

                @Override
                public void onError(int i) {

                }
            });
        }
        // New Message Bubble Initialization
        layoutNewMessageBubble = view.findViewById(R.id.layout_new_message_bubble);
        tvNewMessageCount = view.findViewById(R.id.tv_new_message_count);
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
                if (atBottom) {
                    if (layoutNewMessageBubble != null && layoutNewMessageBubble.getVisibility() == VISIBLE) {
                        layoutNewMessageBubble.setVisibility(GONE);
                        newMessageCount = 0;
                    }
                }
                int first = layoutManager.findFirstVisibleItemPosition();
                if (first == 0 && dy < 0 && !isLoadingMore) {
                    loadMoreMessages(msgPageCount, false);
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

        // scroll listener to detect pull-to-top (load older messages)
        recyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView rv, int dx, int dy) {
                super.onScrolled(rv, dx, dy);
                int first = layoutManager.findFirstVisibleItemPosition();
                if (first == 0 && dy < 0 && !isLoadingMore) {
                    // load older messages
                    loadMoreMessages(msgPageCount, false);
                }
            }
        });
        // initial load: msgTime = 0 -> SDK should return latest page
        loadMoreMessages(msgPageCount, false);
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
                    ivBack.setImageResource(R.drawable.ic_back_white);
                    ivBack.setOnClickListener(v -> exitSelectionMode());
                }
                if (tv != null) {
                    tv.setText("已选择" + selectedCount + "条消息");
                }
                getActivity().findViewById(R.id.iv_settings).setVisibility(GONE);
            } else {
                if (ivBack != null) {
                    ivBack.setImageResource(R.drawable.ic_back_white);
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

    private void showForwardMenu() {
        if (overlayForwardContainer == null)
            return;
        overlayForwardContainer.setVisibility(VISIBLE);
        overlayForwardContainer.setOnClickListener(v -> {
            overlayForwardContainer.removeAllViews();
            overlayForwardContainer.setVisibility(GONE);
            selectionOptionBar.setVisibility(VISIBLE);
        });
        // create a small menu view at bottom
        View menu = LayoutInflater.from(requireContext()).inflate(R.layout.layout_forward_menu, null);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.gravity = android.view.Gravity.BOTTOM;
        overlayForwardContainer.removeAllViews();
        menu.setClickable(true);
        overlayForwardContainer.addView(menu, lp);
        selectionOptionBar.setVisibility(GONE);
        View vSingle = menu.findViewById(R.id.action_forward_single);
        View vMerge = menu.findViewById(R.id.action_forward_merge);
        View vCancel = menu.findViewById(R.id.action_forward_cancel);
        vSingle.setOnClickListener(v -> {
            overlayForwardContainer.removeView(menu);
            overlayForwardContainer.setVisibility(GONE);
            selectionOptionBar.setVisibility(VISIBLE);
            // start ForwardConversationListActivity in single-forward mode
            if (getActivity() != null) {
                Intent it = ForwardConversationListActivity.createIntent(getActivity(), "single");
                getActivity().startActivityForResult(it, ConversationActivity.REQ_FORWARD);
            }
        });
        vMerge.setOnClickListener(v -> {
            overlayForwardContainer.removeView(menu);
            overlayForwardContainer.setVisibility(GONE);
            selectionOptionBar.setVisibility(VISIBLE);
            // start ForwardConversationListActivity in merge-forward mode
            if (getActivity() != null) {
                Intent it = ForwardConversationListActivity.createIntent(getActivity(), "merge");
                getActivity().startActivityForResult(it, ConversationActivity.REQ_FORWARD);
            }
        });
        vCancel.setOnClickListener(v -> {
            overlayForwardContainer.removeView(menu);
            overlayForwardContainer.setVisibility(GONE);
            selectionOptionBar.setVisibility(VISIBLE);
        });
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

    public void showKeyboardIfNeed() {
        ChatInputActionBar input = getActivity().findViewById(R.id.input_bar);
        if (input != null) {
            input.showKeyboardIfNeed();
        }
    }

    private void loadMoreMessages(int c, boolean scrollTop) {
        if (isLoadingMore)
            return;
        isLoadingMore = true;
        long cursor = 0L;
        if (!uiMessages.isEmpty()) {
            // oldest message is at the end of newest-first list
            UiMessage oldest = uiMessages.get(uiMessages.size() - 1);
            cursor = oldest.getTimestamp();
        }

        JIMChatCore.getInstance().getMessages(conversationId,
                isGroup ? Conversation.ConversationType.GROUP : Conversation.ConversationType.PRIVATE, c, cursor,
                new IMessageManager.IGetMessagesCallbackV3() {
                    @Override
                    public void onGetMessages(List<Message> list, long timestamp, boolean hasMore, int code) {
                        if (list == null || list.isEmpty()) {
                            isLoadingMore = false;
                            return;
                        }
                        List<UiMessage> incoming = new ArrayList<>();
                        for (Message m : list) {
                            UiMessage um = UiMessage.fromMessage(m);
                            if (um != null)
                                incoming.add(um);
                        }
                        incoming.sort((a, b) -> Long.compare(b.getTimestamp(), a.getTimestamp()));
                        setMessageRead(incoming);
                        requireActivity().runOnUiThread(() -> {
                            boolean changed = false;
                            for (UiMessage um : incoming) {
                                final String id = um.getMessageId();
                                boolean exists = false;
                                for (UiMessage ex : uiMessages) {
                                    if (ex.getMessageId() != null && ex.getMessageId().equals(id)) {
                                        exists = true;
                                        break;
                                    }
                                }
                                if (!exists) {
                                    uiMessages.add(um);
                                    changed = true;
                                }
                            }
                            if (changed) {
                                // Build display list (oldest-first) and insert time status messages according
                                // to rules
                                List<UiMessage> merged = new ArrayList<>(uiMessages);
                                // merged currently is newest-first (we appended newest to end of uiMessages),
                                // convert to oldest-first for display
                                List<UiMessage> oldestFirst = new ArrayList<>(merged);
                                Collections.reverse(oldestFirst);

                                List<UiMessage> displayWithTimes = new ArrayList<>();
                                UiMessage prev = null;
                                for (UiMessage cur : oldestFirst) {
                                    if (MessageUtils.shouldInsertTimeBefore(prev, cur)) {
                                        UiMessage timeMsg = MessageUtils.createInsertTimeUiMessage(cur.getTimestamp());
                                        if (timeMsg != null)
                                            displayWithTimes.add(timeMsg);
                                    }
                                    displayWithTimes.add(cur);
                                    prev = cur;
                                }

                                int oldFirst = layoutManager.findFirstVisibleItemPosition();
                                adapter.submitList(displayWithTimes, () -> {
                                    // restore to roughly the same content position after prepend.
                                    // Note: because we added time messages, offset by number of inserted entries
                                    // before oldFirst
                                    // Simpler approach: scroll to keep roughly same message at top by finding the
                                    // id at oldFirst
                                    if (oldFirst >= 0 && oldFirst < displayWithTimes.size()) {
                                        layoutManager.scrollToPositionWithOffset(scrollTop ? 0 : oldFirst + incoming.size(), 0);
                                    } else if (oldFirst < 0) {
                                        scrollToBottomIfNeeded();
                                    }
                                });
                            }
                            isLoadingMore = false;
                        });
                    }
                });
    }

    /**
     * Called when an input panel is shown. If the list is not at bottom, scroll to
     * bottom so
     * the newest messages remain visible above the panel.
     */
    public void scrollToBottomIfNeeded() {
        if (recyclerView == null || layoutManager == null)
            return;
        // if we're already at bottom, nothing to do
        int total = layoutManager.getItemCount();
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
            pendingMessages.add(um);
            processPendingMessages();

            if (!atBottom) {
                newMessageCount++;
                if (layoutNewMessageBubble != null && tvNewMessageCount != null) {
                    if (layoutNewMessageBubble.getVisibility() != VISIBLE) {
                        layoutNewMessageBubble.setVisibility(VISIBLE);
                        layoutNewMessageBubble.animate().translationX(0).setDuration(500).start();
                    }
                    tvNewMessageCount.setText((newMessageCount >= 99 ? "99+" : newMessageCount) + "条新消息");
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
            submitInProgress = false;
            // continue processing any messages that arrived during the diff
            processPendingMessages();
        });
    }

    private void setMessageRead(List<UiMessage> uiMessages) {
        List<String> msgIds = new ArrayList<>();
        for (UiMessage um : uiMessages) {
            if (!um.getMessage().isHasRead()
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
        for (Message message : messages) {
            UiMessage um = UiMessage.fromMessage(message);
            if (um == null)
                return;
            if (!message.getConversation().getConversationId().equals(conversationId))
                return;
            int idx = adapter.getIndexByMessageNo(um.getMessage().getClientMsgNo());
            if (idx < 0)
                continue;
            current.set(idx, um);
        }
        adapter.submitList(current);
    }

    private void onMessageAction(UiMessage message, String action) {
        if (message == null || action == null)
            return;
        switch (action) {
            case MessageListAdapter.Action.COPY:
                // copy text content to clipboard if text
                android.content.ClipboardManager cm = (android.content.ClipboardManager) requireContext()
                        .getSystemService(android.content.Context.CLIPBOARD_SERVICE);
                MessageContent content = message.getMessage().getContent();
                String text = "";
                if (content instanceof TextMessage) {
                    text = ((TextMessage) content).getContent();
                } else {
                    return;
                }
                if (text != null) {
                    android.content.ClipData clip = android.content.ClipData.newPlainText("message", text);
                    if (cm != null)
                        cm.setPrimaryClip(clip);
                }
                android.widget.Toast.makeText(requireContext(), "Copied", android.widget.Toast.LENGTH_SHORT).show();
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
            case MessageListAdapter.Action.FORWARD:
                // If not in selection mode, enter selection mode selecting this message
                if (!selectionMode) {
                    enterSelectionMode(message);
                } else {
                    // already in selection mode: toggle this message
                    if (message.getMessageId() != null) {
                        adapter.toggleSelect(message);
                        // sync fragment selectedIds from adapter
                        selectedIds.clear();
                        for (UiMessage um : adapter.getSelectedMessages()) {
                            if (um.getMessageId() != null)
                                selectedIds.add(um.getMessageId());
                        }
                        updateOptionBarState(selectedIds.size());
                    }
                }
                break;
            case "toggle_select":
                // toggle selection for this message (checkbox clicked)
                if (message.getMessageId() != null) {
                    adapter.toggleSelect(message);
                    selectedIds.clear();
                    for (UiMessage um : adapter.getSelectedMessages()) {
                        if (um.getMessageId() != null)
                            selectedIds.add(um.getMessageId());
                    }
                    updateOptionBarState(selectedIds.size());
                }
                break;
            case MessageListAdapter.Action.EDIT:
                ChatInputActionBar input = getActivity().findViewById(R.id.input_bar);
                input.showReferMsgPanel(message.getSenderName(),
                        MessageUtils.getMessageSummary(getContext(), message.getMessage()), message.getMessageId(),
                        R.id.tag_edit_msg);
                break;
            case MessageListAdapter.Action.REPLY:
                input = getActivity().findViewById(R.id.input_bar);
                input.showReferMsgPanel(message.getSenderName(),
                        MessageUtils.getMessageSummary(getContext(), message.getMessage()), message.getMessageId(),
                        R.id.tag_reply_msg);
                break;
            case MessageListAdapter.Action.DELETE:
                List<UiMessage> current = new ArrayList<>(adapter.getCurrentList());
                final int idx = adapter.getIndexByMessageNo(message.getMessage().getClientMsgNo());
                if (idx >= 0) {
                    current.remove(idx);
                }
                this.deleteMessages(Arrays.asList(message), current);
                break;
            default:
                break;
        }
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
                        Toast.makeText(requireContext(), "Delete failed", Toast.LENGTH_SHORT).show();
                    }
                });
    }
}

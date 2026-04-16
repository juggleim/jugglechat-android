package com.juggle.im.android.chat;

import static android.view.View.GONE;
import static android.view.View.VISIBLE;

import static com.juggle.im.android.chat.MessageListFragment.ARG_MENTION;
import static com.juggle.im.android.chat.SelectMemberActivity.DISABLE_MEMBERS;
import static com.juggle.im.android.chat.SelectMemberActivity.GROUP_ID;
import static com.juggle.im.android.chat.SelectMemberActivity.SELECTED_MEMBERS;

import android.content.Context;
import android.content.Intent;
import android.graphics.BitmapFactory;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.juggle.im.android.component.AbsAppActivity;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;

import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.TextView;

import com.juggle.im.JIM;
import com.juggle.im.android.R;

import com.juggle.im.android.chat.call.BaseCallActivity;
import com.juggle.im.android.chat.mention.MentionManager;
import com.juggle.im.android.chat.mention.MentionModel;
import com.juggle.im.android.chat.plugin.CameraPlugin;
import com.juggle.im.android.chat.plugin.FilePlugin;
import com.juggle.im.android.chat.plugin.ImagePlugin;
import com.juggle.im.android.chat.plugin.TimedDeletePlugin;
import com.juggle.im.android.chat.plugin.VideoCallPlugin;
import com.juggle.im.android.chat.plugin.VoiceCallPlugin;
import com.juggle.im.android.chat.utils.FileUtils;
import com.juggle.im.android.chat.message.MessageTypes;
import com.juggle.im.android.chat.utils.MessageUtils;
import com.juggle.im.android.chat.view.ChatInputActionBar;
import com.juggle.im.android.event.MessageReadUpdatedEvent;
import com.juggle.im.android.event.MessageTopEvent;
import com.juggle.im.android.event.MessageUpdatedEvent;
import com.juggle.im.android.event.ReactionUpdatedEvent;
import com.juggle.im.android.model.UiMessage;
import com.juggle.im.interfaces.IMessageManager;
import com.juggle.im.model.Conversation;
import com.juggle.im.model.ConversationInfo;
import com.juggle.im.model.MergeMessagePreviewUnit;
import com.juggle.im.model.Message;
import com.juggle.im.model.MessageMentionInfo;
import com.juggle.im.model.MessageOptions;
import com.juggle.im.model.MessageReaction;
import com.juggle.im.model.PushData;
import com.juggle.im.model.UserInfo;
import com.juggle.im.model.messages.FileMessage;
import com.juggle.im.model.messages.ImageMessage;
import com.juggle.im.model.messages.MergeMessage;
import com.juggle.im.model.messages.TextMessage;
import com.juggle.im.model.messages.VoiceMessage;
import com.juggle.im.android.utils.ToastUtils;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class ConversationActivity extends AbsAppActivity {
    public static final String EXTRA_CONVERSATION_ID = "extra_conversation_id";
    public static final String EXTRA_IS_GROUP = "extra_is_group";
    public static final String EXTRA_TITLE = "extra_title";
    public static final String EXTRA_IS_TOP = "extra_is_top";
    public static final String EXTRA_IS_MUTE = "extra_is_mute";
    public static final String EXTRA_UNREAD_COUNT = "extra_unread_count";
    public static final int REQ_FORWARD = 2001;
    public static final int REQ_MENTION = 2002;
    public static final int REQ_MULTI_CALL_VOICE = 2003;
    public static final int REQ_MULTI_CALL_VIDEO = 2004;
    private boolean isGroup;
    private String conversationId;
    private Conversation conversation;
    private final Handler typingHandler = new Handler(Looper.getMainLooper());
    private Runnable typingHideRunnable;

    public static Intent intentFor(Context ctx,
            String conversationId,
            String title,
            boolean isGroup,
            boolean isTop,
            boolean isMute) {
        Intent i = new Intent(ctx, ConversationActivity.class);
        i.putExtra(EXTRA_CONVERSATION_ID, conversationId);
        i.putExtra(EXTRA_IS_GROUP, isGroup);
        i.putExtra(EXTRA_IS_TOP, isTop);
        i.putExtra(EXTRA_IS_MUTE, isMute);
        i.putExtra(EXTRA_TITLE, title);
        return i;
    }

    /**
     * 带会话标题的便捷跳转方法。
     *
     * @param ctx 上下文
     * @param conversationId 会话 id
     * @param isGroup 是否群聊
     * @param title 会话标题
     * @return 会话页 Intent
     */
    public static Intent intentFor(Context ctx, String conversationId, boolean isGroup, String title) {
        Intent i = intentFor(ctx, conversationId, title, isGroup, false, false);
        i.putExtra(EXTRA_TITLE, title);
        return i;
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_conversation);
        EventBus.getDefault().register(this);

        // setup toolbar title and back button
        TextView tvTitle = findViewById(R.id.tv_title);
        ImageView ivBack = findViewById(R.id.iv_back);
        ImageView ivSettings = findViewById(R.id.iv_settings);
        String title = getIntent().getStringExtra(EXTRA_TITLE);
        if (tvTitle != null && title != null) {
            tvTitle.setText(title);
        }
        if (ivBack != null) {
            ivBack.setOnClickListener(v -> finish());
        }

        if (ivSettings != null) {
            ivSettings.setOnClickListener(v -> {
                // launch settings activity with conversation id and isGroup
                String cid = getIntent().getStringExtra(EXTRA_CONVERSATION_ID);
                boolean grp = getIntent().getBooleanExtra(EXTRA_IS_GROUP, false);
                String t = getIntent().getStringExtra(EXTRA_TITLE);
                boolean isTop = getIntent().getBooleanExtra(EXTRA_IS_TOP, false);
                boolean isMute = getIntent().getBooleanExtra(EXTRA_IS_MUTE, false);
                Intent it = ConversationSettingsActivity.intentFor(this, cid, t, grp, isTop, isMute);
                startActivity(it);
            });
        }

        conversationId = getIntent().getStringExtra(EXTRA_CONVERSATION_ID);
        isGroup = getIntent().getBooleanExtra(EXTRA_IS_GROUP, false);
        conversation = new Conversation(
                isGroup ? Conversation.ConversationType.GROUP : Conversation.ConversationType.PRIVATE,
                conversationId);
        registerMentionResultListener();

        if (savedInstanceState == null) {
            boolean isMention = getIntent().getBooleanExtra(ARG_MENTION, false);
            int unreadCount = getIntent().getIntExtra(EXTRA_UNREAD_COUNT, 0);
            MessageListFragment frag = MessageListFragment.newInstance(conversationId, isGroup, unreadCount, isMention);
            getSupportFragmentManager()
                    .beginTransaction()
                    .replace(R.id.fragment_messages_container, frag)
                    .commit();
        }

        // wire up input bar
        ChatInputActionBar inputBar = findViewById(R.id.input_bar);
        if (inputBar != null) {
            inputBar.setListener(new ChatInputActionBar.Listener() {
                public void onSend(String text, String msgId, List<MentionModel> mentionModelList, int sendType) {
                    TextMessage msg = new TextMessage(text);
                    MessageOptions options = new MessageOptions();
                    PushData pushData = new PushData();
                    pushData.setContent(text);
                    options.setPushData(pushData);
                    if (mentionModelList != null && !mentionModelList.isEmpty()) {
                        MessageMentionInfo mentionInfo = getMessageMentionInfo(mentionModelList);
                        options.setMentionInfo(mentionInfo);
                    }
                    clearConversationDraft();
                    if (sendType == R.id.tag_edit_msg) {
                        editTextMessage(msgId, msg, options, conversation);
                    } else {
                        options.setReferredMessageId(msgId);
                        sendTextMessage(msg, options, conversation);
                    }
                }

                @Override
                public void onMentionTrigger(MentionManager mentionManager) {
                    showMentionMemberSheet();
                }

                @Override
                public void onRequestVoice() {
                }

                @Override
                public void onStartVoiceRecord() {
                    Log.i("TAG", "onStartVoiceRecord");
                }

                @Override
                public void onFinishRecord(String voiceUrl, long duration) {
                    Log.i("TAG", "onFinishVoiceRecord");
                    VoiceMessage voice = new VoiceMessage();
                    voice.setLocalPath(voiceUrl);
                    voice.setDuration((int) duration);
                    sendVoiceMessage(voice, conversation);
                }

                @Override
                public void onCancelVoiceRecord() {
                    Log.i("TAG", "onCancelVoiceRecord");
                }

                @Override
                public void onMoreAction(String pluginId, String action, Object data) {
                    handlePluginResult(pluginId, action, data);
                }

                @Override
                public void onPanelVisibilityChanged(boolean visible) {
                    scrollMessageListIfNeed(visible);
                }

                @Override
                public void onKeyboardVisibilityChanged(boolean visible) {
                    if (visible) {
                        // Keyboard 弹出时仅通过消息流接口通知，避免耦合具体 Fragment 实现。
                        MessageStreamSink streamSink = findMessageStreamSink();
                        if (streamSink != null) {
                            streamSink.scrollToBottomIfNeeded();
                        }
                    }
                }

                @Override
                public void onKeyboardCreated(int h) {
                    getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING);
                }
            });
            if (!TextUtils.isEmpty(title)) {
                inputBar.setInputHint(getString(R.string.input_msg_to, title));
            }
        }
        restoreConversationDraftToInput();

        // 消息置顶
        JIM.getInstance().getMessageManager().getTopMessage(conversation, new IMessageManager.IGetTopMessageCallback() {
            @Override
            public void onSuccess(Message message, UserInfo userInfo, long l) {
                handleTopMessage(message, userInfo);
            }

            @Override
            public void onError(int i) {
                Log.i("TAG", "getTopMessage error: " + i);
            }
        });
        applySystemBarStyle();
    }

    /**
     * 恢复会话草稿到输入框。
     *
     * <p>简要描述：进入会话页时优先使用 SDK 草稿回填输入框，保证离开后再进入仍可继续编辑。</p>
     */
    private void restoreConversationDraftToInput() {
        if (conversation == null) {
            return;
        }
        ChatInputActionBar inputBar = findViewById(R.id.input_bar);
        if (inputBar == null) {
            return;
        }
        ConversationInfo info = JIM.getInstance().getConversationManager().getConversationInfo(conversation);
        if (info == null) {
            return;
        }
        String draft = info.getDraft();
        if (!TextUtils.isEmpty(draft)) {
            inputBar.setInputText(draft);
        }
    }

    /**
     * 将当前输入框内容同步为会话草稿。
     *
     * <p>简要描述：输入框有内容则写入草稿，输入框为空则清空草稿，确保列表摘要与输入框状态一致。</p>
     */
    private void syncConversationDraftFromInput() {
        if (conversation == null) {
            return;
        }
        ChatInputActionBar inputBar = findViewById(R.id.input_bar);
        if (inputBar == null) {
            return;
        }
        String inputText = inputBar.getInputText();
        if (TextUtils.isEmpty(inputText) || TextUtils.isEmpty(inputText.trim())) {
            clearConversationDraft();
            return;
        }
        JIM.getInstance().getConversationManager().setDraft(conversation, inputText);
    }

    /**
     * 清空当前会话草稿。
     */
    private void clearConversationDraft() {
        if (conversation == null) {
            return;
        }
        JIM.getInstance().getConversationManager().clearDraft(conversation);
    }

    @NonNull
    private static MessageMentionInfo getMessageMentionInfo(List<MentionModel> mentionModelList) {
        MessageMentionInfo mentionInfo = new MessageMentionInfo();
        List<UserInfo> messageMentionInfoList = new ArrayList<>();
        for (MentionModel mentionModel : mentionModelList) {
            UserInfo userInfo = new UserInfo();
            userInfo.setUserId(mentionModel.getUserId());
            userInfo.setUserName(mentionModel.getDisplayName());
            messageMentionInfoList.add(userInfo);
        }
        mentionInfo.setType(MessageMentionInfo.MentionType.SOMEONE);
        mentionInfo.setTargetUsers(messageMentionInfoList);
        return mentionInfo;
    }

    private void registerMentionResultListener() {
        getSupportFragmentManager().setFragmentResultListener(
                MentionMemberSheetDialog.REQUEST_KEY,
                this,
                (requestKey, result) -> {
                    MessageStreamSink streamSink = findMessageStreamSink();
                    if (streamSink == null) {
                        return;
                    }
                    String resultType = result.getString(
                            MentionMemberSheetDialog.RESULT_TYPE,
                            MentionMemberSheetDialog.RESULT_TYPE_CANCELLED);
                    if (MentionMemberSheetDialog.RESULT_TYPE_SELECTED.equals(resultType)) {
                        ArrayList<String> ids = result.getStringArrayList(MentionMemberSheetDialog.RESULT_SELECTED_IDS);
                        ArrayList<String> names = result.getStringArrayList(MentionMemberSheetDialog.RESULT_SELECTED_NAMES);
                        if (ids != null && names != null && !ids.isEmpty() && ids.size() == names.size()) {
                            streamSink.insertMention(ids, names);
                            return;
                        }
                    }
                    streamSink.showKeyboardIfNeed();
                });
    }

    private void showMentionMemberSheet() {
        if (getSupportFragmentManager().findFragmentByTag(MentionMemberSheetDialog.TAG) != null) {
            return;
        }
        MentionMemberSheetDialog.newInstance(conversationId, isGroup)
                .show(getSupportFragmentManager(), MentionMemberSheetDialog.TAG);
    }

    private void handleTopMessage(Message message, UserInfo userInfo) {
        View vPin = findViewById(R.id.layout_pin_message);
        TextView tvContent = vPin.findViewById(R.id.pin_message_content);
        TextView tvSubtitle = vPin.findViewById(R.id.pin_message_subtitle);
        String userName = userInfo == null ? "" : userInfo.getUserName();
        tvContent.setText(
                userName + "：" + MessageUtils.getMessageSummary(ConversationActivity.this, message));
        if (tvSubtitle != null) {
            tvSubtitle.setText(getString(R.string.msg_pin_by_user, userName));
        }
        View del = findViewById(R.id.button_del_pin);
        del.setOnClickListener(v -> {
            JIM.getInstance().getMessageManager().setTop(message.getMessageId(), conversation, false, null);
            vPin.setVisibility(GONE);
        });
        vPin.setVisibility(VISIBLE);
    }

    private void applySystemBarStyle() {
        Window window = getWindow();
        window.setStatusBarColor(getColor(R.color.white));
        window.setNavigationBarColor(getColor(R.color.input_bg_light));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            WindowInsetsController controller = window.getInsetsController();
            if (controller != null) {
                controller.setSystemBarsAppearance(
                        WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS | WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS,
                        WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS | WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS);
            }
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            int ui = window.getDecorView().getSystemUiVisibility();
            ui |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                ui |= View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
            }
            window.getDecorView().setSystemUiVisibility(ui);
        }
    }

    @Nullable
    private MessageStreamSink findMessageStreamSink() {
        Fragment fragment = getSupportFragmentManager().findFragmentById(R.id.fragment_messages_container);
        if (fragment instanceof MessageStreamSink) {
            return (MessageStreamSink) fragment;
        }
        return null;
    }

    private void dispatchNewMessageToStream(@Nullable Message message) {
        if (message == null) {
            return;
        }
        MessageStreamSink streamSink = findMessageStreamSink();
        if (streamSink != null) {
            streamSink.onNewMessage(message);
        }
    }

    private void dispatchUpdatedMessagesToStream(List<Message> messages) {
        if (messages == null || messages.isEmpty()) {
            return;
        }
        MessageStreamSink streamSink = findMessageStreamSink();
        if (streamSink != null) {
            streamSink.onUpdateMessage(messages);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        ChatInputActionBar inputBar = findViewById(R.id.input_bar);
        boolean handled = false;
        if (inputBar != null) {
            handled = inputBar.onActivityResult(requestCode, resultCode, data);
        }
        // If not handled by plugins, you may still want to handle other requestCodes
        // here.
        if (!handled && requestCode == REQ_FORWARD && resultCode == RESULT_OK && data != null) {
            String targetConvId = data.getStringExtra(ForwardConversationListActivity.EXTRA_CONVERSATION_ID);
            String targetName = data.getStringExtra(ForwardConversationListActivity.EXTRA_CONVERSATION_NAME);
            boolean targetIsGroup = data.getBooleanExtra(ForwardConversationListActivity.EXTRA_IS_GROUP, false);
            String mode = data.getStringExtra(ForwardConversationListActivity.EXTRA_FORWARD_MODE);
            // obtain the message list fragment to get selected messages
            MessageListFragment frag = (MessageListFragment) getSupportFragmentManager()
                    .findFragmentById(R.id.fragment_messages_container);
            if (frag != null) {
                List<UiMessage> selected = frag.getSelectedMessagesForForward();
                Conversation targetConv = new Conversation(
                        targetIsGroup ? Conversation.ConversationType.GROUP : Conversation.ConversationType.PRIVATE,
                        targetConvId);
                if (mode != null && mode.equals("merge")) {
                    // perform merge forward
                    sendMergeMessage(selected, targetConv, targetName);
                } else {
                    // single-forward: forward text messages only (others skipped for now)
                    for (UiMessage um : selected) {
                        if (um == null || um.getMessage() == null)
                            continue;
                        Message m = um.getMessage();
                        if (m.getContent() instanceof TextMessage) {
                            sendTextMessage(new TextMessage(((TextMessage) m.getContent()).getContent()), null,
                                    targetConv);
                        } else if (m.getContent() instanceof ImageMessage) {
                            sendImageMessage((ImageMessage) m.getContent(), null, targetConv);
                        } else if (m.getContent() instanceof VoiceMessage) {
                            sendVoiceMessage((VoiceMessage) m.getContent(), targetConv);
                        } else if (m.getContent() instanceof FileMessage) {
                            sendFileMessage((FileMessage) m.getContent(), targetConv);
                        }
                    }
                }
                // clear selection state in fragment after forwarding
                frag.clearSelectionAfterForward();
            }
        } else if ((requestCode == REQ_MULTI_CALL_VOICE || requestCode == REQ_MULTI_CALL_VIDEO)
                && resultCode == RESULT_OK) {
            ArrayList<String> newIds = data.getStringArrayListExtra(SELECTED_MEMBERS);
            BaseCallActivity.startMultiCall(this, conversationId,
                    requestCode == REQ_MULTI_CALL_VIDEO,
                    JIM.getInstance().getCurrentUserId(),
                    newIds,
                    "outgoing");
        }
    }

    private void scrollMessageListIfNeed(boolean panelVisible) {
        MessageStreamSink streamSink = findMessageStreamSink();
        if (panelVisible && streamSink != null) {
            streamSink.scrollToBottomIfNeeded();
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void MessageTopEvent(MessageTopEvent event) {
        if (!event.getMessage().getConversation().getConversationId().equals(conversationId)) {
            return;
        }
        if (event.isTop()) {
            handleTopMessage(event.getMessage(), event.getUserInfo());
        } else {
            findViewById(R.id.layout_pin_message).setVisibility(GONE);
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void MessageUpdatedEvent(MessageUpdatedEvent event) {
        if (!event.getMessage().getConversation().getConversationId().equals(conversationId)) {
            return;
        }
        // tips: typing 消息不加入消息列表，而是在 title bar 下方显示"正在输入"指示器
        if (MessageTypes.TYPING_NTF.equals(event.getMessage().getContentType())) {
            showTypingIndicator();
            return;
        }
        dispatchNewMessageToStream(event.getMessage());
        // tag message read
        Conversation conversation = new Conversation(
                isGroup ? Conversation.ConversationType.GROUP : Conversation.ConversationType.PRIVATE,
                conversationId);
        JIM.getInstance().getConversationManager().clearUnreadCount(conversation, null);
    }

    /**
     * 显示"对方正在输入…"指示器，3秒内无新 typing 消息则自动隐藏。
     */
    private void showTypingIndicator() {
        TextView tvTyping = findViewById(R.id.tv_typing_indicator);
        if (tvTyping != null) {
            tvTyping.setVisibility(VISIBLE);
        }
        if (typingHideRunnable != null) {
            typingHandler.removeCallbacks(typingHideRunnable);
        }
        typingHideRunnable = () -> {
            TextView tv = findViewById(R.id.tv_typing_indicator);
            if (tv != null) {
                tv.setVisibility(GONE);
            }
            typingHideRunnable = null;
        };
        typingHandler.postDelayed(typingHideRunnable, 3000L);
    }

    /**
     * 清理 typing 指示器定时器。
     */
    private void cleanupTypingIndicator() {
        if (typingHideRunnable != null) {
            typingHandler.removeCallbacks(typingHideRunnable);
            typingHideRunnable = null;
        }
        TextView tvTyping = findViewById(R.id.tv_typing_indicator);
        if (tvTyping != null) {
            tvTyping.setVisibility(GONE);
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void MessageReadUpdatedEvent(MessageReadUpdatedEvent event) {
        if (!event.getConversation().getConversationId().equals(conversationId)) {
            return;
        }
        List<Message> messages = JIM.getInstance().getMessageManager()
                .getMessagesByMessageIds(event.getMessageIds());
        dispatchUpdatedMessagesToStream(messages);
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onReactionUpdatedEvent(ReactionUpdatedEvent event) {
        if (!event.getConversation().getConversationId().equals(conversationId)) {
            return;
        }
        // Refresh the message to get updated reactions
        String messageId = event.getMessageReaction().getMessageId();
        if (messageId != null) {
            List<String> messageIdList = new ArrayList<>();
            messageIdList.add(messageId);
            JIM.getInstance().getMessageManager().getMessagesReaction(
                    messageIdList,
                    event.getConversation(),
                    new IMessageManager.IMessageReactionListCallback() {
                        @Override
                        public void onSuccess(List<MessageReaction> reactionList) {
                            // Reactions refreshed - notify adapter to update the specific message
                            Log.d("ConversationActivity", "Reactions refreshed for message: " + messageId);
                            MessageListFragment frag = (MessageListFragment) getSupportFragmentManager()
                                    .findFragmentById(R.id.fragment_messages_container);
                            if (frag != null) {
                                frag.refreshMessageById(messageId);
                            }
                        }

                        @Override
                        public void onError(int errorCode) {
                            Log.e("ConversationActivity", "Failed to refresh reactions: " + errorCode);
                        }
                    });
        }
    }

    private void handlePluginResult(String pluginId, String action, Object data) {
        if (pluginId == null) {
            return;
        }
        if (pluginId.equals(ImagePlugin.ID)) {
            if (!(data instanceof List)) {
                return;
            }
            for (Object item : (List<?>) data) {
                if (!(item instanceof String)) {
                    continue;
                }
                String url = (String) item;
                ImageMessage image = new ImageMessage();
                String fileUrl = FileUtils.convertContentUriToFile(this, url);
                applyImageSize(image, fileUrl);
                image.setLocalPath(fileUrl);
                image.setThumbnailLocalPath(fileUrl);
                sendImageMessage(image, null, conversation);
            }
        } else if (pluginId.equals(CameraPlugin.ID)) {
            if (data == null) {
                return;
            }
            ImageMessage image = new ImageMessage();
            String fileUrl = FileUtils.convertContentUriToFile(this, data.toString());
            applyImageSize(image, fileUrl);
            image.setLocalPath(fileUrl);
            image.setThumbnailLocalPath(fileUrl);
            sendImageMessage(image, null, conversation);
        } else if (pluginId.equals("location")) {
        } else if (pluginId.equals("contact")) {

        } else if (pluginId.equals(FilePlugin.ID)) {
            if (data == null) {
                return;
            }
            String fileUrl = FileUtils.convertContentUriToFile(this, data.toString());
            FileMessage fileMessage = new FileMessage();
            File f = new File(fileUrl);
            fileMessage.setLocalPath(fileUrl);
            fileMessage.setName(f.getName().length() > 10 ? f.getName().substring(0, 10) : f.getName());
            long size = f.length();
            fileMessage.setSize(size);
            sendFileMessage(fileMessage, conversation);
        } else if (pluginId.equals(VoiceCallPlugin.ID) || pluginId.equals(VideoCallPlugin.ID)) {
            if (isGroup) {
                Intent it = new Intent(this, SelectMemberActivity.class);
                it.putExtra("GROUP_ID", conversationId);
                startActivityForResult(it,
                        pluginId.equals(VideoCallPlugin.ID) ? REQ_MULTI_CALL_VIDEO : REQ_MULTI_CALL_VOICE);
            } else {
                ArrayList<String> ids = new ArrayList<>();
                ids.add(conversationId);
                BaseCallActivity.startSingleCall(this,
                        conversationId,
                        isGroup,
                        pluginId.equals(VideoCallPlugin.ID),
                        JIM.getInstance().getCurrentUserId(),
                        ids, "outgoing");
            }
        } else if (pluginId.equals(TimedDeletePlugin.ID)) {
            showTimedDeleteSelector();
        }
    }

    private void showTimedDeleteSelector() {
        final String[] durations = new String[]{
                getString(R.string.design_timed_delete_1d),
                getString(R.string.design_timed_delete_1w),
                getString(R.string.design_timed_delete_1m),
                getString(R.string.design_timed_delete_3m),
                getString(R.string.design_timed_delete_6m),
                getString(R.string.design_timed_delete_1y)
        };
        new AlertDialog.Builder(this)
                .setTitle(R.string.timed_delete)
                .setItems(durations, (dialog, which) -> {
                    if (which < 0 || which >= durations.length) {
                        return;
                    }
                    ToastUtils.show(this, getString(R.string.timed_delete_selected, durations[which]));
                })
                .setNegativeButton(R.string.txt_cancel, null)
                .show();
    }

    private void editTextMessage(String msgId, TextMessage msg, MessageOptions options, Conversation conversation) {
        JIM.getInstance().getMessageManager().updateMessage(msgId, msg, conversation,
                new IMessageManager.IMessageCallback() {
                    @Override
                    public void onSuccess(Message message) {
                        dispatchUpdatedMessagesToStream(Arrays.asList(message));
                    }

                    @Override
                    public void onError(int i) {
                        Log.d("MessageListFragment", "update message failed: " + i);
                    }
                });
    }

    private void sendTextMessage(TextMessage text, MessageOptions options, Conversation conversation) {
        IMessageManager.ISendMessageCallback callback = new IMessageManager.ISendMessageCallback() {
            @Override
            public void onSuccess(Message message) {
                dispatchUpdatedMessagesToStream(Arrays.asList(message));
            }

            @Override
            public void onError(Message message, int errorCode) {
                Log.i("TAG", "send message error: " + errorCode);
                dispatchUpdatedMessagesToStream(Arrays.asList(message));
            }
        };
        Message message = JIM.getInstance().getMessageManager().sendMessage(text, conversation, options, callback);
        dispatchNewMessageToStream(message);
    }

    private void applyImageSize(ImageMessage image, String filePath) {
        int width = 800;
        int height = 600;
        if (!TextUtils.isEmpty(filePath)) {
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(filePath, options);
            if (options.outWidth > 0 && options.outHeight > 0) {
                width = options.outWidth;
                height = options.outHeight;
            }
        }
        image.setWidth(width);
        image.setHeight(height);
    }

    private void sendImageMessage(ImageMessage image, MessageOptions options, Conversation conversation) {
        IMessageManager.ISendMediaMessageCallback callback = new IMessageManager.ISendMediaMessageCallback() {
            @Override
            public void onProgress(int progress, Message message) {
                Log.i("sendImageMessage", "onProgress: " + progress);
            }

            @Override
            public void onSuccess(Message message) {
                Log.i("sendImageMessage", "send message success");
                dispatchUpdatedMessagesToStream(Arrays.asList(message));
            }

            @Override
            public void onError(Message message, int errorCode) {
                Log.i("sendImageMessage", "send message error: " + errorCode);
                dispatchUpdatedMessagesToStream(Arrays.asList(message));
            }

            @Override
            public void onCancel(Message message) {
                Log.i("sendImageMessage", "onCancel");
            }
        };
        Message message = JIM.getInstance().getMessageManager().sendMediaMessage(image, conversation, callback);
        Log.i("TAG", "sendImageMessage msgId= " + message.getMessageId());
        dispatchNewMessageToStream(message);
    }

    public void sendFileMessage(FileMessage fileMessage, Conversation conversation) {
        IMessageManager.ISendMediaMessageCallback callback = new IMessageManager.ISendMediaMessageCallback() {
            @Override
            public void onProgress(int progress, Message message) {
                Log.i("TAG", "onProgress");
            }

            @Override
            public void onSuccess(Message message) {
                Log.i("TAG", "send message success");
                dispatchUpdatedMessagesToStream(Arrays.asList(message));

            }

            @Override
            public void onError(Message message, int errorCode) {
                Log.i("TAG", "send message error: " + errorCode);
                dispatchUpdatedMessagesToStream(Arrays.asList(message));

            }

            @Override
            public void onCancel(Message message) {
                Log.i("TAG", "onCancel");
            }
        };

        Message message = JIM.getInstance().getMessageManager().sendMediaMessage(fileMessage, conversation, callback);
        Log.i("TAG", "after send, clientMsgNo is " + message.getClientMsgNo());
        dispatchNewMessageToStream(message);
    }

    private void sendVoiceMessage(VoiceMessage voice, Conversation conversation) {
        IMessageManager.ISendMediaMessageCallback callback = new IMessageManager.ISendMediaMessageCallback() {
            @Override
            public void onProgress(int progress, Message message) {
                Log.i("TAG", "onProgress");
            }

            @Override
            public void onSuccess(Message message) {
                Log.i("TAG", "send message success");
                dispatchUpdatedMessagesToStream(Arrays.asList(message));
            }

            @Override
            public void onError(Message message, int errorCode) {
                Log.i("TAG", "send message error: " + errorCode);
                dispatchUpdatedMessagesToStream(Arrays.asList(message));
            }

            @Override
            public void onCancel(Message message) {
                Log.i("TAG", "onCancel");
            }
        };
        Message message = JIM.getInstance().getMessageManager().sendMediaMessage(voice, conversation, callback);
        Log.i("TAG", "after send, clientMsgNo is " + message.getClientMsgNo());
        dispatchNewMessageToStream(message);
    }

    public void sendMergeMessage(List<UiMessage> forwardMsg, Conversation targetConv, String targetName) {
        List<MergeMessagePreviewUnit> previewList = new ArrayList<>();
        List<String> msgIds = new ArrayList<>();
        for (int i = 0; i < forwardMsg.size(); i++) {
            MergeMessagePreviewUnit unit = new MergeMessagePreviewUnit();
            unit.setPreviewContent(MessageUtils.getMessageSummary(this, forwardMsg.get(i).getMessage()));
            UserInfo userInfo = new UserInfo();
            userInfo.setUserId(forwardMsg.get(i).getSenderId());
            userInfo.setUserName(forwardMsg.get(i).getSenderName());
            unit.setSender(userInfo);
            previewList.add(unit);
            msgIds.add(forwardMsg.get(i).getMessageId());
        }
        MergeMessage merge = new MergeMessage(targetName, conversation, msgIds, previewList);
        Message m = JIM.getInstance().getMessageManager().sendMessage(merge, targetConv,
                new IMessageManager.ISendMessageCallback() {
                    @Override
                    public void onSuccess(Message message) {
                        dispatchUpdatedMessagesToStream(Arrays.asList(message));
                    }

                    @Override
                    public void onError(Message message, int errorCode) {
                        Log.i("TAG", "send message error: " + errorCode);
                        dispatchUpdatedMessagesToStream(Arrays.asList(message));
                    }
                });
        dispatchNewMessageToStream(m);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        ChatInputActionBar inputBar = findViewById(R.id.input_bar);
        if (inputBar != null) {
            inputBar.onPluginRequestPermissionsResult(requestCode, permissions, grantResults);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        syncConversationDraftFromInput();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        cleanupTypingIndicator();
        ChatInputActionBar inputBar = findViewById(R.id.input_bar);
        if (inputBar != null) {
            inputBar.hideKeyboard();
        }
        EventBus.getDefault().unregister(this);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            finish();
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    public void finish() {
        syncConversationDraftFromInput();
        // Hide the keyboard before finishing the activity
        ChatInputActionBar inputBar = findViewById(R.id.input_bar);
        if (inputBar != null) {
            inputBar.hideKeyboard();
        }
        super.finish();
    }
}

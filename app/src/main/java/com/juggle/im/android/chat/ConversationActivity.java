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
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.juggle.im.android.component.AbsAppActivity;
import androidx.fragment.app.Fragment;

import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.Window;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.juggle.im.JIM;
import com.juggle.im.android.R;

import com.juggle.im.android.chat.call.BaseCallActivity;
import com.juggle.im.android.chat.message.LifeTimeNotifyMessage;
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
import com.juggle.im.android.chat.message.TypingNotifyMessage;
import com.juggle.im.android.chat.utils.MessageUtils;
import com.juggle.im.android.chat.view.ChatInputActionBar;
import com.juggle.im.android.event.MessageContentUpdatedEvent;
import com.juggle.im.android.event.MessageReadUpdatedEvent;
import com.juggle.im.android.event.MessageTopEvent;
import com.juggle.im.android.event.MessageUpdatedEvent;
import com.juggle.im.android.event.ReactionUpdatedEvent;
import com.juggle.im.android.model.UiMessage;
import com.juggle.im.android.server.beans.ConversationConfigBean;
import com.juggle.im.android.server.beans.GroupDetailBean;
import com.juggle.im.android.server.beans.GroupManagementBean;
import com.juggle.im.android.server.http.ApiCallback;
import com.juggle.im.android.server.http.ServiceManager;
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
import com.juggle.im.android.utils.LogUtils;
import com.juggle.im.android.utils.ToastUtils;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

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
    private long lastTypingSendTimeMs = 0L;
    private int lastTypingTriggerLength = -1;
    private boolean hasSentTypingStart = false;
    private boolean inputHasFocus = false;
    private static final long TYPING_VISIBLE_DURATION_MS = 3000L;
    private static final long TYPING_SEND_MIN_INTERVAL_MS = 1200L;
    private static final int TYPING_TRIGGER_STEP = 6;
    private static final int[] TIMED_DELETE_DAYS = new int[]{1, 7, 30, 90, 180};
    private static final long EXPIRED_MESSAGE_PRUNE_INTERVAL_MS = TimeUnit.HOURS.toMillis(1);
    private static final String FEATURE_TIMED_DELETE = "timed_delete";
    private final Handler timedDeleteHandler = new Handler(Looper.getMainLooper());
    private final Runnable expiredMessagePruneRunnable = new Runnable() {
        @Override
        public void run() {
            MessageStreamSink streamSink = findMessageStreamSink();
            if (streamSink != null) {
                streamSink.removeExpiredMessages(System.currentTimeMillis());
            }
            timedDeleteHandler.postDelayed(this, EXPIRED_MESSAGE_PRUNE_INTERVAL_MS);
        }
    };
    private int currentMessageLifeTimeDays;
    private boolean timedDeleteConfigLoaded;
    private boolean timedDeleteConfigLoading;
    private boolean timedDeleteConfigSaving;
    private boolean showTimedDeleteAfterLoad;
    private int timedDeleteStateVersion;

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
            if (isGroup) {
                inputBar.setMorePluginVisible(TimedDeletePlugin.ID, false);
            }
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
                    notifyTypingStatus(TypingNotifyMessage.TYPE_END, true);
                }

                @Override
                public void onMentionTrigger(MentionManager mentionManager) {
                    if (!isGroup) {
                        return;
                    }
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

                @Override
                public void onInputFocusChanged(boolean hasFocus) {
                    onLocalInputFocusChanged(hasFocus);
                }

                @Override
                public void onInputTextChanged(@NonNull String text) {
                    onLocalInputTextChanged(text);
                }
            });
            if (!TextUtils.isEmpty(title)) {
                inputBar.setInputHint(getString(R.string.input_msg_to, title));
            }
        }
        restoreConversationDraftToInput();
        loadTimedDeleteConfig(false);
        loadTimedDeletePermission();
        timedDeleteHandler.post(expiredMessagePruneRunnable);

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
        if (!isGroup) {
            return;
        }
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
        String operatorName = userInfo == null ? "" : userInfo.getUserName();
        String senderName = resolveMessageSenderName(message);
        tvContent.setText(senderName + "：" + MessageUtils.getMessageSummary(ConversationActivity.this, message));
        if (tvSubtitle != null) {
            tvSubtitle.setText(getString(R.string.msg_pin_by_user, operatorName));
        }
        View del = findViewById(R.id.button_del_pin);
        del.setOnClickListener(v -> {
            JIM.getInstance().getMessageManager().setTop(message.getMessageId(), conversation, false, null);
            vPin.setVisibility(GONE);
        });
        vPin.setOnClickListener(v -> {
            MessageStreamSink streamSink = findMessageStreamSink();
            if (streamSink != null) {
                streamSink.scrollToMessage(message.getMessageId(), message.getTimestamp());
            }
        });
        vPin.setVisibility(VISIBLE);
    }

    /**
     * 解析置顶消息的发送者名称。
     *
     * @param message 目标消息
     * @return 展示名称
     */
    private String resolveMessageSenderName(@Nullable Message message) {
        if (message == null) {
            return "";
        }
        String senderUserId = message.getSenderUserId();
        if (!TextUtils.isEmpty(senderUserId)) {
            UserInfo cacheUser = JIM.getInstance().getUserInfoManager().getUserInfo(senderUserId);
            if (cacheUser != null && !TextUtils.isEmpty(cacheUser.getUserName())) {
                return cacheUser.getUserName();
            }
            return senderUserId;
        }
        return "";
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
            handleTypingMessage(event.getMessage());
            return;
        }
        if (event.getMessage().getContent() instanceof LifeTimeNotifyMessage) {
            LifeTimeNotifyMessage lifeTimeNotifyMessage =
                    (LifeTimeNotifyMessage) event.getMessage().getContent();
            timedDeleteStateVersion++;
            currentMessageLifeTimeDays = Math.max(lifeTimeNotifyMessage.getType(), 0);
            timedDeleteConfigLoaded = true;
        }
        dispatchNewMessageToStream(event.getMessage());
        // tag message read
        Conversation conversation = new Conversation(
                isGroup ? Conversation.ConversationType.GROUP : Conversation.ConversationType.PRIVATE,
                conversationId);
        JIM.getInstance().getConversationManager().clearUnreadCount(conversation, null);
    }

    /**
     * 已存在消息的内容更新（如 AI 流式文本持续输出），原地刷新对应气泡。
     */
    @Subscribe(threadMode = ThreadMode.MAIN)
    public void MessageContentUpdatedEvent(MessageContentUpdatedEvent event) {
        if (event.getMessage() == null
                || event.getMessage().getConversation() == null
                || !event.getMessage().getConversation().getConversationId().equals(conversationId)) {
            return;
        }
        dispatchUpdatedMessagesToStream(Arrays.asList(event.getMessage()));
    }

    /**
     * 显示"对方正在输入…"指示器，3秒内无新 typing 消息则自动隐藏。
     */
    private void showTypingIndicator() {
        setTypingIndicatorVisible(true);
        if (typingHideRunnable != null) {
            typingHandler.removeCallbacks(typingHideRunnable);
        }
        typingHideRunnable = () -> {
            hideTypingIndicator();
        };
        typingHandler.postDelayed(typingHideRunnable, TYPING_VISIBLE_DURATION_MS);
    }

    /**
     * 隐藏"对方正在输入…"指示器。
     */
    private void hideTypingIndicator() {
        if (typingHideRunnable != null) {
            typingHandler.removeCallbacks(typingHideRunnable);
            typingHideRunnable = null;
        }
        setTypingIndicatorVisible(false);
    }

    /**
     * 清理 typing 指示器定时器。
     */
    private void cleanupTypingIndicator() {
        hideTypingIndicator();
    }

    /**
     * 处理收到的 typing 状态消息。
     *
     * <p>简要描述：严格按 START/END 控制 UI，避免 END 消息到达后仍然继续显示“正在输入”。</p>
     *
     * @param message typing 状态消息
     */
    private void handleTypingMessage(@NonNull Message message) {
        if (isGroup || message.getDirection() == Message.MessageDirection.SEND) {
            return;
        }
        if (!(message.getContent() instanceof TypingNotifyMessage)) {
            showTypingIndicator();
            return;
        }
        TypingNotifyMessage typingNotifyMessage = (TypingNotifyMessage) message.getContent();
        if (typingNotifyMessage.getType() == TypingNotifyMessage.TYPE_START) {
            showTypingIndicator();
            return;
        }
        hideTypingIndicator();
    }

    /**
     * 输入框焦点变化处理。
     *
     * @param hasFocus 输入框是否获得焦点
     */
    private void onLocalInputFocusChanged(boolean hasFocus) {
        if (isGroup) {
            return;
        }
        inputHasFocus = hasFocus;
        if (hasFocus) {
            notifyTypingStatus(TypingNotifyMessage.TYPE_START, true);
            return;
        }
        notifyTypingStatus(TypingNotifyMessage.TYPE_END, true);
    }

    /**
     * 输入框文本变化处理。
     *
     * @param text 输入框当前文本
     */
    private void onLocalInputTextChanged(@NonNull String text) {
        if (isGroup || !inputHasFocus) {
            return;
        }
        if (TextUtils.isEmpty(text)) {
            if (hasSentTypingStart) {
                notifyTypingStatus(TypingNotifyMessage.TYPE_END, true);
            }
            lastTypingTriggerLength = -1;
            return;
        }
        int length = text.length();
        if (length % TYPING_TRIGGER_STEP == 0 && length != lastTypingTriggerLength) {
            notifyTypingStatus(TypingNotifyMessage.TYPE_START, false);
            lastTypingTriggerLength = length;
        }
    }

    /**
     * 发送 typing 状态消息。
     *
     * @param typingType 输入状态类型，0-结束输入，1-开始输入
     * @param force 是否强制发送（忽略最小发送间隔）
     */
    private void notifyTypingStatus(int typingType, boolean force) {
        if (isGroup || conversation == null) {
            return;
        }
        if (typingType == TypingNotifyMessage.TYPE_END && !hasSentTypingStart) {
            return;
        }
        long now = System.currentTimeMillis();
        if (typingType == TypingNotifyMessage.TYPE_START
                && !force
                && now - lastTypingSendTimeMs < TYPING_SEND_MIN_INTERVAL_MS) {
            return;
        }
        TypingNotifyMessage typingNotifyMessage = new TypingNotifyMessage();
        typingNotifyMessage.setType(typingType);
        MessageOptions options = new MessageOptions();
        JIM.getInstance().getMessageManager().sendMessage(
                typingNotifyMessage,
                conversation,
                options,
                new IMessageManager.ISendMessageCallback() {
                    @Override
                    public void onSuccess(Message message) {
                    }

                    @Override
                    public void onError(Message message, int errorCode) {
                        Log.d("ConversationActivity", "send typing failed: " + errorCode);
                    }
                });
        lastTypingSendTimeMs = now;
        hasSentTypingStart = typingType == TypingNotifyMessage.TYPE_START;
        if (!hasSentTypingStart) {
            lastTypingTriggerLength = -1;
        }
    }

    /**
     * 控制顶部 typing 指示器显隐。
     *
     * <p>简要描述：指示器作为标题副标题，固定显示在 tv_title 下方，不再改变消息列表顶部偏移。</p>
     *
     * @param visible 是否显示 typing 指示器
     */
    private void setTypingIndicatorVisible(boolean visible) {
        TextView tvTyping = findViewById(R.id.tv_typing_indicator);
        if (tvTyping != null) {
            tvTyping.setVisibility(visible ? VISIBLE : GONE);
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
        if (event == null || event.getConversation() == null || event.getMessageReaction() == null) {
            return;
        }
        if (!event.getConversation().getConversationId().equals(conversationId)) {
            return;
        }

        String messageId = event.getMessageReaction().getMessageId();
        if (TextUtils.isEmpty(messageId)) {
            return;
        }

        MessageListFragment frag = (MessageListFragment) getSupportFragmentManager()
                .findFragmentById(R.id.fragment_messages_container);
        // 简要描述：先用 SDK 本地缓存立即刷新 UI，避免等待网络拉取导致“对方已回应但本地不更新”。
        if (frag != null) {
            frag.refreshMessageById(messageId);
        }

        List<String> messageIdList = new ArrayList<>();
        messageIdList.add(messageId);
        JIM.getInstance().getMessageManager().getMessagesReaction(
                messageIdList,
                event.getConversation(),
                new IMessageManager.IMessageReactionListCallback() {
                    @Override
                    public void onSuccess(List<MessageReaction> reactionList) {
                        Log.d("ConversationActivity", "Reactions refreshed for message: " + messageId);
                        MessageListFragment fragment = (MessageListFragment) getSupportFragmentManager()
                                .findFragmentById(R.id.fragment_messages_container);
                        if (fragment != null) {
                            fragment.refreshMessageById(messageId);
                        }
                    }

                    @Override
                    public void onError(int errorCode) {
                        Log.e("ConversationActivity", "Failed to refresh reactions: " + errorCode);
                    }
                });
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
            FileUtils.CopiedContentFile copiedFile = FileUtils.copyContentUriToCache(this, data.toString());
            FileMessage fileMessage = createFileMessageFromPickedFile(copiedFile);
            if (fileMessage == null) {
                ToastUtils.show(this, R.string.file_process_failed);
                return;
            }
            sendFileMessage(fileMessage, conversation);
        } else if (pluginId.equals(VoiceCallPlugin.ID) || pluginId.equals(VideoCallPlugin.ID)) {
            if (isGroup) {
                Intent it = new Intent(this, SelectMemberActivity.class);
                it.putExtra("GROUP_ID", conversationId);
                ArrayList<String> disabledMembers = new ArrayList<>();
                disabledMembers.add(JIM.getInstance().getCurrentUserId());
                it.putStringArrayListExtra(DISABLE_MEMBERS, disabledMembers);
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

    /**
     * 将文件选择结果转换为可发送的 FileMessage。
     *
     * <p>简要描述：这里统一补齐 localPath/name/size，避免出现“文件名被截断”或“全部显示为临时 jpg 名称”的问题。</p>
     *
     * @param copiedFile 文件复制结果
     * @return 可发送的 FileMessage；若本地文件无效则返回 null
     */
    @Nullable
    private FileMessage createFileMessageFromPickedFile(@Nullable FileUtils.CopiedContentFile copiedFile) {
        if (copiedFile == null || TextUtils.isEmpty(copiedFile.getLocalPath())) {
            return null;
        }
        File localFile = new File(copiedFile.getLocalPath());
        if (!localFile.exists() || !localFile.isFile()) {
            return null;
        }
        FileMessage fileMessage = new FileMessage();
        fileMessage.setLocalPath(localFile.getAbsolutePath());
        fileMessage.setName(FileUtils.resolveAttachmentDisplayName(copiedFile.getDisplayName(), localFile.getAbsolutePath()));
        fileMessage.setSize(localFile.length());
        return fileMessage;
    }

    private void showTimedDeleteSelector() {
        if (!timedDeleteConfigLoaded) {
            loadTimedDeleteConfig(true);
            return;
        }
        if (timedDeleteConfigSaving || isFinishing() || isDestroyed()) {
            return;
        }
        showTimedDeleteBottomSheet();
    }

    /**
     * 读取当前会话保存的消息自动删除周期。
     *
     * @param showSelectorAfterLoad 加载成功后是否立即打开选择弹层
     */
    private void loadTimedDeleteConfig(boolean showSelectorAfterLoad) {
        if (conversation == null) {
            return;
        }
        showTimedDeleteAfterLoad = showTimedDeleteAfterLoad || showSelectorAfterLoad;
        if (timedDeleteConfigLoaded) {
            if (showTimedDeleteAfterLoad) {
                showTimedDeleteAfterLoad = false;
                showTimedDeleteBottomSheet();
            }
            return;
        }
        if (timedDeleteConfigLoading) {
            return;
        }
        timedDeleteConfigLoading = true;
        final int requestStateVersion = timedDeleteStateVersion;
        ServiceManager.getUserService().getConversationConfig(
                conversation.getConversationId(),
                conversation.getConversationType().getValue(),
                conversation.getSubChannel(),
                new ApiCallback<ConversationConfigBean>() {
                    @Override
                    public void onSuccess(ConversationConfigBean data) {
                        timedDeleteConfigLoading = false;
                        // TIPS：若加载期间收到其他端的配置通知，保留更新后的状态，避免旧响应回写覆盖。
                        if (requestStateVersion == timedDeleteStateVersion) {
                            timedDeleteStateVersion++;
                            timedDeleteConfigLoaded = true;
                            currentMessageLifeTimeDays = data == null
                                    ? 0
                                    : Math.max(data.getMessageLifeTimeDays(), 0);
                        }
                        if (showTimedDeleteAfterLoad && !isFinishing() && !isDestroyed()) {
                            showTimedDeleteAfterLoad = false;
                            showTimedDeleteBottomSheet();
                        }
                    }

                    @Override
                    public void onError(int code, String message) {
                        timedDeleteConfigLoading = false;
                        LogUtils.serverError(FEATURE_TIMED_DELETE, "load", code, message);
                        if (showTimedDeleteAfterLoad && !isFinishing() && !isDestroyed()) {
                            showTimedDeleteAfterLoad = false;
                            ToastUtils.show(ConversationActivity.this, R.string.timed_delete_load_failed);
                        }
                    }
                });
    }

    /**
     * 根据群管理权限控制定时删除插件入口。
     */
    private void loadTimedDeletePermission() {
        if (!isGroup) {
            return;
        }
        ServiceManager.getUserService().getGroupInfo(conversationId, new ApiCallback<GroupDetailBean>() {
            @Override
            public void onSuccess(GroupDetailBean data) {
                if (isFinishing() || isDestroyed()) {
                    return;
                }
                GroupManagementBean management = data == null ? null : data.getGroupManagement();
                int settingRight = management == null
                        ? 0
                        : management.getGroupSetMsgLifeRight();
                int memberRole = data == null ? -1 : data.getMyRole();
                boolean visible = GroupManagementRoleHelper.hasSettingPermission(settingRight, memberRole);
                ChatInputActionBar inputBar = findViewById(R.id.input_bar);
                if (inputBar != null) {
                    inputBar.setMorePluginVisible(TimedDeletePlugin.ID, visible);
                }
            }

            @Override
            public void onError(int code, String message) {
                LogUtils.serverError(FEATURE_TIMED_DELETE, "loadPermission", code, message);
            }
        });
    }

    /**
     * 展示符合 App 视觉规范的定时删除底部选择弹层。
     */
    private void showTimedDeleteBottomSheet() {
        BottomSheetDialog dialog = new BottomSheetDialog(this, R.style.TransparentBottomSheetDialog);
        dialog.setContentView(R.layout.dialog_timed_delete_sheet);
        dialog.setCanceledOnTouchOutside(true);
        LinearLayout optionsContainer = dialog.findViewById(R.id.layout_timed_delete_options);
        if (optionsContainer == null) {
            return;
        }

        boolean showTurnOff = currentMessageLifeTimeDays > 0;
        for (int i = 0; i < TIMED_DELETE_DAYS.length; i++) {
            int days = TIMED_DELETE_DAYS[i];
            boolean lastItem = !showTurnOff && i == TIMED_DELETE_DAYS.length - 1;
            addTimedDeleteOption(dialog, optionsContainer, days, lastItem, false);
        }
        if (showTurnOff) {
            addTimedDeleteOption(dialog, optionsContainer, 0, true, true);
        }
        dialog.setOnShowListener(ignored -> {
            View bottomSheet = dialog.findViewById(com.google.android.material.R.id.design_bottom_sheet);
            if (bottomSheet != null) {
                bottomSheet.setBackgroundColor(Color.TRANSPARENT);
            }
        });
        dialog.show();
    }

    /**
     * 添加定时删除弹层选项。
     *
     * @param dialog 所属弹层
     * @param container 选项容器
     * @param days 自动删除天数，0 表示关闭
     * @param lastItem 是否为最后一项
     * @param warning 是否使用警示色
     */
    private void addTimedDeleteOption(@NonNull BottomSheetDialog dialog,
                                      @NonNull LinearLayout container,
                                      int days,
                                      boolean lastItem,
                                      boolean warning) {
        View itemView = LayoutInflater.from(this)
                .inflate(R.layout.item_timed_delete_option, container, false);
        TextView titleView = itemView.findViewById(R.id.tv_timed_delete_option);
        ImageView checkedView = itemView.findViewById(R.id.iv_timed_delete_checked);
        View dividerView = itemView.findViewById(R.id.v_timed_delete_divider);

        titleView.setText(resolveTimedDeleteLabel(days));
        if (warning) {
            titleView.setTextColor(getColor(R.color.red));
        }
        checkedView.setVisibility(days > 0 && days == currentMessageLifeTimeDays ? VISIBLE : View.INVISIBLE);
        dividerView.setVisibility(lastItem ? GONE : VISIBLE);
        itemView.setOnClickListener(v -> {
            dialog.dismiss();
            if (days == currentMessageLifeTimeDays) {
                return;
            }
            saveTimedDeleteSetting(days);
        });
        container.addView(itemView);
    }

    /**
     * 保存会话自动删除周期，成功后再更新本地状态并发送状态通知。
     *
     * @param days 自动删除天数，0 表示关闭
     */
    private void saveTimedDeleteSetting(int days) {
        if (conversation == null || timedDeleteConfigSaving) {
            return;
        }
        timedDeleteConfigSaving = true;
        ServiceManager.getUserService().setConversationMessageLifeTime(
                conversation.getConversationId(),
                conversation.getConversationType().getValue(),
                conversation.getSubChannel(),
                days,
                new ApiCallback<Void>() {
                    @Override
                    public void onSuccess(Void data) {
                        timedDeleteConfigSaving = false;
                        timedDeleteConfigLoaded = true;
                        timedDeleteStateVersion++;
                        currentMessageLifeTimeDays = Math.max(days, 0);
                        sendTimedDeleteNotification(currentMessageLifeTimeDays);
                        if (currentMessageLifeTimeDays == 0) {
                            ToastUtils.show(ConversationActivity.this, R.string.timed_delete_turned_off);
                        } else {
                            ToastUtils.show(ConversationActivity.this,
                                    getString(R.string.timed_delete_selected,
                                            resolveTimedDeleteLabel(currentMessageLifeTimeDays)));
                        }
                    }

                    @Override
                    public void onError(int code, String message) {
                        timedDeleteConfigSaving = false;
                        LogUtils.serverError(FEATURE_TIMED_DELETE, "save", code, message);
                        if (!isFinishing() && !isDestroyed()) {
                            ToastUtils.show(ConversationActivity.this, R.string.timed_delete_save_failed);
                        }
                    }
                });
    }

    /**
     * 向会话发送定时删除配置变化通知。
     *
     * @param days 自动删除天数，0 表示关闭
     */
    private void sendTimedDeleteNotification(int days) {
        LifeTimeNotifyMessage content = new LifeTimeNotifyMessage();
        content.setType(days);
        MessageOptions options = new MessageOptions();
        if (days > 0) {
            options.setLifeTime(TimeUnit.DAYS.toMillis(days));
        }
        IMessageManager.ISendMessageCallback callback = new IMessageManager.ISendMessageCallback() {
            @Override
            public void onSuccess(Message message) {
                dispatchUpdatedMessagesToStream(Arrays.asList(message));
            }

            @Override
            public void onError(Message message, int errorCode) {
                LogUtils.e("ConversationActivity", "-", FEATURE_TIMED_DELETE,
                        "sendNotification", "error", "code=" + errorCode);
                dispatchUpdatedMessagesToStream(Arrays.asList(message));
            }
        };
        Message message = JIM.getInstance().getMessageManager()
                .sendMessage(content, conversation, options, callback);
        dispatchNewMessageToStream(message);
    }

    /**
     * 返回定时删除周期对应的本地化文案。
     *
     * @param days 自动删除天数，0 表示关闭
     * @return 本地化周期文案
     */
    private String resolveTimedDeleteLabel(int days) {
        switch (days) {
            case 1:
                return getString(R.string.timed_delete_1_day);
            case 7:
                return getString(R.string.timed_delete_7_days);
            case 30:
                return getString(R.string.timed_delete_30_days);
            case 90:
                return getString(R.string.timed_delete_90_days);
            case 180:
                return getString(R.string.timed_delete_180_days);
            default:
                return getString(R.string.timed_delete_off);
        }
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

    /**
     * 为当前会话的新消息补充自动删除周期。
     *
     * <p>TIPS：发送方法也用于转发，只有目标等于当前会话时才附加周期，避免把当前配置带到其他会话。</p>
     *
     * @param options 原始发送参数，可为空
     * @param targetConversation 实际发送目标
     * @return 可直接传给 SDK 的发送参数
     */
    @NonNull
    private MessageOptions applyCurrentMessageLifeTime(@Nullable MessageOptions options,
                                                       @Nullable Conversation targetConversation) {
        MessageOptions safeOptions = options == null ? new MessageOptions() : options;
        if (conversation != null && conversation.equals(targetConversation)) {
            long lifeTimeMillis = currentMessageLifeTimeDays > 0
                    ? TimeUnit.DAYS.toMillis(currentMessageLifeTimeDays)
                    : 0L;
            safeOptions.setLifeTime(lifeTimeMillis);
        }
        return safeOptions;
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
        MessageOptions sendOptions = applyCurrentMessageLifeTime(options, conversation);
        Message message = JIM.getInstance().getMessageManager()
                .sendMessage(text, conversation, sendOptions, callback);
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
        MessageOptions sendOptions = applyCurrentMessageLifeTime(options, conversation);
        Message message = JIM.getInstance().getMessageManager()
                .sendMediaMessage(image, conversation, sendOptions, callback);
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

        MessageOptions sendOptions = applyCurrentMessageLifeTime(null, conversation);
        Message message = JIM.getInstance().getMessageManager()
                .sendMediaMessage(fileMessage, conversation, sendOptions, callback);
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
        MessageOptions sendOptions = applyCurrentMessageLifeTime(null, conversation);
        Message message = JIM.getInstance().getMessageManager()
                .sendMediaMessage(voice, conversation, sendOptions, callback);
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
        MessageOptions sendOptions = applyCurrentMessageLifeTime(null, targetConv);
        Message m = JIM.getInstance().getMessageManager().sendMessage(merge, targetConv, sendOptions,
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
        onLocalInputFocusChanged(false);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        cleanupTypingIndicator();
        timedDeleteHandler.removeCallbacks(expiredMessagePruneRunnable);
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

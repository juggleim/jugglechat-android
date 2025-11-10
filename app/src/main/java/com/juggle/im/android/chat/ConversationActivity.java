package com.juggle.im.android.chat;

import static android.view.View.GONE;
import static android.view.View.VISIBLE;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import com.juggle.im.JIM;
import com.juggle.im.android.R;

import com.juggle.im.android.chat.utils.FileUtils;
import com.juggle.im.android.chat.utils.MessageUtils;
import com.juggle.im.android.chat.view.ChatInputActionBar;
import com.juggle.im.android.event.MessageReadUpdatedEvent;
import com.juggle.im.android.event.MessageTopEvent;
import com.juggle.im.android.event.MessageUpdatedEvent;
import com.juggle.im.android.model.UiMessage;
import com.juggle.im.android.utils.AvatarUtils;
import com.juggle.im.interfaces.IMessageManager;
import com.juggle.im.model.Conversation;
import com.juggle.im.model.MergeMessagePreviewUnit;
import com.juggle.im.model.Message;
import com.juggle.im.model.MessageMentionInfo;
import com.juggle.im.model.MessageOptions;
import com.juggle.im.model.PushData;
import com.juggle.im.model.UserInfo;
import com.juggle.im.model.messages.FileMessage;
import com.juggle.im.model.messages.ImageMessage;
import com.juggle.im.model.messages.MergeMessage;
import com.juggle.im.model.messages.TextMessage;
import com.juggle.im.model.messages.VoiceMessage;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class ConversationActivity extends AppCompatActivity {
    public static final String EXTRA_CONVERSATION_ID = "extra_conversation_id";
    public static final String EXTRA_IS_GROUP = "extra_is_group";
    public static final String EXTRA_TITLE = "extra_title";
    public static final String EXTRA_IS_TOP = "extra_is_top";
    public static final String EXTRA_IS_MUTE = "extra_is_mute";
    public static final int REQ_FORWARD = 2001;
    private boolean isGroup;
    private String conversationId;
    private Conversation conversation;

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

    // Overload to include a human-readable title for the conversation (UiConversation.name)
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
            ivBack.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    finish();
                }
            });
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

        if (savedInstanceState == null) {
            conversationId = getIntent().getStringExtra(EXTRA_CONVERSATION_ID);
            isGroup = getIntent().getBooleanExtra(EXTRA_IS_GROUP, false);
            conversation = new Conversation(
                    isGroup ? Conversation.ConversationType.GROUP : Conversation.ConversationType.PRIVATE,
                    conversationId);
            MessageListFragment frag = MessageListFragment.newInstance(conversationId, isGroup);
            getSupportFragmentManager()
                    .beginTransaction()
                    .replace(R.id.fragment_messages_container, frag)
                    .commit();
        }

        // wire up input bar
        ChatInputActionBar inputBar = findViewById(R.id.input_bar);
        if (inputBar != null) {
            inputBar.setListener(new ChatInputActionBar.Listener() {
                public void onSend(String text, String replyMsgId, MessageMentionInfo mentionInfo) {
                    TextMessage msg = new TextMessage(text);
                    MessageOptions options = new MessageOptions();
                    PushData pushData = new PushData();
                    pushData.setContent(text);
                    options.setPushData(pushData);
                    options.setMentionInfo(mentionInfo);
                    options.setReferredMessageId(replyMsgId);
                    sendTextMessage(msg, options, conversation);
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
                    // plugin may directly return data (Uri/Bitmap) or just notify action; delegate to handler
                    if (data != null) {
                        handlePluginResult(pluginId, action, data);
                    } else {
                        // no data yet: plugin most likely requested permissions and will retry, or will call again when done
                    }
                }

                @Override
                public void onPanelVisibilityChanged(boolean visible) {
                    scrollMessageListIfNeed(visible);
                }

                @Override
                public void onKeyboardVisibilityChanged(boolean visible) {
                    if (visible) {
                        // when keyboard shows, ensure messages are scrolled to bottom so input isn't obscured
                        MessageListFragment frag = (MessageListFragment) getSupportFragmentManager().findFragmentById(R.id.fragment_messages_container);
                        if (frag != null) frag.scrollToBottomIfNeeded();
                    }
                }
            });
        }

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
    }

    private void handleTopMessage(Message message, UserInfo userInfo) {
        View vPin = findViewById(R.id.layout_pin_message);
        TextView tvContent = vPin.findViewById(R.id.pin_message_content);
        tvContent.setText(userInfo.getUserName() + "：" + MessageUtils.getMessageSummary(ConversationActivity.this, message));
        View del = findViewById(R.id.button_del_pin);
        del.setOnClickListener( v -> {
            JIM.getInstance().getMessageManager().setTop(message.getMessageId(), conversation, false, null);
            vPin.setVisibility(GONE);
        });
        vPin.setVisibility(VISIBLE);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        ChatInputActionBar inputBar = findViewById(R.id.input_bar);
        boolean handled = false;
        if (inputBar != null) {
            handled = inputBar.onActivityResult(requestCode, resultCode, data);
        }
        // If not handled by plugins, you may still want to handle other requestCodes here.
        if (!handled && requestCode == REQ_FORWARD && resultCode == RESULT_OK && data != null) {
            String targetConvId = data.getStringExtra(ForwardConversationListActivity.EXTRA_CONVERSATION_ID);
            String targetName = data.getStringExtra(ForwardConversationListActivity.EXTRA_CONVERSATION_NAME);
            boolean targetIsGroup = data.getBooleanExtra(ForwardConversationListActivity.EXTRA_IS_GROUP, false);
            String mode = data.getStringExtra(ForwardConversationListActivity.EXTRA_FORWARD_MODE);
            // obtain the message list fragment to get selected messages
            MessageListFragment frag = (MessageListFragment) getSupportFragmentManager().findFragmentById(R.id.fragment_messages_container);
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
                        if (um == null || um.getMessage() == null) continue;
                        Message m = um.getMessage();
                        if (m.getContent() instanceof TextMessage) {
                            sendTextMessage(new TextMessage(((TextMessage) m.getContent()).getContent()), null, targetConv);
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
        }
    }

    private void scrollMessageListIfNeed(boolean panelVisible) {
        // find the fragment and notify it when a panel opens so it can scroll to bottom if needed
        MessageListFragment frag = (MessageListFragment) getSupportFragmentManager().findFragmentById(R.id.fragment_messages_container);
        if (frag != null) {
            if (panelVisible) {
                frag.scrollToBottomIfNeeded();
            } else {
                // nothing special for hide currently
            }
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
        // New single message received via EventBus -> append to the end (newest)
        if (!event.getMessage().getConversation().getConversationId().equals(conversationId)) {
            return;
        }
        Message m = event.getMessage();
        if (m == null || !MessageUtils.shownInMessageList(m)) return;
        MessageListFragment frag = (MessageListFragment) getSupportFragmentManager().findFragmentById(R.id.fragment_messages_container);
        if (frag != null) {
            frag.onNewMessage(m);
        }
        //tag message read
        Conversation conversation = new Conversation(
                isGroup ? Conversation.ConversationType.GROUP : Conversation.ConversationType.PRIVATE,
                conversationId);
        JIM.getInstance().getConversationManager().clearUnreadCount(conversation, null);
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void MessageReadUpdatedEvent(MessageReadUpdatedEvent event) {
        if (!event.getConversation().getConversationId().equals(conversationId)) {
            return;
        }
        MessageListFragment frag = (MessageListFragment) getSupportFragmentManager().findFragmentById(R.id.fragment_messages_container);
        if (frag != null) {
            List<Message> messages = JIM.getInstance().getMessageManager().getMessagesByMessageIds(event.getMessageIds());
            frag.onUpdateMessage(messages);
        }
    }

    private void handlePluginResult(String pluginId, String action, Object data) {
        if (pluginId.equals("photo")) {
            for (String url : (ArrayList<String>) data) {
                ImageMessage image = new ImageMessage();
                image.setHeight(600);
                image.setWidth(800);
                String fileUrl = FileUtils.convertContentUriToFile(this, url);
                image.setLocalPath(fileUrl);
                image.setThumbnailLocalPath(fileUrl);
                sendImageMessage(image, null, conversation);
            }
        } else if (pluginId.equals("camera")) {
            ImageMessage image = new ImageMessage();
            image.setHeight(600);
            image.setWidth(800);
            String fileUrl = FileUtils.convertContentUriToFile(this, data.toString());
            image.setLocalPath(fileUrl);
            image.setThumbnailLocalPath(fileUrl);
            sendImageMessage(image, null, conversation);
        } else if (pluginId.equals("location")) {
        } else if (pluginId.equals("contact")) {

        } else if (pluginId.equals("file")) {
            String fileUrl = FileUtils.convertContentUriToFile(this, data.toString());
            FileMessage fileMessage = new FileMessage();
            File f = new File(fileUrl);
            fileMessage.setLocalPath(fileUrl);
            fileMessage.setName(f.getName().length() > 10 ? f.getName().substring(0, 10) : f.getName());
            long size = f.length();
            fileMessage.setSize(size);
            sendFileMessage(fileMessage, conversation);
        }
    }

    private void sendTextMessage(TextMessage text, MessageOptions options, Conversation conversation) {
        IMessageManager.ISendMessageCallback callback = new IMessageManager.ISendMessageCallback() {
            @Override
            public void onSuccess(Message message) {
                MessageListFragment frag = (MessageListFragment) getSupportFragmentManager().findFragmentById(R.id.fragment_messages_container);
                if (frag != null) {
                    frag.onUpdateMessage(Arrays.asList(message));
                }
            }

            @Override
            public void onError(Message message, int errorCode) {
                Log.i("TAG", "send message error: " + errorCode);
                MessageListFragment frag = (MessageListFragment) getSupportFragmentManager().findFragmentById(R.id.fragment_messages_container);
                if (frag != null) {
                    frag.onUpdateMessage(Arrays.asList(message));
                }
            }
        };
        Message message = JIM.getInstance().getMessageManager().sendMessage(text, conversation, options, callback);
        MessageListFragment frag = (MessageListFragment) getSupportFragmentManager().findFragmentById(R.id.fragment_messages_container);
        if (frag != null) {
            frag.onNewMessage(message);
        }
    }

    private void sendImageMessage(ImageMessage image, MessageOptions options, Conversation conversation) {
        final MessageListFragment frag = (MessageListFragment) getSupportFragmentManager().findFragmentById(R.id.fragment_messages_container);
        IMessageManager.ISendMediaMessageCallback callback = new IMessageManager.ISendMediaMessageCallback() {
            @Override
            public void onProgress(int progress, Message message) {
                Log.i("sendImageMessage", "onProgress: " + progress);
            }

            @Override
            public void onSuccess(Message message) {
                Log.i("sendImageMessage", "send message success");
                frag.onUpdateMessage(Arrays.asList(message));
            }

            @Override
            public void onError(Message message, int errorCode) {
                Log.i("sendImageMessage", "send message error: " + errorCode);
                frag.onUpdateMessage(Arrays.asList(message));
            }

            @Override
            public void onCancel(Message message) {
                Log.i("sendImageMessage", "onCancel");
            }
        };
        Message message = JIM.getInstance().getMessageManager().sendMediaMessage(image, conversation, callback);
        Log.i("TAG", "sendImageMessage msgId= " + message.getMessageId());
        if (frag != null) {
            frag.onNewMessage(message);
        }
    }

    public void sendFileMessage(FileMessage fileMessage, Conversation conversation) {
        MessageListFragment frag = (MessageListFragment) getSupportFragmentManager().findFragmentById(R.id.fragment_messages_container);
        IMessageManager.ISendMediaMessageCallback callback = new IMessageManager.ISendMediaMessageCallback() {
            @Override
            public void onProgress(int progress, Message message) {
                Log.i("TAG", "onProgress");
            }

            @Override
            public void onSuccess(Message message) {
                Log.i("TAG", "send message success");
                frag.onUpdateMessage(Arrays.asList(message));

            }

            @Override
            public void onError(Message message, int errorCode) {
                Log.i("TAG", "send message error: " + errorCode);
                frag.onUpdateMessage(Arrays.asList(message));

            }

            @Override
            public void onCancel(Message message) {
                Log.i("TAG", "onCancel");
            }
        };

        Message message = JIM.getInstance().getMessageManager().sendMediaMessage(fileMessage, conversation, callback);
        Log.i("TAG", "after send, clientMsgNo is " + message.getClientMsgNo());
        frag.onNewMessage(message);
    }

    private void sendVoiceMessage(VoiceMessage voice, Conversation conversation) {
        MessageListFragment frag = (MessageListFragment) getSupportFragmentManager().findFragmentById(R.id.fragment_messages_container);
        IMessageManager.ISendMediaMessageCallback callback = new IMessageManager.ISendMediaMessageCallback() {
            @Override
            public void onProgress(int progress, Message message) {
                Log.i("TAG", "onProgress");
            }

            @Override
            public void onSuccess(Message message) {
                Log.i("TAG", "send message success");
                frag.onUpdateMessage(Arrays.asList(message));
            }

            @Override
            public void onError(Message message, int errorCode) {
                Log.i("TAG", "send message error: " + errorCode);
                frag.onUpdateMessage(Arrays.asList(message));
            }

            @Override
            public void onCancel(Message message) {
                Log.i("TAG", "onCancel");
            }
        };
        Message message = JIM.getInstance().getMessageManager().sendMediaMessage(voice, conversation, callback);
        Log.i("TAG", "after send, clientMsgNo is " + message.getClientMsgNo());
        frag.onNewMessage(message);
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
        MessageListFragment frag = (MessageListFragment) getSupportFragmentManager().findFragmentById(R.id.fragment_messages_container);
        Message m = JIM.getInstance().getMessageManager().sendMessage(merge, targetConv, new IMessageManager.ISendMessageCallback() {
            @Override
            public void onSuccess(Message message) {
                frag.onUpdateMessage(Arrays.asList(message));
            }

            @Override
            public void onError(Message message, int errorCode) {
                Log.i("TAG", "send message error: " + errorCode);
                frag.onUpdateMessage(Arrays.asList(message));
            }
        });
        frag.onNewMessage(m);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        ChatInputActionBar inputBar = findViewById(R.id.input_bar);
        if (inputBar != null) {
            inputBar.onRequestPermissionsResult(requestCode, permissions, grantResults);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        EventBus.getDefault().unregister(this);
    }
}

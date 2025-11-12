package com.juggle.im.android.chat.utils;

import android.annotation.SuppressLint;
import android.content.Context;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;

import com.juggle.im.JIM;
import com.juggle.im.android.R;
import com.juggle.im.android.chat.message.FriendNotifyMessage;
import com.juggle.im.android.chat.message.GroupNotifyMessage;
import com.juggle.im.android.chat.message.InsertTimeStatusMessage;
import com.juggle.im.android.chat.provider.FileMessageView;
import com.juggle.im.android.chat.provider.ImageMessageView;
import com.juggle.im.android.chat.provider.MergeMessageView;
import com.juggle.im.android.chat.provider.MessageView;
import com.juggle.im.android.chat.provider.StatusMessageView;
import com.juggle.im.android.chat.provider.TextMessageView;
import com.juggle.im.android.chat.provider.VoiceMessageView;
import com.juggle.im.android.model.UiMessage;
import com.juggle.im.call.model.CallFinishNotifyMessage;
import com.juggle.im.model.Conversation;
import com.juggle.im.android.model.LocalMessage;
import com.juggle.im.model.Message;
import com.juggle.im.model.MessageContent;

import com.juggle.im.model.UserInfo;
import com.juggle.im.model.messages.FileMessage;
import com.juggle.im.model.messages.ImageMessage;
import com.juggle.im.model.messages.MergeMessage;
import com.juggle.im.model.messages.RecallInfoMessage;
import com.juggle.im.model.messages.TextMessage;
import com.juggle.im.model.messages.UnknownMessage;
import com.juggle.im.model.messages.VoiceMessage;

import java.lang.reflect.Constructor;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;


public class MessageUtils {
    public static final Map<Class<? extends MessageContent>, Class<? extends MessageView>> messageViewCache = new HashMap<>();
    public static final Map<Class<? extends MessageView>, Integer> messageTemplateCache = new HashMap<>();


    static {
        registerMessageView(TextMessage.class, TextMessageView.class);
        registerMessageView(ImageMessage.class, ImageMessageView.class);
        registerMessageView(VoiceMessage.class, VoiceMessageView.class);
        registerMessageView(FileMessage.class, FileMessageView.class);
        registerMessageView(MergeMessage.class, MergeMessageView.class);
        registerMessageView(UnknownMessage.class, StatusMessageView.class);
        registerMessageView(FriendNotifyMessage.class, StatusMessageView.class);
        registerMessageView(GroupNotifyMessage.class, StatusMessageView.class);
        registerMessageView(InsertTimeStatusMessage.class, StatusMessageView.class);
        registerMessageView(RecallInfoMessage.class, StatusMessageView.class);
        registerMessageView(CallFinishNotifyMessage.class, StatusMessageView.class);

        registerMessageViewTemplate(StatusMessageView.class, R.layout.item_message_notification);
    }

    /**
     * Decide whether to show a time/status message before the current message based on previous message.
     * Rules:
     * - If prev is null -> show time (first message or user just entered)
     * - If date part differs (cross-day) -> show time
     * - If gap > 5 minutes -> show time
     */
    public static boolean shouldInsertTimeBefore(UiMessage prev, UiMessage cur) {
        if (cur == null) return false;
        if (prev == null) return true; // first message -> show time
        long t1 = prev.getTimestamp();
        long t2 = cur.getTimestamp();
        // if cross-day (different yyyyMMdd)
        Calendar c1 = Calendar.getInstance();
        Calendar c2 = Calendar.getInstance();
        c1.setTimeInMillis(t1);
        c2.setTimeInMillis(t2);
        if (c1.get(Calendar.YEAR) != c2.get(Calendar.YEAR)
                || c1.get(Calendar.DAY_OF_YEAR) != c2.get(Calendar.DAY_OF_YEAR)) {
            return true;
        }
        // if gap > 5 minutes (300000 ms)
//        if (Math.abs(t2 - t1) > 5 * 60 * 1000L) return true;
        return false;
    }

    /** Create a UiMessage wrapping an InsertTimeStatusMessage for the given timestamp. */
    public static UiMessage createInsertTimeUiMessage(long timestamp) {
        com.juggle.im.android.chat.message.InsertTimeStatusMessage st = new com.juggle.im.android.chat.message.InsertTimeStatusMessage();

        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(timestamp);

        Calendar now = Calendar.getInstance();

        String text;
        // same day -> show HH:mm
        boolean sameYear = now.get(Calendar.YEAR) == cal.get(Calendar.YEAR);
        boolean sameDay = sameYear && now.get(Calendar.DAY_OF_YEAR) == cal.get(Calendar.DAY_OF_YEAR);

        // yesterday
        Calendar yesterday = (Calendar) now.clone();
        yesterday.add(Calendar.DAY_OF_YEAR, -1);
        boolean isYesterday = sameYear && yesterday.get(Calendar.DAY_OF_YEAR) == cal.get(Calendar.DAY_OF_YEAR);

        if (sameDay) {
            java.text.DateFormat df = new java.text.SimpleDateFormat("HH:mm", Locale.getDefault());
            text = "今天";// df.format(new Date(timestamp));
        } else if (isYesterday) {
            java.text.DateFormat df = new SimpleDateFormat("HH:mm", Locale.getDefault());
            text = "昨天 " + df.format(new Date(timestamp));
        } else if (sameYear) {
            DateFormat df = new SimpleDateFormat("MM-dd HH:mm", Locale.getDefault());
            text = df.format(new Date(timestamp));
        } else {
            DateFormat df = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault());
            text = df.format(new Date(timestamp));
        }
        st.setDescription(text);
        // construct a local Message wrapper to feed UiMessage.fromMessage
        LocalMessage lm = new LocalMessage(timestamp, st);
        lm.setDirection(Message.MessageDirection.RECEIVE);
        lm.setSenderUserId(null);
        UiMessage um = UiMessage.fromMessage(lm);
        return um;
    }

    public static String formateConversationTime(long timestamp) {
        com.juggle.im.android.chat.message.InsertTimeStatusMessage st = new com.juggle.im.android.chat.message.InsertTimeStatusMessage();

        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(timestamp);

        Calendar now = Calendar.getInstance();

        String text;
        // same day -> show HH:mm
        boolean sameYear = now.get(Calendar.YEAR) == cal.get(Calendar.YEAR);
        boolean sameDay = sameYear && now.get(Calendar.DAY_OF_YEAR) == cal.get(Calendar.DAY_OF_YEAR);

        // yesterday
        Calendar yesterday = (Calendar) now.clone();
        yesterday.add(Calendar.DAY_OF_YEAR, -1);
        boolean isYesterday = sameYear && yesterday.get(Calendar.DAY_OF_YEAR) == cal.get(Calendar.DAY_OF_YEAR);

        if (sameDay) {
            DateFormat df = new SimpleDateFormat("HH:mm", Locale.getDefault());
            text = df.format(new Date(timestamp));
        } else if (isYesterday) {
            DateFormat df = new SimpleDateFormat("HH:mm", Locale.getDefault());
            text = "昨天 " + df.format(new Date(timestamp));
        } else if (sameYear) {
            DateFormat df = new SimpleDateFormat("MM-dd", Locale.getDefault());
            text = df.format(new Date(timestamp));
        } else {
            DateFormat df = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
            text = df.format(new Date(timestamp));
        }
        return text;
    }

    public static void registerMessageView(Class<? extends MessageContent> message, Class<? extends MessageView> holder) {
        messageViewCache.put(message, holder);
    }

    public static void registerMessageViewTemplate(Class<? extends MessageView> message, int resId) {
        messageTemplateCache.put(message, resId);
    }

    public static int getMessageViewTemplate(UiMessage msg) {
        Class<? extends MessageView> holderClass = messageViewCache.get(msg.getMessage().getContent().getClass());
        Integer resId = messageTemplateCache.get(holderClass);
        if (resId == null) {
            if (msg.getMessage().getDirection() == Message.MessageDirection.SEND) {
                resId = R.layout.item_message_sent;
            } else {
                resId = R.layout.item_message_received;
            }
        }
        return resId;
    }

    @SuppressLint("ClickableViewAccessibility")
    public static MessageView createMessageViewHolder(@NonNull UiMessage msg, @NonNull ViewGroup container) {
        Class<? extends MessageView> holderClass = messageViewCache.get(msg.getMessage().getContent().getClass());
        if (holderClass == null) {
            throw new IllegalArgumentException("No ViewHolder registered for: " + msg.getMessage().getContent().getClass().getSimpleName());
        }
        try {
            Constructor<? extends MessageView> constructor = holderClass.getConstructor(ViewGroup.class);
            MessageView holder = constructor.newInstance(container);
            container.addView(holder.itemView);
            return holder;
        } catch (Exception e) {
            throw new RuntimeException("Failed to instantiate ViewHolder for " + msg.getMessage().getContent().getClass().getSimpleName(), e);
        }
    }


    /**
     * 格式化会话列表中的最后一条消息显示内容
     *
     * @param message
     * @return
     */
    public static String formatChatListMessageSummary(View view, String senderName, Message message) {
        String content = message.getConversation().getConversationType().equals(Conversation.ConversationType.PRIVATE) ?
                "%s" : senderName + ": %s";
        if (message.getContent() instanceof TextMessage) {
            return String.format(content, ((TextMessage) message.getContent()).getContent());
        } else if (message.getContent() instanceof ImageMessage) {
            return String.format(content, view.getResources().getString(R.string.msg_image));
        } else if (message.getContent() instanceof VoiceMessage) {
            return String.format(content, view.getResources().getString(R.string.msg_voice));
        } else if (message.getContent() instanceof FileMessage) {
            return String.format(content, view.getResources().getString(R.string.msg_file));
        } else if (message.getContent() instanceof MergeMessage) {
            return String.format(content, view.getResources().getString(R.string.history_messages));
        } else if (message.getContent() instanceof RecallInfoMessage) {
            return  senderName + String.format(content, view.getResources().getString(R.string.msg_recall));
        } else if (message.getContent() instanceof CallFinishNotifyMessage) {
            return "通话结束";
        }
        // notify message
        else if (message.getContentType().equals("jgd:grpntf")) {
            return view.getResources().getString(R.string.msg_group_notify);
        } else if (message.getContentType().equals("jgd:friendntf")) {
            return view.getResources().getString(R.string.msg_friend_notify);
        } else {
            Log.i("formater", "conv msg: " + message.getConversation().getConversationType().toString());
            return String.format(content, message.getContent().toString());
        }
    }

    public static String getStatusMessageSummary(MessageContent t, UserInfo userInfo) {
        if (t instanceof GroupNotifyMessage) {
            GroupNotifyMessage msg = (GroupNotifyMessage) t;
            return msg.description();
        } else if (t instanceof FriendNotifyMessage) {
            FriendNotifyMessage msg = (FriendNotifyMessage) t;
            return (userInfo != null ? userInfo.getUserName() : "") + msg.description() + "你为好友";
        } else if (t instanceof InsertTimeStatusMessage) {
            InsertTimeStatusMessage msg = (InsertTimeStatusMessage) t;
            return msg.description();
        } else if (t instanceof RecallInfoMessage) {
            return (userInfo != null ? userInfo.getUserName() : "") + "撤回了一条消息";
        } else if (t instanceof CallFinishNotifyMessage) {
            return "通话结束";
        }
        else {
            return "不支持的消息类型";
        }
    }

    public static String getMessageSummary(Context view, Message message) {
        if (message.getContent() instanceof TextMessage) {
            return ((TextMessage) message.getContent()).getContent();
        } else if (message.getContent() instanceof ImageMessage) {
            return view.getResources().getString(R.string.msg_image);
        } else if (message.getContent() instanceof VoiceMessage) {
            return view.getResources().getString(R.string.msg_voice);
        } else if (message.getContent() instanceof FileMessage) {
            return view.getResources().getString(R.string.msg_file);
        } else if (message.getContent() instanceof MergeMessage) {
            return view.getResources().getString(R.string.history_messages);
        }
        return "unknown";
    }

    /**
     * 判断消息是否在消息列表中显示
     *
     * @param message
     * @return
     */
    public static boolean shownInMessageList(Message message) {
        if (message.getContent() instanceof TextMessage
                || message.getContent() instanceof ImageMessage
                || message.getContent() instanceof VoiceMessage
                || message.getContent() instanceof FileMessage
                || message.getContent() instanceof MergeMessage) {
            return true;
        } else {
            return false;
        }
    }

    public static String formatTimestamp(long ts) {
        DateFormat df = new SimpleDateFormat("HH:mm", Locale.getDefault());
        String text = df.format(new Date(ts));
        return text;
    }
}

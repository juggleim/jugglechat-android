package com.juggle.im.android.chat.utils;

import android.annotation.SuppressLint;
import android.content.Context;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.style.ForegroundColorSpan;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;

import com.juggle.im.JIM;
import com.juggle.im.android.R;
import com.juggle.im.android.chat.message.FriendNotifyMessage;
import com.juggle.im.android.chat.message.GroupNotifyMessage;
import com.juggle.im.android.chat.message.InsertTimeStatusMessage;
import com.juggle.im.android.chat.message.LifeTimeNotifyMessage;
import com.juggle.im.android.chat.message.MomentNotifyMessage;
import com.juggle.im.android.chat.message.StickerEmojiMessage;
import com.juggle.im.android.chat.message.StickerGameMessage;
import com.juggle.im.android.chat.message.SyncDataNotifyMessage;
import com.juggle.im.android.chat.message.TimelineNotifyMessage;
import com.juggle.im.android.chat.provider.FileMessageView;
import com.juggle.im.android.chat.provider.ImageMessageView;
import com.juggle.im.android.chat.provider.MergeMessageView;
import com.juggle.im.android.chat.provider.MessageView;
import com.juggle.im.android.chat.provider.StatusMessageView;
import com.juggle.im.android.chat.provider.StreamTextMessageView;
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
import com.juggle.im.model.messages.StreamTextMessage;
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
        registerMessageView(StreamTextMessage.class, StreamTextMessageView.class);
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
        registerMessageView(LifeTimeNotifyMessage.class, StatusMessageView.class);
        registerMessageView(MomentNotifyMessage.class, StatusMessageView.class);
        registerMessageView(SyncDataNotifyMessage.class, StatusMessageView.class);
        registerMessageView(TimelineNotifyMessage.class, StatusMessageView.class);
        registerMessageView(StickerGameMessage.class, StatusMessageView.class);
        registerMessageView(StickerEmojiMessage.class, StatusMessageView.class);

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
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(timestamp);

        Calendar now = Calendar.getInstance();

        boolean sameYear = now.get(Calendar.YEAR) == cal.get(Calendar.YEAR);
        boolean sameDay = sameYear && now.get(Calendar.DAY_OF_YEAR) == cal.get(Calendar.DAY_OF_YEAR);

        Calendar yesterday = (Calendar) now.clone();
        yesterday.add(Calendar.DAY_OF_YEAR, -1);
        boolean isYesterday = sameYear && yesterday.get(Calendar.DAY_OF_YEAR) == cal.get(Calendar.DAY_OF_YEAR);

        if (sameDay) {
            DateFormat df = new SimpleDateFormat("HH:mm", Locale.getDefault());
            return df.format(new Date(timestamp));
        }
        if (isYesterday) {
            DateFormat df = new SimpleDateFormat("HH:mm", Locale.getDefault());
            return "昨天 " + df.format(new Date(timestamp));
        }
        if (isSameWeek(now, cal)) {
            DateFormat df = new SimpleDateFormat("HH:mm", Locale.getDefault());
            return toChineseWeekday(cal) + " " + df.format(new Date(timestamp));
        }
        if (sameYear) {
            DateFormat df = new SimpleDateFormat("MM-dd HH:mm", Locale.getDefault());
            return df.format(new Date(timestamp));
        }
        DateFormat df = new SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.getDefault());
        return df.format(new Date(timestamp));
    }

    /**
     * 判断两个时间是否处于同一自然周（以周一作为一周起始）。
     */
    private static boolean isSameWeek(Calendar left, Calendar right) {
        Calendar l = (Calendar) left.clone();
        Calendar r = (Calendar) right.clone();
        l.setFirstDayOfWeek(Calendar.MONDAY);
        r.setFirstDayOfWeek(Calendar.MONDAY);
        return l.getWeekYear() == r.getWeekYear()
                && l.get(Calendar.WEEK_OF_YEAR) == r.get(Calendar.WEEK_OF_YEAR);
    }

    private static String toChineseWeekday(Calendar calendar) {
        switch (calendar.get(Calendar.DAY_OF_WEEK)) {
            case Calendar.MONDAY:
                return "周一";
            case Calendar.TUESDAY:
                return "周二";
            case Calendar.WEDNESDAY:
                return "周三";
            case Calendar.THURSDAY:
                return "周四";
            case Calendar.FRIDAY:
                return "周五";
            case Calendar.SATURDAY:
                return "周六";
            default:
                return "周日";
        }
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
            // 处理 @提及 替换
            String textContent = formatMentionToText(((TextMessage) message.getContent()).getContent(), message.getMentionInfo());
            return String.format(content, textContent);
        } else if (message.getContent() instanceof StreamTextMessage) {
            // 流式文本消息，处理 @提及 替换
            String textContent = formatMentionToText(((StreamTextMessage) message.getContent()).getContent(), message.getMentionInfo());
            return String.format(content, textContent);
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
        // 自定义消息类型
        else if (message.getContent() instanceof GroupNotifyMessage) {
            return view.getResources().getString(R.string.msg_group_notify);
        } else if (message.getContent() instanceof FriendNotifyMessage) {
            return view.getResources().getString(R.string.msg_friend_notify);
        } else if (message.getContent() instanceof LifeTimeNotifyMessage) {
            return view.getResources().getString(R.string.msg_lifetime_notify);
        } else if (message.getContent() instanceof MomentNotifyMessage) {
            return view.getResources().getString(R.string.msg_moment_notify);
        } else if (message.getContent() instanceof StickerEmojiMessage) {
            return view.getResources().getString(R.string.msg_sticker_emoji);
        } else if (message.getContent() instanceof StickerGameMessage) {
            StickerGameMessage gameMsg = (StickerGameMessage) message.getContent();
            if (StickerGameMessage.TYPE_DICE.equals(gameMsg.getGameType())) {
                return view.getResources().getString(R.string.msg_sticker_game_dice);
            } else if (StickerGameMessage.TYPE_MORA.equals(gameMsg.getGameType())) {
                return view.getResources().getString(R.string.msg_sticker_game_mora);
            }
            return view.getResources().getString(R.string.msg_sticker_game);
        } else if (message.getContent() instanceof SyncDataNotifyMessage) {
            return view.getResources().getString(R.string.msg_sync_data_notify);
        } else if (message.getContent() instanceof TimelineNotifyMessage) {
            return view.getResources().getString(R.string.msg_timeline_notify);
        }
        // 通过 contentType 判断（处理未注册的情况）
        else if (message.getContentType().equals("jgd:grpntf")) {
            return view.getResources().getString(R.string.msg_group_notify);
        } else if (message.getContentType().equals("jgd:friendntf")) {
            return view.getResources().getString(R.string.msg_friend_notify);
        } else if (message.getContentType().equals("jgd:lifetime_msg")) {
            return view.getResources().getString(R.string.msg_lifetime_notify);
        } else if (message.getContentType().equals("jgd:postnotify")) {
            return view.getResources().getString(R.string.msg_moment_notify);
        } else if (message.getContentType().equals("snl:sticker")) {
            return view.getResources().getString(R.string.msg_sticker_emoji);
        } else if (message.getContentType().equals("jgd:sticker_game")) {
            return view.getResources().getString(R.string.msg_sticker_game);
        } else if (message.getContentType().equals("snl:syncdntf")) {
            return view.getResources().getString(R.string.msg_sync_data_notify);
        } else if (message.getContentType().equals("jgd:timeline")) {
            return view.getResources().getString(R.string.msg_timeline_notify);
        } else if (message.getContentType().equals("snl:typing")) {
            return view.getResources().getString(R.string.msg_typing_notify);
        } else if (message.getContentType().equals("jgd:contactcard")) {
            return view.getResources().getString(R.string.msg_contact_card);
        } else {
            Log.i("formater", "conv msg: " + message.getConversation().getConversationType().toString());
            return String.format(content, view.getResources().getString(R.string.msg_unknown));
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
        } else if (t instanceof LifeTimeNotifyMessage) {
            LifeTimeNotifyMessage msg = (LifeTimeNotifyMessage) t;
            return msg.description(userInfo != null ? userInfo.getUserName() : "对方");
        } else if (t instanceof MomentNotifyMessage) {
            return "朋友圈通知";
        } else if (t instanceof SyncDataNotifyMessage) {
            return "数据已同步";
        } else if (t instanceof TimelineNotifyMessage) {
            TimelineNotifyMessage msg = (TimelineNotifyMessage) t;
            String content = msg.getContent();
            return TextUtils.isEmpty(content) ? "时间线" : content;
        } else if (t instanceof StickerGameMessage) {
            StickerGameMessage msg = (StickerGameMessage) t;
            if (StickerGameMessage.TYPE_DICE.equals(msg.getGameType())) {
                return "骰子游戏";
            } else if (StickerGameMessage.TYPE_MORA.equals(msg.getGameType())) {
                return "猜拳游戏";
            }
            return "互动游戏";
        } else if (t instanceof StickerEmojiMessage) {
            return "表情贴纸";
        } else {
            return "不支持的消息类型";
        }
    }

    public static String getMessageSummary(Context view, Message message) {
        if (message.getContent() instanceof TextMessage) {
            return ((TextMessage) message.getContent()).getContent();
        } else if (message.getContent() instanceof StreamTextMessage) {
            return ((StreamTextMessage) message.getContent()).getContent();
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
                || message.getContent() instanceof StreamTextMessage
                || message.getContent() instanceof ImageMessage
                || message.getContent() instanceof VoiceMessage
                || message.getContent() instanceof FileMessage
                || message.getContent() instanceof MergeMessage) {
            return true;
        } else {
            return false;
        }
    }

    /**
     * 格式化带有 @提及 的文本消息
     * 将 {userId} 替换为 @用户名 并返回 SpannableString 以高亮显示
     *
     * @param content     原始文本内容
     * @param mentionInfo 提及信息
     * @param context     上下文用于获取颜色
     * @return 格式化后的 SpannableString
     */
    public static SpannableString formatMentionText(String content, com.juggle.im.model.MessageMentionInfo mentionInfo, Context context) {
        if (TextUtils.isEmpty(content)) {
            return new SpannableString("");
        }

        // 构建 userId -> userName 映射
        java.util.Map<String, String> idToNameMap = new java.util.HashMap<>();
        idToNameMap.put("all", "所有人");

        if (mentionInfo != null && mentionInfo.getTargetUsers() != null) {
            for (UserInfo user : mentionInfo.getTargetUsers()) {
                idToNameMap.put(user.getUserId(), user.getUserName());
            }
        }

        // 替换 {userId} 为 @用户名
        final java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("\\{([^}]+)\\}");
        final java.util.regex.Matcher matcher = pattern.matcher(content);
        StringBuffer sb = new StringBuffer();
        java.util.List<int[]> mentionRanges = new java.util.ArrayList<>();

        while (matcher.find()) {
            String userId = matcher.group(1);
            String userName = idToNameMap.get(userId);
            if (userName != null) {
                int start = sb.length();
                String replacement = "@" + userName + " ";
                matcher.appendReplacement(sb, java.util.regex.Matcher.quoteReplacement(replacement));
                mentionRanges.add(new int[]{start, start + replacement.length()});
            } else {
                // tips: 保留原始占位符时同样需要转义 replacement，避免用户名或占位内容中的特殊字符导致替换异常
                matcher.appendReplacement(sb, java.util.regex.Matcher.quoteReplacement(matcher.group(0)));
            }
        }
        matcher.appendTail(sb);

        String result = sb.toString();
        SpannableString spannable = new SpannableString(result);

        // 高亮所有 @提及
        int mentionColor = ContextCompat.getColor(context, R.color.mention_text_color);
        for (int[] range : mentionRanges) {
            spannable.setSpan(
                    new ForegroundColorSpan(mentionColor),
                    range[0],
                    range[1],
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            );
        }

        return spannable;
    }

    /**
     * 将 @提及 的文本转换为纯文本（用于会话列表摘要显示）
     * 将 {userId} 替换为 @用户名
     *
     * @param content     原始文本内容
     * @param mentionInfo 提及信息
     * @return 替换后的纯文本
     */
    public static String formatMentionToText(String content, com.juggle.im.model.MessageMentionInfo mentionInfo) {
        if (TextUtils.isEmpty(content)) {
            return content;
        }

        // 如果没有 mentionInfo，检查是否有 {all} 需要替换
        if (mentionInfo == null) {
            return content.replace("{all}", "@所有人 ");
        }

        // 构建 userId -> userName 映射
        java.util.Map<String, String> idToNameMap = new java.util.HashMap<>();
        idToNameMap.put("all", "所有人");

        if (mentionInfo.getTargetUsers() != null) {
            for (UserInfo user : mentionInfo.getTargetUsers()) {
                idToNameMap.put(user.getUserId(), user.getUserName());
            }
        }

        // 替换 {userId} 为 @用户名
        final java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("\\{([^}]+)\\}");
        final java.util.regex.Matcher matcher = pattern.matcher(content);
        StringBuffer sb = new StringBuffer();

        while (matcher.find()) {
            String userId = matcher.group(1);
            String userName = idToNameMap.get(userId);
            if (userName != null) {
                matcher.appendReplacement(sb, java.util.regex.Matcher.quoteReplacement("@" + userName + " "));
            } else {
                matcher.appendReplacement(sb, java.util.regex.Matcher.quoteReplacement(matcher.group(0)));
            }
        }
        matcher.appendTail(sb);

        return sb.toString();
    }

    public static String formatTimestamp(long ts) {
        DateFormat df = new SimpleDateFormat("HH:mm", Locale.getDefault());
        String text = df.format(new Date(ts));
        return text;
    }
}

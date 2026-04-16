package com.juggle.im.android.app;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import com.juggle.im.JIM;
import com.juggle.im.android.R;
import com.juggle.im.android.chat.ImagePreviewActivity;
import com.juggle.im.android.chat.MergeMessageActivity;
import com.juggle.im.android.chat.utils.MessageUtils;
import com.juggle.im.android.component.AbsAppActivity;
import com.juggle.im.android.model.UiMessage;
import com.juggle.im.android.chat.provider.MessageView;
import com.juggle.im.model.Message;
import com.juggle.im.model.MessageContent;
import com.juggle.im.model.UserInfo;
import com.juggle.im.model.messages.ImageMessage;
import com.juggle.im.model.messages.MergeMessage;

import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * 收藏消息详情预览页。
 * <p>
 * 根据消息类型（文本、图片、语音、文件、合并消息等）渲染对应的内容视图，
 * 复用项目已有的 MessageView 体系。
 * <ul>
 *   <li>文本 / 流式文本 → 文本气泡</li>
 *   <li>图片 → 缩略图，点击进入全屏预览</li>
 *   <li>语音 → 播放控件</li>
 *   <li>文件 → 文件卡片，点击下载/打开</li>
 *   <li>合并消息 → 摘要卡片，点击进入合并消息详情页</li>
 * </ul>
 */
public class FavoriteDetailActivity extends AbsAppActivity {

    public static final String EXTRA_MESSAGE_ID = "message_id";
    public static final String EXTRA_CONVERSATION_NAME = "conversation_name";

    /**
     * 启动收藏详情页
     *
     * @param context          上下文
     * @param messageId        消息 ID
     * @param conversationName 来源会话名称
     */
    public static void start(Context context, String messageId, String conversationName) {
        Intent intent = new Intent(context, FavoriteDetailActivity.class);
        intent.putExtra(EXTRA_MESSAGE_ID, messageId);
        intent.putExtra(EXTRA_CONVERSATION_NAME, conversationName);
        context.startActivity(intent);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_favorite_detail);

        findViewById(R.id.iv_back).setOnClickListener(v -> finish());

        String messageId = getIntent().getStringExtra(EXTRA_MESSAGE_ID);
        String conversationName = getIntent().getStringExtra(EXTRA_CONVERSATION_NAME);

        // tips: 从 SDK 获取单条收藏消息，通过 messageId 查找
        Message message = fetchFavoriteMessage(messageId);
        if (message == null) {
            TextView tvTitle = findViewById(R.id.tv_title);
            tvTitle.setText("消息不存在");
            return;
        }

        // tips: 构造 UiMessage 并通过 MessageView 体系渲染内容
        UiMessage uiMessage = UiMessage.fromMessage(message);
        if (uiMessage == null) {
            TextView tvTitle = findViewById(R.id.tv_title);
            tvTitle.setText("消息不存在");
            return;
        }

        bindHeader(uiMessage, conversationName);
        bindContent(uiMessage);
    }

    /**
     * 绑定顶部信息栏：发送者、时间、来源会话
     */
    private void bindHeader(UiMessage uiMessage, String conversationName) {
        TextView tvSender = findViewById(R.id.tv_sender);
        TextView tvTime = findViewById(R.id.tv_time);
        TextView tvConversation = findViewById(R.id.tv_conversation);

        String senderName = resolveSenderName(uiMessage);
        tvSender.setText(senderName);
        tvTime.setText(formatTime(uiMessage.getMessage().getTimestamp()));
        tvConversation.setText(TextUtils.isEmpty(conversationName) ? "" : "来自: " + conversationName);
    }

    /**
     * 根据消息类型渲染内容区域
     */
    private void bindContent(UiMessage uiMessage) {
        FrameLayout contentContainer = findViewById(R.id.content_container);
        MessageContent content = uiMessage.getMessage().getContent();
        if (content == null) {
            return;
        }

        // tips: 复用 MessageView 体系渲染消息内容，保持与聊天页一致的展示效果
        Class<? extends MessageView> holderClass = MessageUtils.messageViewCache.get(content.getClass());
        if (holderClass == null) {
            showUnsupportedMessage(contentContainer);
            return;
        }

        try {
            java.lang.reflect.Constructor<? extends MessageView> constructor =
                    holderClass.getConstructor(ViewGroup.class);
            MessageView messageView = constructor.newInstance(contentContainer);
            contentContainer.addView(messageView.itemView);

            // tips: 收藏预览统一以接收消息样式展示，不显示发送状态
            UiMessage displayMessage = createDisplayMessage(uiMessage);
            messageView.bind(displayMessage, content, false, messageView.itemView);

            // tips: 特殊类型点击行为：图片→全屏预览，合并消息→合并详情页
            handleSpecialClick(contentContainer, content, uiMessage);
        } catch (Exception e) {
            showUnsupportedMessage(contentContainer);
        }
    }

    /**
     * 构造用于展示的 UiMessage，统一为接收方向避免发送态 UI 干扰
     */
    private UiMessage createDisplayMessage(UiMessage original) {
        Message msg = original.getMessage();
        // tips: 将方向强制设为 RECEIVE，使气泡以灰色接收样式显示
        msg.setDirection(Message.MessageDirection.RECEIVE);
        return UiMessage.fromMessage(msg);
    }

    /**
     * 处理特殊消息类型的点击事件
     */
    private void handleSpecialClick(FrameLayout container, MessageContent content, UiMessage uiMessage) {
        if (content instanceof ImageMessage) {
            ImageMessage img = (ImageMessage) content;
            String url = !TextUtils.isEmpty(img.getUrl()) ? img.getUrl() : img.getThumbnailUrl();
            if (!TextUtils.isEmpty(url)) {
                container.setOnClickListener(v -> {
                    Intent intent = new Intent(this, ImagePreviewActivity.class);
                    intent.putExtra(ImagePreviewActivity.EXTRA_IMAGE_URL, url);
                    startActivity(intent);
                });
            }
        } else if (content instanceof MergeMessage) {
            container.setOnClickListener(v -> {
                MergeMessageActivity.start(this, uiMessage.getMessageId());
            });
        }
    }

    /**
     * 显示不支持的消息类型提示
     */
    private void showUnsupportedMessage(FrameLayout container) {
        TextView tvEmpty = new TextView(this);
        tvEmpty.setText("暂不支持查看此消息类型");
        tvEmpty.setTextColor(getColor(R.color.search_secondary_text));
        tvEmpty.setTextSize(14);
        container.addView(tvEmpty);
    }

    /**
     * 从 SDK 获取收藏消息
     * tips: 通过 getMessagesByMessageIds 从本地数据库获取消息详情
     *
     * @param messageId 消息 ID
     * @return 消息对象，获取失败返回 null
     */
    private Message fetchFavoriteMessage(String messageId) {
        if (TextUtils.isEmpty(messageId)) {
            return null;
        }
        List<Message> messages = JIM.getInstance().getMessageManager()
                .getMessagesByMessageIds(Collections.singletonList(messageId));
        if (messages != null && !messages.isEmpty()) {
            return messages.get(0);
        }
        return null;
    }

    /**
     * 解析发送者名称
     */
    private String resolveSenderName(UiMessage uiMessage) {
        String senderId = uiMessage.getSenderId();
        if (!TextUtils.isEmpty(uiMessage.getSenderName())) {
            return uiMessage.getSenderName();
        }
        if (!TextUtils.isEmpty(senderId)) {
            UserInfo userInfo = JIM.getInstance().getUserInfoManager().getUserInfo(senderId);
            if (userInfo != null && !TextUtils.isEmpty(userInfo.getUserName())) {
                return userInfo.getUserName();
            }
        }
        return senderId != null ? senderId : "";
    }

    private String formatTime(long timestamp) {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(new Date(timestamp));
    }
}

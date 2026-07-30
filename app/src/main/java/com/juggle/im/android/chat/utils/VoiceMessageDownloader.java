package com.juggle.im.android.chat.utils;

import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.juggle.im.JIM;
import com.juggle.im.android.utils.LogUtils;
import com.juggle.im.interfaces.IMessageManager;
import com.juggle.im.model.MediaMessageContent;
import com.juggle.im.model.Message;
import com.juggle.im.model.messages.VoiceMessage;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 语音消息本地文件管理。
 *
 * <p>对齐 iOS 的 downloadVoiceMessageIfNeeded + prefetchVoiceMessagesIfNeeded：
 * 播放一律走本地文件，本地缺失时才下载；消息批量加载后静默预下载，让点击播放几乎立即出声。
 * 直接把远端 URL 交给 MediaPlayer 会在每次点击时重新走网络缓冲，这是"点了要等一会儿才响"的根因。</p>
 */
public final class VoiceMessageDownloader {

    private static final String TAG = "VoiceDownloader";
    private static final String FEATURE = "voice";

    /** 同一条消息的下载去重集合，仅在主线程访问 */
    private static final Set<String> DOWNLOADING_MESSAGE_IDS = new HashSet<>();
    /** 下载完成后待回调的队列，key 为 messageId，仅在主线程访问 */
    private static final Map<String, List<Callback>> PENDING_CALLBACKS = new HashMap<>();
    private static final Handler MAIN_HANDLER = new Handler(Looper.getMainLooper());

    private VoiceMessageDownloader() {
    }

    /**
     * 语音文件就绪回调。
     */
    public interface Callback {
        /**
         * 本地文件已就绪。
         *
         * @param localPath 可直接播放的本地文件路径
         */
        void onReady(@NonNull String localPath);

        /**
         * 下载失败。
         */
        void onFailed();
    }

    /**
     * 获取可播放的本地文件路径。
     *
     * @param content 语音消息内容
     * @return 本地文件存在时返回其路径，否则返回 null
     */
    @Nullable
    public static String playableLocalPath(@Nullable MediaMessageContent content) {
        if (content == null || TextUtils.isEmpty(content.getLocalPath())) {
            return null;
        }
        String localPath = content.getLocalPath();
        // TIPS: SDK 可能给出 file:// 前缀，MediaPlayer 与 File 都按纯路径处理更稳
        if (localPath.startsWith("file://")) {
            localPath = localPath.substring("file://".length());
        }
        File file = new File(localPath);
        return file.exists() && file.length() > 0 ? localPath : null;
    }

    /**
     * 确保语音消息的本地文件就绪，就绪后回调。
     *
     * @param message  语音消息
     * @param callback 结果回调，可为 null（表示静默预下载）
     */
    public static void ensureLocal(@Nullable Message message, @Nullable Callback callback) {
        if (message == null || !(message.getContent() instanceof VoiceMessage)) {
            notifyFailed(callback);
            return;
        }
        VoiceMessage voice = (VoiceMessage) message.getContent();
        String localPath = playableLocalPath(voice);
        if (localPath != null) {
            if (callback != null) {
                callback.onReady(localPath);
            }
            return;
        }

        String messageId = message.getMessageId();
        if (TextUtils.isEmpty(messageId)) {
            notifyFailed(callback);
            return;
        }

        if (callback != null) {
            List<Callback> callbacks = PENDING_CALLBACKS.get(messageId);
            if (callbacks == null) {
                callbacks = new ArrayList<>();
                PENDING_CALLBACKS.put(messageId, callbacks);
            }
            callbacks.add(callback);
        }
        if (!DOWNLOADING_MESSAGE_IDS.add(messageId)) {
            // 已有同一条消息在下载，回调已入队，等它结束统一通知
            return;
        }

        JIM.getInstance().getMessageManager().downloadMediaMessage(messageId,
                new IMessageManager.IDownloadMediaMessageCallback() {
                    @Override
                    public void onProgress(int progress, Message downloading) {
                        // 语音体积小，不展示进度
                    }

                    @Override
                    public void onSuccess(Message downloaded) {
                        String readyPath = downloaded == null
                                ? null
                                : playableLocalPath(asMediaContent(downloaded));
                        MAIN_HANDLER.post(() -> finish(messageId, readyPath));
                    }

                    @Override
                    public void onError(int errorCode) {
                        LogUtils.serverError(FEATURE, "downloadVoice", errorCode, "messageId=" + messageId);
                        MAIN_HANDLER.post(() -> finish(messageId, null));
                    }

                    @Override
                    public void onCancel(Message canceled) {
                        MAIN_HANDLER.post(() -> finish(messageId, null));
                    }
                });
    }

    /**
     * 批量预下载语音消息，用于消息列表加载完成后提前落地文件。
     *
     * @param messages 本批消息
     */
    public static void prefetch(@Nullable List<Message> messages) {
        if (messages == null || messages.isEmpty()) {
            return;
        }
        for (Message message : messages) {
            if (message == null || !(message.getContent() instanceof VoiceMessage)) {
                continue;
            }
            if (playableLocalPath((VoiceMessage) message.getContent()) != null) {
                continue;
            }
            ensureLocal(message, null);
        }
    }

    @Nullable
    private static MediaMessageContent asMediaContent(@NonNull Message message) {
        return message.getContent() instanceof MediaMessageContent
                ? (MediaMessageContent) message.getContent()
                : null;
    }

    private static void finish(@NonNull String messageId, @Nullable String localPath) {
        DOWNLOADING_MESSAGE_IDS.remove(messageId);
        List<Callback> callbacks = PENDING_CALLBACKS.remove(messageId);
        if (callbacks == null) {
            return;
        }
        for (Callback callback : callbacks) {
            if (localPath != null) {
                callback.onReady(localPath);
            } else {
                callback.onFailed();
            }
        }
    }

    private static void notifyFailed(@Nullable Callback callback) {
        if (callback != null) {
            callback.onFailed();
        }
    }
}

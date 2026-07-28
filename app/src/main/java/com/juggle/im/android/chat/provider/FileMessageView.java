package com.juggle.im.android.chat.provider;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.net.Uri;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.MimeTypeMap;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;

import com.juggle.im.android.R;
import com.juggle.im.android.model.UiMessage;
import com.juggle.im.model.Message;
import com.juggle.im.model.messages.FileMessage;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import com.juggle.im.android.i18n.AppRes;

/**
 * 文件消息内容视图。
 */
public class FileMessageView extends MessageView<UiMessage, FileMessage> {
    private static final String TAG = "FileMessageView";
    private static final String DOWNLOAD_DIR_NAME = "juggle_files";
    private static final OkHttpClient DOWNLOAD_CLIENT = new OkHttpClient();
    private static final Handler MAIN_HANDLER = new Handler(Looper.getMainLooper());
    private static final ConcurrentHashMap<String, DownloadTask> DOWNLOAD_TASKS = new ConcurrentHashMap<>();

    private FileMessage boundFileMessage;
    private String boundTaskKey = "";
    private String boundFileName = "";
    private String boundFileMeta = "";

    private final DownloadStateListener boundDownloadStateListener = snapshot -> itemView.post(() -> {
        if (!TextUtils.equals(boundTaskKey, snapshot.taskKey)) {
            return;
        }
        renderDownloadState(snapshot);
    });

    /**
     * 文件下载状态枚举。
     */
    private enum DownloadState {
        IDLE,
        DOWNLOADING,
        SUCCESS,
        FAILED,
        CANCELED
    }

    /**
     * 下载状态快照，用于 UI 渲染。
     */
    private static final class DownloadSnapshot {
        private final String taskKey;
        private final DownloadState state;
        private final int progress;
        private final String localPath;
        private final String errorMessage;

        private DownloadSnapshot(String taskKey, DownloadState state, int progress, String localPath, String errorMessage) {
            this.taskKey = taskKey;
            this.state = state;
            this.progress = progress;
            this.localPath = localPath == null ? "" : localPath;
            this.errorMessage = errorMessage == null ? "" : errorMessage;
        }
    }

    /**
     * 文件下载任务。
     */
    private static final class DownloadTask {
        private final String taskKey;
        private final CopyOnWriteArrayList<DownloadStateListener> listeners = new CopyOnWriteArrayList<>();
        private DownloadState state = DownloadState.IDLE;
        private int progress = 0;
        private String localPath = "";
        private String errorMessage = "";
        private long runId = 0L;
        @Nullable
        private Call call;

        private DownloadTask(String taskKey) {
            this.taskKey = taskKey;
        }

        private DownloadSnapshot toSnapshot() {
            return new DownloadSnapshot(taskKey, state, progress, localPath, errorMessage);
        }
    }

    /**
     * 下载状态监听。
     */
    private interface DownloadStateListener {
        void onStateChanged(@NonNull DownloadSnapshot snapshot);
    }

    /**
     * 文件消息视图。
     *
     * @param root 消息内容容器
     */
    public FileMessageView(@NonNull ViewGroup root) {
        super(root, R.layout.content_file);
    }

    /**
     * 绑定文件消息内容。
     *
     * @param uiMessage 消息对象
     * @param fileMessage 文件消息内容
     * @param isGroup 是否群聊
     */
    @Override
    public void bindItem(UiMessage uiMessage, FileMessage fileMessage, boolean isGroup) {
        this.boundFileMessage = fileMessage;

        ImageView fileActionIcon = itemView.findViewById(R.id.image_file_action_icon);
        TextView fileNameView = itemView.findViewById(R.id.text_file_name);
        TextView fileSizeView = itemView.findViewById(R.id.text_file_size);
        TextView fileStatusView = itemView.findViewById(R.id.text_file_status);
        ProgressBar downloadProgressView = itemView.findViewById(R.id.progress_file_download);
        View clickContainer = itemView.findViewById(R.id.layout_file_container);

        applyFileBubbleStyle(uiMessage);

        if (fileActionIcon != null) {
            fileActionIcon.setColorFilter(ContextCompat.getColor(itemView.getContext(), android.R.color.white));
        }

        String fileName = resolveFileName(fileMessage);
        boundFileName = fileName;
        boundFileMeta = buildFileMeta(resolveFileSize(fileMessage), resolveFileType(fileName, fileMessage));

        if (fileNameView != null) {
            fileNameView.setText(fileName);
            fileNameView.setTextColor(ContextCompat.getColor(itemView.getContext(), R.color.title));
        }
        if (fileSizeView != null) {
            fileSizeView.setText(boundFileMeta);
            fileSizeView.setTextColor(ContextCompat.getColor(itemView.getContext(), R.color.conversation_secondary_text));
        }
        if (fileStatusView != null) {
            fileStatusView.setVisibility(View.GONE);
        }
        if (downloadProgressView != null) {
            downloadProgressView.setVisibility(View.GONE);
            downloadProgressView.setProgress(0);
        }

        String taskKey = buildTaskKey(uiMessage, fileMessage, fileName);
        bindTaskObserver(taskKey);
        DownloadSnapshot snapshot = resolveDisplaySnapshot(taskKey, fileMessage, fileName);
        renderDownloadState(snapshot);

        View actionArea = clickContainer == null ? itemView : clickContainer;
        actionArea.setOnClickListener(v -> handleFileAction(v.getContext()));
        actionArea.setOnLongClickListener(v -> {
            View parent = (View) itemView.getParent();
            if (parent != null) {
                parent.performLongClick();
            }
            return true;
        });
    }

    /**
     * 处理文件消息点击行为。
     *
     * <p>简要描述：下载中点击执行取消；本地存在文件则直接打开；否则进入下载流程。</p>
     */
    private void handleFileAction(@Nullable Context context) {
        if (context == null || boundFileMessage == null) {
            return;
        }
        String taskKey = boundTaskKey;
        DownloadSnapshot snapshot = resolveDisplaySnapshot(taskKey, boundFileMessage, boundFileName);
        if (snapshot.state == DownloadState.DOWNLOADING) {
            cancelDownload(taskKey);
            return;
        }

        File localFile = resolveLocalFile(context, boundFileMessage, boundFileName, snapshot);
        if (localFile != null && localFile.exists()) {
            openFile(context, localFile);
            return;
        }

        String url = boundFileMessage.getUrl();
        if (TextUtils.isEmpty(url)) {
            Toast.makeText(context, R.string.file_link_invalid, Toast.LENGTH_SHORT).show();
            return;
        }
        startDownload(context, taskKey, url, boundFileName);
    }

    /**
     * 绑定下载状态监听器。
     */
    private void bindTaskObserver(@NonNull String taskKey) {
        if (!TextUtils.isEmpty(boundTaskKey) && !TextUtils.equals(boundTaskKey, taskKey)) {
            removeTaskListener(boundTaskKey, boundDownloadStateListener);
        }
        boundTaskKey = taskKey;
        addTaskListener(taskKey, boundDownloadStateListener);
    }

    /**
     * 渲染下载状态。
     */
    private void renderDownloadState(@NonNull DownloadSnapshot snapshot) {
        ImageView fileActionIcon = itemView.findViewById(R.id.image_file_action_icon);
        TextView fileSizeView = itemView.findViewById(R.id.text_file_size);
        TextView fileStatusView = itemView.findViewById(R.id.text_file_status);
        ProgressBar downloadProgressView = itemView.findViewById(R.id.progress_file_download);

        if (fileActionIcon == null || fileSizeView == null || fileStatusView == null || downloadProgressView == null) {
            return;
        }
        fileSizeView.setText(boundFileMeta);
        fileStatusView.setVisibility(View.GONE);
        downloadProgressView.setVisibility(View.GONE);

        switch (snapshot.state) {
            case DOWNLOADING:
                fileActionIcon.setImageResource(R.drawable.ic_cancel_white);
                fileStatusView.setVisibility(View.VISIBLE);
                fileStatusView.setText(AppRes.string(R.string.file_downloading, snapshot.progress));
                downloadProgressView.setVisibility(View.VISIBLE);
                downloadProgressView.setProgress(Math.max(0, Math.min(100, snapshot.progress)));
                break;
            case FAILED:
                fileActionIcon.setImageResource(R.drawable.ic_download);
                fileStatusView.setVisibility(View.VISIBLE);
                fileStatusView.setText(R.string.file_download_failed_retry);
                break;
            case CANCELED:
                fileActionIcon.setImageResource(R.drawable.ic_download);
                fileStatusView.setVisibility(View.VISIBLE);
                fileStatusView.setText(R.string.file_download_canceled_retry);
                break;
            case SUCCESS:
                fileActionIcon.setImageResource(R.drawable.ic_file);
                fileStatusView.setVisibility(View.VISIBLE);
                fileStatusView.setText(R.string.file_downloaded_open);
                break;
            case IDLE:
            default:
                File localFile = resolveLocalFile(itemView.getContext(), boundFileMessage, boundFileName, snapshot);
                fileActionIcon.setImageResource(localFile != null && localFile.exists()
                        ? R.drawable.ic_file
                        : R.drawable.ic_download);
                break;
        }
    }

    /**
     * 启动文件下载。
     *
     * <p>简要描述：下载过程由静态任务表统一维护，保障 RecyclerView 复用后仍可继续展示同一条消息的下载进度和取消状态。</p>
     */
    private void startDownload(@NonNull Context context,
                               @NonNull String taskKey,
                               @NonNull String fileUrl,
                               @NonNull String fileName) {
        File targetFile = buildDownloadTargetFile(context, fileName);
        if (targetFile.exists()) {
            markTaskSuccess(taskKey, 0L, targetFile.getAbsolutePath());
            openFile(context, targetFile);
            return;
        }

        Request request = new Request.Builder()
                .url(fileUrl)
                .build();
        Call call = DOWNLOAD_CLIENT.newCall(request);
        long runId = markTaskDownloading(taskKey, call);

        call.enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                if (call.isCanceled()) {
                    markTaskCanceled(taskKey, runId);
                    return;
                }
                Log.e(TAG, "文件下载失败", e);
                markTaskFailed(taskKey, runId, e.getMessage());
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) {
                if (call.isCanceled()) {
                    markTaskCanceled(taskKey, runId);
                    closeQuietly(response);
                    return;
                }
                if (!response.isSuccessful()) {
                    Log.e(TAG, "文件下载失败，响应码: " + response.code());
                    markTaskFailed(taskKey, runId, "http_" + response.code());
                    closeQuietly(response);
                    return;
                }

                ResponseBody responseBody = response.body();
                if (responseBody == null) {
                    Log.e(TAG, "文件下载失败，响应体为空");
                    markTaskFailed(taskKey, runId, "empty_body");
                    closeQuietly(response);
                    return;
                }

                long total = responseBody.contentLength();
                int lastProgress = -1;
                try (InputStream inputStream = responseBody.byteStream();
                     FileOutputStream outputStream = new FileOutputStream(targetFile)) {
                    byte[] buffer = new byte[8 * 1024];
                    int bytesRead;
                    long received = 0L;
                    while ((bytesRead = inputStream.read(buffer)) != -1) {
                        if (call.isCanceled()) {
                            markTaskCanceled(taskKey, runId);
                            closeQuietly(response);
                            return;
                        }
                        outputStream.write(buffer, 0, bytesRead);
                        received += bytesRead;
                        if (total > 0L) {
                            int progress = (int) Math.min(100L, (received * 100L) / total);
                            if (progress != lastProgress) {
                                lastProgress = progress;
                                updateTaskProgress(taskKey, runId, progress);
                            }
                        }
                    }
                    outputStream.flush();
                    markTaskSuccess(taskKey, runId, targetFile.getAbsolutePath());
                    MAIN_HANDLER.post(() -> openFile(context, targetFile));
                } catch (IOException e) {
                    if (call.isCanceled()) {
                        markTaskCanceled(taskKey, runId);
                    } else {
                        Log.e(TAG, "文件保存失败", e);
                        markTaskFailed(taskKey, runId, e.getMessage());
                    }
                } finally {
                    closeQuietly(response);
                }
            }
        });
    }

    private void cancelDownload(@NonNull String taskKey) {
        DownloadTask task = getOrCreateTask(taskKey);
        Call activeCall;
        synchronized (task) {
            activeCall = task.call;
            if (task.state != DownloadState.DOWNLOADING) {
                return;
            }
            task.state = DownloadState.CANCELED;
            task.errorMessage = "canceled";
            task.call = null;
        }
        notifyTaskListeners(task);
        if (activeCall != null) {
            activeCall.cancel();
        }
    }

    private String resolveFileName(FileMessage message) {
        if (message == null || TextUtils.isEmpty(message.getName())) {
            return AppRes.string(R.string.file_unknown_name);
        }
        return message.getName().trim();
    }

    private long resolveFileSize(FileMessage message) {
        if (message == null) {
            return 0L;
        }
        try {
            Method method = message.getClass().getMethod("getSize");
            Object value = method.invoke(message);
            if (value instanceof Number) {
                long size = ((Number) value).longValue();
                if (size > 0) {
                    return size;
                }
            }
        } catch (Exception ignored) {
        }

        String localPath = message.getLocalPath();
        if (!TextUtils.isEmpty(localPath)) {
            File localFile = new File(localPath);
            if (localFile.exists()) {
                return localFile.length();
            }
        }
        return 0L;
    }

    private String buildFileMeta(long sizeBytes, String fileType) {
        String size = formatFileSize(sizeBytes);
        if (TextUtils.isEmpty(fileType)) {
            return size;
        }
        return size + " · " + fileType;
    }

    private String resolveFileType(String fileName, @Nullable FileMessage message) {
        String extension = getFileExtension(fileName);
        if (TextUtils.isEmpty(extension) && message != null) {
            extension = getFileExtensionFromUrl(message.getUrl());
        }
        if (TextUtils.isEmpty(extension)) {
            return "FILE";
        }
        return extension.toUpperCase(Locale.getDefault());
    }

    private DownloadSnapshot resolveDisplaySnapshot(@NonNull String taskKey,
                                                    @Nullable FileMessage message,
                                                    @NonNull String fileName) {
        DownloadSnapshot snapshot = getTaskSnapshot(taskKey);
        Context context = itemView.getContext();
        File localFile = resolveLocalFile(context, message, fileName, snapshot);
        if (localFile != null && localFile.exists()) {
            return new DownloadSnapshot(taskKey, DownloadState.SUCCESS, 100, localFile.getAbsolutePath(), "");
        }
        if (snapshot.state == DownloadState.SUCCESS) {
            return new DownloadSnapshot(taskKey, DownloadState.IDLE, 0, "", "");
        }
        return snapshot;
    }

    @Nullable
    private File resolveLocalFile(@Nullable Context context,
                                  @Nullable FileMessage message,
                                  @NonNull String fileName,
                                  @NonNull DownloadSnapshot snapshot) {
        if (message != null && !TextUtils.isEmpty(message.getLocalPath())) {
            File localFile = new File(message.getLocalPath());
            if (localFile.exists()) {
                return localFile;
            }
        }
        if (!TextUtils.isEmpty(snapshot.localPath)) {
            File localFile = new File(snapshot.localPath);
            if (localFile.exists()) {
                return localFile;
            }
        }
        if (context == null) {
            return null;
        }
        File candidate = buildDownloadTargetFile(context, fileName);
        return candidate.exists() ? candidate : null;
    }

    private String buildTaskKey(@Nullable UiMessage uiMessage, @Nullable FileMessage fileMessage, @NonNull String fileName) {
        if (uiMessage != null && !TextUtils.isEmpty(uiMessage.getMessageId())) {
            return "msg:" + uiMessage.getMessageId();
        }
        if (uiMessage != null && uiMessage.getMessage() != null && uiMessage.getMessage().getClientMsgNo() > 0L) {
            return "client:" + uiMessage.getMessage().getClientMsgNo();
        }
        if (fileMessage != null && !TextUtils.isEmpty(fileMessage.getUrl())) {
            return "url:" + fileMessage.getUrl();
        }
        return "name:" + fileName;
    }

    private void applyFileBubbleStyle(UiMessage message) {
        ViewGroup bubbleContainer = itemView.findViewById(R.id.message_bubble_container);
        if (bubbleContainer != null) {
            bubbleContainer.setBackgroundResource(R.drawable.bg_message_received);
            bubbleContainer.setPadding(
                    dp(itemView, 8),
                    dp(itemView, 6),
                    dp(itemView, 8),
                    dp(itemView, 4)
            );
        }

        TextView timeView = itemView.findViewById(R.id.msg_sent_time);
        if (timeView != null) {
            timeView.setTextColor(ContextCompat.getColor(itemView.getContext(), R.color.conversation_secondary_text));
        }

        ProgressBar sendProgress = itemView.findViewById(R.id.msg_send_status);
        if (sendProgress != null) {
            sendProgress.setIndeterminateTintList(ColorStateList.valueOf(
                    ContextCompat.getColor(itemView.getContext(), R.color.conversation_secondary_text)));
        }

        // 简要描述：文件气泡统一为浅色卡片，发送态下仅“单勾已发送”使用灰色，其余状态沿用原有语义色。
        if (message.getDirection() == Message.MessageDirection.SEND) {
            ImageView statusView = itemView.findViewById(R.id.msg_read_status);
            if (statusView != null && statusView.getVisibility() == View.VISIBLE) {
                int msgState = message.getMessage().getState() != null
                        ? message.getMessage().getState().getValue()
                        : -1;
                if (!message.getMessage().isHasRead() && msgState == Message.MessageState.SENT.getValue()) {
                    statusView.setColorFilter(ContextCompat.getColor(itemView.getContext(), R.color.conversation_secondary_text));
                } else {
                    statusView.clearColorFilter();
                }
            }
        }
    }

    private String formatFileSize(long sizeBytes) {
        if (sizeBytes <= 0) {
            return AppRes.string(R.string.file_unknown_size);
        }
        if (sizeBytes < 1024) {
            return sizeBytes + " B";
        }
        double size = sizeBytes / 1024d;
        String unit = "KB";
        if (size >= 1024) {
            size /= 1024d;
            unit = "MB";
        }
        if (size >= 1024) {
            size /= 1024d;
            unit = "GB";
        }
        return String.format(Locale.getDefault(), "%.2f %s", size, unit);
    }

    private String getFileExtension(String fileName) {
        if (TextUtils.isEmpty(fileName)) {
            return "";
        }
        int idx = fileName.lastIndexOf(".");
        if (idx < 0 || idx >= fileName.length() - 1) {
            return "";
        }
        return fileName.substring(idx + 1);
    }

    private String getFileExtensionFromUrl(String url) {
        if (TextUtils.isEmpty(url)) {
            return "";
        }
        String pureUrl = url;
        int queryIndex = pureUrl.indexOf('?');
        if (queryIndex > 0) {
            pureUrl = pureUrl.substring(0, queryIndex);
        }
        return getFileExtension(pureUrl);
    }

    private File buildDownloadTargetFile(@NonNull Context context, @NonNull String fileName) {
        File externalDownloads = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
        File parentDir = externalDownloads != null ? externalDownloads : context.getFilesDir();
        File downloadDir = new File(parentDir, DOWNLOAD_DIR_NAME);
        if (!downloadDir.exists()) {
            downloadDir.mkdirs();
        }
        String safeName = sanitizeFileName(fileName);
        if (TextUtils.isEmpty(safeName)) {
            safeName = "file_" + System.currentTimeMillis();
        }
        return new File(downloadDir, safeName);
    }

    private String sanitizeFileName(String rawName) {
        if (rawName == null) {
            return "";
        }
        return rawName.trim().replaceAll("[\\\\/:*?\"<>|]", "_");
    }

    private void openFile(Context context, File file) {
        try {
            Uri fileUri = FileProvider.getUriForFile(context, context.getPackageName() + ".fileprovider", file);

            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(fileUri, getMimeType(file.getAbsolutePath()));
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

            String extension = getFileExtension(file.getName()).toLowerCase(Locale.getDefault());
            switch (extension) {
                case "jpg":
                case "jpeg":
                case "png":
                case "gif":
                case "bmp":
                    Intent imageIntent = new Intent(Intent.ACTION_VIEW);
                    imageIntent.setDataAndType(fileUri, "image/*");
                    imageIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    context.startActivity(Intent.createChooser(imageIntent, AppRes.string(R.string.file_chooser_image)));
                    break;
                case "txt":
                    context.startActivity(Intent.createChooser(intent, AppRes.string(R.string.file_chooser_text)));
                    break;
                case "pdf":
                    context.startActivity(Intent.createChooser(intent, AppRes.string(R.string.file_chooser_pdf)));
                    break;
                default:
                    context.startActivity(Intent.createChooser(intent, AppRes.string(R.string.file_chooser_default)));
                    break;
            }
        } catch (ActivityNotFoundException e) {
            Log.e(TAG, "无法找到适合的应用打开文件", e);
            Toast.makeText(context, R.string.file_no_app, Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Log.e(TAG, "打开文件时出错", e);
            Toast.makeText(context, R.string.file_open_error, Toast.LENGTH_SHORT).show();
        }
    }

    private String getMimeType(String filePath) {
        String extension = getFileExtension(filePath).toLowerCase(Locale.getDefault());
        String mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension);
        return mimeType != null ? mimeType : "*/*";
    }

    private int dp(View view, int value) {
        return Math.round(value * view.getResources().getDisplayMetrics().density);
    }

    private static DownloadTask getOrCreateTask(@NonNull String taskKey) {
        DownloadTask task = DOWNLOAD_TASKS.get(taskKey);
        if (task != null) {
            return task;
        }
        DownloadTask newTask = new DownloadTask(taskKey);
        DownloadTask oldTask = DOWNLOAD_TASKS.putIfAbsent(taskKey, newTask);
        return oldTask == null ? newTask : oldTask;
    }

    private static void addTaskListener(@NonNull String taskKey, @NonNull DownloadStateListener listener) {
        DownloadTask task = getOrCreateTask(taskKey);
        if (!task.listeners.contains(listener)) {
            task.listeners.add(listener);
        }
        listener.onStateChanged(task.toSnapshot());
    }

    private static void removeTaskListener(@NonNull String taskKey, @NonNull DownloadStateListener listener) {
        DownloadTask task = DOWNLOAD_TASKS.get(taskKey);
        if (task == null) {
            return;
        }
        task.listeners.remove(listener);
    }

    private static DownloadSnapshot getTaskSnapshot(@NonNull String taskKey) {
        DownloadTask task = DOWNLOAD_TASKS.get(taskKey);
        if (task == null) {
            return new DownloadSnapshot(taskKey, DownloadState.IDLE, 0, "", "");
        }
        synchronized (task) {
            return task.toSnapshot();
        }
    }

    private static long markTaskDownloading(@NonNull String taskKey, @NonNull Call call) {
        DownloadTask task = getOrCreateTask(taskKey);
        synchronized (task) {
            task.runId = System.nanoTime();
            task.call = call;
            task.state = DownloadState.DOWNLOADING;
            task.progress = 0;
            task.errorMessage = "";
        }
        notifyTaskListeners(task);
        return task.runId;
    }

    private static void updateTaskProgress(@NonNull String taskKey, long runId, int progress) {
        DownloadTask task = DOWNLOAD_TASKS.get(taskKey);
        if (task == null) {
            return;
        }
        synchronized (task) {
            if (task.runId != runId) {
                return;
            }
            task.progress = Math.max(0, Math.min(100, progress));
            task.state = DownloadState.DOWNLOADING;
        }
        notifyTaskListeners(task);
    }

    private static void markTaskSuccess(@NonNull String taskKey, long runId, @NonNull String localPath) {
        DownloadTask task = getOrCreateTask(taskKey);
        synchronized (task) {
            if (runId > 0L && task.runId != runId) {
                return;
            }
            task.state = DownloadState.SUCCESS;
            task.progress = 100;
            task.localPath = localPath;
            task.errorMessage = "";
            task.call = null;
        }
        notifyTaskListeners(task);
    }

    private static void markTaskFailed(@NonNull String taskKey, long runId, @Nullable String errorMessage) {
        DownloadTask task = getOrCreateTask(taskKey);
        synchronized (task) {
            if (runId > 0L && task.runId != runId) {
                return;
            }
            task.state = DownloadState.FAILED;
            task.errorMessage = errorMessage == null ? "" : errorMessage;
            task.call = null;
        }
        notifyTaskListeners(task);
    }

    private static void markTaskCanceled(@NonNull String taskKey, long runId) {
        DownloadTask task = getOrCreateTask(taskKey);
        synchronized (task) {
            if (runId > 0L && task.runId != runId) {
                return;
            }
            task.state = DownloadState.CANCELED;
            task.errorMessage = "canceled";
            task.call = null;
        }
        notifyTaskListeners(task);
    }

    private static void notifyTaskListeners(@NonNull DownloadTask task) {
        DownloadSnapshot snapshot;
        synchronized (task) {
            snapshot = task.toSnapshot();
        }
        for (DownloadStateListener listener : task.listeners) {
            try {
                listener.onStateChanged(snapshot);
            } catch (Throwable ignored) {
            }
        }
    }

    private static void closeQuietly(@Nullable Response response) {
        if (response == null) {
            return;
        }
        try {
            response.close();
        } catch (Exception ignored) {
        }
    }
}

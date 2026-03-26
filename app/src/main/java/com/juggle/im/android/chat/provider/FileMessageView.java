package com.juggle.im.android.chat.provider;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.net.Uri;
import android.os.Environment;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.MimeTypeMap;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
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

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

/**
 * 文件消息内容视图。
 */
public class FileMessageView extends MessageView<UiMessage, FileMessage> {
    private static final String TAG = "FileMessageView";

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
     * @param m       消息对象
     * @param f       文件消息内容
     * @param isGroup 是否群聊
     */
    @Override
    public void bindItem(UiMessage m, FileMessage f, boolean isGroup) {
        ImageView ivIcon = this.itemView.findViewById(R.id.image_file_icon);
        TextView tvName = this.itemView.findViewById(R.id.text_file_name);
        TextView tvSize = this.itemView.findViewById(R.id.text_file_size);
        View clickContainer = this.itemView.findViewById(R.id.layout_file_container);

        applyFileBubbleStyle(m);

        if (ivIcon != null) {
            ivIcon.setImageResource(R.drawable.ic_msg_type_file);
        }

        final String fileName = resolveFileName(f);
        if (tvName != null) {
            tvName.setText(fileName);
            tvName.setTextColor(ContextCompat.getColor(itemView.getContext(), R.color.title));
        }
        if (tvSize != null) {
            tvSize.setText(formatFileSize(resolveFileSize(f)));
            tvSize.setTextColor(ContextCompat.getColor(itemView.getContext(), R.color.conversation_secondary_text));
        }

        View actionArea = clickContainer == null ? itemView : clickContainer;
        actionArea.setOnClickListener(v -> openOrDownloadFile(v.getContext(), f, fileName));
        actionArea.setOnLongClickListener(v -> {
            View parent = (View) this.itemView.getParent();
            if (parent != null) {
                parent.performLongClick();
            }
            return false;
        });
    }

    private String resolveFileName(FileMessage message) {
        if (message == null || TextUtils.isEmpty(message.getName())) {
            return "未知文件";
        }
        return message.getName().trim();
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

        android.widget.ProgressBar sendProgress = itemView.findViewById(R.id.msg_send_status);
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

    private String formatFileSize(long sizeBytes) {
        if (sizeBytes <= 0) {
            return "未知大小";
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

    private void openOrDownloadFile(Context context, FileMessage message, String fileName) {
        if (context == null || message == null) {
            return;
        }
        String localPath = message.getLocalPath();
        if (!TextUtils.isEmpty(localPath)) {
            File localFile = new File(localPath);
            if (localFile.exists()) {
                openFile(context, localFile);
                return;
            }
        }

        String url = message.getUrl();
        if (TextUtils.isEmpty(url)) {
            Toast.makeText(context, "文件链接无效", Toast.LENGTH_SHORT).show();
            return;
        }
        downloadAndOpenFile(context, url, fileName);
    }

    /**
     * 获取文件扩展名。
     */
    private String getFileExtension(String fileName) {
        if (fileName == null || fileName.lastIndexOf(".") == -1) {
            return "";
        }
        return fileName.substring(fileName.lastIndexOf(".") + 1);
    }

    /**
     * 下载并打开文件。
     */
    private void downloadAndOpenFile(Context context, String fileUrl, String fileName) {
        File downloadDir = new File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "juggle_files");
        if (!downloadDir.exists()) {
            downloadDir.mkdirs();
        }

        String safeFileName = TextUtils.isEmpty(fileName) ? ("file_" + System.currentTimeMillis()) : fileName;
        File localFile = new File(downloadDir, safeFileName);

        if (localFile.exists()) {
            openFile(context, localFile);
            return;
        }

        OkHttpClient client = new OkHttpClient();
        Request request = new Request.Builder()
                .url(fileUrl)
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                Log.e(TAG, "文件下载失败", e);
                if (context instanceof android.app.Activity) {
                    ((android.app.Activity) context).runOnUiThread(() ->
                        Toast.makeText(context, "文件下载失败: " + e.getMessage(), Toast.LENGTH_SHORT).show()
                    );
                }
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                if (!response.isSuccessful()) {
                    Log.e(TAG, "文件下载失败，响应码: " + response.code());
                    if (context instanceof android.app.Activity) {
                        ((android.app.Activity) context).runOnUiThread(() ->
                            Toast.makeText(context, "文件下载失败，响应码: " + response.code(), Toast.LENGTH_SHORT).show()
                        );
                    }
                    return;
                }
                
                ResponseBody responseBody = response.body();
                if (responseBody == null) {
                    Log.e(TAG, "文件下载失败，响应体为空");
                    if (context instanceof android.app.Activity) {
                        ((android.app.Activity) context).runOnUiThread(() ->
                            Toast.makeText(context, "文件下载失败，响应体为空", Toast.LENGTH_SHORT).show()
                        );
                    }
                    return;
                }

                try (InputStream inputStream = responseBody.byteStream();
                     FileOutputStream outputStream = new FileOutputStream(localFile)) {

                    byte[] buffer = new byte[4096];
                    int bytesRead;
                    while ((bytesRead = inputStream.read(buffer)) != -1) {
                        outputStream.write(buffer, 0, bytesRead);
                    }

                    if (context instanceof android.app.Activity) {
                        ((android.app.Activity) context).runOnUiThread(() ->
                            openFile(context, localFile)
                        );
                    }
                } catch (IOException e) {
                    Log.e(TAG, "文件保存失败", e);
                    if (context instanceof android.app.Activity) {
                        ((android.app.Activity) context).runOnUiThread(() ->
                            Toast.makeText(context, "文件保存失败: " + e.getMessage(), Toast.LENGTH_SHORT).show()
                        );
                    }
                }
            }
        });
    }

    /**
     * 根据文件类型打开文件。
     */
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
                    context.startActivity(Intent.createChooser(imageIntent, "查看图片"));
                    break;

                case "txt":
                    context.startActivity(Intent.createChooser(intent, "打开文本文件"));
                    break;

                case "pdf":
                    context.startActivity(Intent.createChooser(intent, "打开PDF文件"));
                    break;

                default:
                    context.startActivity(Intent.createChooser(intent, "打开文件"));
                    break;
            }
        } catch (ActivityNotFoundException e) {
            Log.e(TAG, "无法找到适合的应用打开文件", e);
            Toast.makeText(context, "无法找到适合的应用打开文件", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Log.e(TAG, "打开文件时出错", e);
            Toast.makeText(context, "打开文件时出错: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * 获取文件 MIME 类型。
     */
    private String getMimeType(String filePath) {
        String extension = getFileExtension(filePath).toLowerCase(Locale.getDefault());
        String mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension);
        return mimeType != null ? mimeType : "*/*";
    }

    private int dp(View view, int value) {
        return Math.round(value * view.getResources().getDisplayMetrics().density);
    }
}

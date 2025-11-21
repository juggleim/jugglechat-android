package com.juggle.im.android.chat.provider;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.net.Uri;
import android.os.Environment;
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
import java.util.Locale;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

/**
 * File message content view.
 */
public class FileMessageView extends MessageView<UiMessage, FileMessage> {
    private static final String TAG = "FileMessageView";
    
    public FileMessageView(@NonNull ViewGroup root) {
        super(root, R.layout.content_file);
    }

    @Override
    public void bindItem(UiMessage m, FileMessage f, boolean isGroup) {
        ImageView ivIcon = this.itemView.findViewById(R.id.image_file_icon);
        TextView tvName = this.itemView.findViewById(R.id.text_file_name);
        ImageView btnDownload = this.itemView.findViewById(R.id.button_download_file);
        
        // 设置文件图标
        setFileIconByExtension(ivIcon, f.getName());
        
        String name = f.getName();
        String url = f.getUrl();
        if (tvName != null) tvName.setText(name);
        if (btnDownload != null) {
            btnDownload.setOnClickListener(v -> {
                if (url != null && !url.isEmpty()) {
                    downloadAndOpenFile(v.getContext(), url, name);
                } else {
                    Toast.makeText(v.getContext(), "文件链接无效", Toast.LENGTH_SHORT).show();
                }
            });
        }
        if (tvName != null) {
            if (m.getMessage().getDirection() == Message.MessageDirection.SEND) {
                tvName.setTextColor(ColorStateList.valueOf(Color.WHITE));
            } else {
                tvName.setTextColor(ContextCompat.getColor(itemView.getContext(), R.color.black));
            }
        }
    }
    
    /**
     * 根据文件扩展名设置文件图标
     */
    private void setFileIconByExtension(ImageView iconView, String fileName) {
        String extension = getFileExtension(fileName).toLowerCase(Locale.getDefault());
        switch (extension) {
            case "txt":
                iconView.setImageResource(R.drawable.ic_txt);
                break;
            case "jpg":
            case "jpeg":
            case "png":
            case "gif":
            case "bmp":
                iconView.setImageResource(R.drawable.ic_input_img);
                break;
            case "pdf":
                iconView.setImageResource(R.drawable.ic_pdf);
                break;
            case "doc":
            case "docx":
                iconView.setImageResource(R.drawable.ic_word);
                break;
            case "xls":
            case "xlsx":
                iconView.setImageResource(R.drawable.ic_word);
                break;
            case "ppt":
            case "pptx":
                iconView.setImageResource(R.drawable.ic_word);
                break;
            default:
                iconView.setImageResource(R.drawable.ic_file);
                break;
        }
    }
    
    /**
     * 获取文件扩展名
     */
    private String getFileExtension(String fileName) {
        if (fileName == null || fileName.lastIndexOf(".") == -1) {
            return "";
        }
        return fileName.substring(fileName.lastIndexOf(".") + 1);
    }
    
    /**
     * 下载并打开文件
     */
    private void downloadAndOpenFile(Context context, String fileUrl, String fileName) {
        // 创建下载目录
        File downloadDir = new File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "juggle_files");
        if (!downloadDir.exists()) {
            downloadDir.mkdirs();
        }
        
        File localFile = new File(downloadDir, fileName);
        
        // 如果文件已存在，直接打开
        if (localFile.exists()) {
            openFile(context, localFile);
            return;
        }
        
        // 使用OkHttpClient下载文件
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
                
                // 写入文件
                try (InputStream inputStream = responseBody.byteStream();
                     FileOutputStream outputStream = new FileOutputStream(localFile)) {
                    
                    byte[] buffer = new byte[4096];
                    int bytesRead;
                    while ((bytesRead = inputStream.read(buffer)) != -1) {
                        outputStream.write(buffer, 0, bytesRead);
                    }
                    
                    // 下载完成，打开文件
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
     * 根据文件类型打开文件
     */
    private void openFile(Context context, File file) {
        try {
            Uri fileUri = FileProvider.getUriForFile(context, context.getPackageName() + ".fileprovider", file);
            
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(fileUri, getMimeType(file.getAbsolutePath()));
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            
            String extension = getFileExtension(file.getName()).toLowerCase(Locale.getDefault());
            
            // 根据文件类型进行特殊处理
            switch (extension) {
                case "jpg":
                case "jpeg":
                case "png":
                case "gif":
                case "bmp":
                    // 图片文件使用内部图片预览器
                    Intent imageIntent = new Intent(Intent.ACTION_VIEW);
                    imageIntent.setDataAndType(fileUri, "image/*");
                    imageIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    context.startActivity(Intent.createChooser(imageIntent, "查看图片"));
                    break;
                    
                case "txt":
                    // 文本文件可以直接查看
                    context.startActivity(Intent.createChooser(intent, "打开文本文件"));
                    break;
                    
                case "pdf":
                    // PDF文件
                    context.startActivity(Intent.createChooser(intent, "打开PDF文件"));
                    break;
                    
                default:
                    // 其他文件类型
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
     * 获取文件MIME类型
     */
    private String getMimeType(String filePath) {
        String extension = getFileExtension(filePath);
        String mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension);
        return mimeType != null ? mimeType : "*/*";
    }
}
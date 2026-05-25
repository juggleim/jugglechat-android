package com.juggle.im.android.chat.utils;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.OpenableColumns;
import android.util.Log;

import androidx.core.content.FileProvider;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.HashMap;
import java.util.Map;
import java.util.Date;
import java.util.Locale;

public class FileUtils {
    private static final String TAG = "FileUtils";
    private static final Map<String, String> MIME_EXTENSION_MAP = createMimeExtensionMap();

    /**
     * Content Uri 复制结果。
     */
    public static final class CopiedContentFile {
        private final String localPath;
        private final String displayName;

        /**
         * 构造复制结果对象。
         *
         * @param localPath 复制后的本地路径
         * @param displayName 文件展示名
         */
        public CopiedContentFile(String localPath, String displayName) {
            this.localPath = localPath == null ? "" : localPath;
            this.displayName = displayName == null ? "" : displayName;
        }

        /**
         * 获取复制后的本地路径。
         *
         * @return 文件本地路径
         */
        public String getLocalPath() {
            return localPath;
        }

        /**
         * 获取文件展示名。
         *
         * @return 文件展示名
         */
        public String getDisplayName() {
            return displayName;
        }
    }

    /**
     * 将 `content://` 文件复制到应用缓存目录，并返回可发送的本地路径与展示名。
     *
     * <p>简要描述：该方法会优先保留文档原始文件名和后缀，避免出现“全部被保存为 jpg”导致
     * 文件类型展示错误、点击打开失败的问题。</p>
     *
     * @param context Android 上下文
     * @param uriValue 文件 Uri 字符串
     * @return 复制结果（包含本地路径和展示名）
     */
    public static CopiedContentFile copyContentUriToCache(Context context, String uriValue) {
        if (context == null) {
            return new CopiedContentFile("", resolveAttachmentDisplayName(null, uriValue));
        }
        if (isBlank(uriValue)) {
            return new CopiedContentFile("", resolveAttachmentDisplayName(null, null));
        }
        if (!uriValue.startsWith("content://")) {
            return new CopiedContentFile(uriValue, resolveAttachmentDisplayName(null, uriValue));
        }

        Uri uri = Uri.parse(uriValue);
        ContentResolver resolver = context.getContentResolver();
        String rawDisplayName = queryDisplayName(resolver, uri);
        String mimeType = resolver.getType(uri);
        String displayName = resolveAttachmentDisplayName(rawDisplayName, uriValue);
        String extension = resolveExtension(displayName, mimeType);
        String cacheFileName = buildCacheFileName(displayName, extension);
        File targetFile = new File(context.getCacheDir(), cacheFileName);

        try (InputStream inputStream = resolver.openInputStream(uri);
             OutputStream outputStream = new FileOutputStream(targetFile)) {
            if (inputStream == null) {
                return new CopiedContentFile("", displayName);
            }
            copyStream(inputStream, outputStream);
            return new CopiedContentFile(targetFile.getAbsolutePath(), displayName);
        } catch (Exception e) {
            Log.e(TAG, "Error copying content Uri to cache", e);
            return new CopiedContentFile("", displayName);
        }
    }

    /**
     * 解析文件展示名：优先使用文档返回名，缺失时回退本地路径文件名。
     *
     * @param preferredName 文档查询到的展示名
     * @param localPath 本地路径（用于兜底）
     * @return 可展示文件名
     */
    public static String resolveAttachmentDisplayName(String preferredName, String localPath) {
        if (!isBlank(preferredName)) {
            return preferredName.trim();
        }
        if (!isBlank(localPath)) {
            String name = new File(localPath).getName();
            if (!isBlank(name)) {
                return name;
            }
        }
        return "file_" + System.currentTimeMillis();
    }

    /**
     * 解析文件扩展名：优先从文件名取后缀，缺失时再从 MIME 类型推断。
     *
     * @param fileName 文件名
     * @param mimeType MIME 类型
     * @return 扩展名（不含点）
     */
    public static String resolveExtension(String fileName, String mimeType) {
        if (!isBlank(fileName)) {
            int lastDot = fileName.lastIndexOf('.');
            if (lastDot >= 0 && lastDot < fileName.length() - 1) {
                return fileName.substring(lastDot + 1).trim().toLowerCase(Locale.US);
            }
        }
        if (isBlank(mimeType)) {
            return "";
        }

        String normalizedMime = mimeType.trim().toLowerCase(Locale.US);
        String mapped = MIME_EXTENSION_MAP.get(normalizedMime);
        if (!isBlank(mapped)) {
            return mapped;
        }

        int slashIndex = normalizedMime.indexOf('/');
        if (slashIndex < 0 || slashIndex >= normalizedMime.length() - 1) {
            return "";
        }
        String ext = normalizedMime.substring(slashIndex + 1);
        int semicolonIndex = ext.indexOf(';');
        if (semicolonIndex > 0) {
            ext = ext.substring(0, semicolonIndex);
        }
        int plusIndex = ext.indexOf('+');
        if (plusIndex > 0) {
            ext = ext.substring(0, plusIndex);
        }
        if ("plain".equals(ext)) {
            return "txt";
        }
        return ext;
    }

    public static String convertContentUriToFile(Context context, String contentUri) {
        CopiedContentFile copiedFile = copyContentUriToCache(context, contentUri);
        if (isBlank(copiedFile.getLocalPath())) {
            return contentUri == null ? "" : contentUri;
        }
        return copiedFile.getLocalPath();
    }

    /**
     * 删除临时文件
     */
    public static boolean deleteTempFile(File tempFile) {
        if (tempFile != null && tempFile.exists()) {
            return tempFile.delete();
        }
        return false;
    }

    /**
     * create temp file
     *
     * @param context
     * @return
     * @throws IOException
     */
    public static Uri createTmpImageFile(Context context) {
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
        String imageFileName = "JPEG_" + timeStamp + "_";
        File storageDir = context.getCacheDir();
        try {
            File photoFile = File.createTempFile(
                    imageFileName,
                    ".jpg",
                    storageDir
            );
            // Use packageName + ".fileprovider" so it matches the authority declared in AndroidManifest
            String authority = context.getPackageName() + ".fileprovider";
            Uri photoURI = FileProvider.getUriForFile(context, authority, photoFile);
            return photoURI;
        } catch (Exception e) {
            Log.e(TAG, "Error creating temp file", e);
            throw new RuntimeException(e);
        }
    }

    private static String queryDisplayName(ContentResolver resolver, Uri uri) {
        Cursor cursor = null;
        try {
            cursor = resolver.query(uri, new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null);
            if (cursor != null && cursor.moveToFirst()) {
                int idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (idx >= 0) {
                    String value = cursor.getString(idx);
                    return value == null ? "" : value;
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "query display name failed", e);
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        return "";
    }

    private static String buildCacheFileName(String displayName, String extension) {
        String safeName = sanitizeFileName(displayName);
        if (isBlank(safeName)) {
            safeName = "file";
        }
        String ext = isBlank(extension) ? "" : extension.trim().toLowerCase(Locale.US);
        if (!isBlank(ext) && !safeName.toLowerCase(Locale.US).endsWith("." + ext)) {
            safeName = safeName + "." + ext;
        }
        return System.currentTimeMillis() + "_" + safeName;
    }

    private static String sanitizeFileName(String rawName) {
        if (rawName == null) {
            return "";
        }
        return rawName.trim()
                .replaceAll("[\\\\/:*?\"<>|]", "_")
                .replaceAll("\\s+", " ");
    }

    private static void copyStream(InputStream inputStream, OutputStream outputStream) throws IOException {
        byte[] buffer = new byte[8 * 1024];
        int length;
        while ((length = inputStream.read(buffer)) > 0) {
            outputStream.write(buffer, 0, length);
        }
        outputStream.flush();
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private static Map<String, String> createMimeExtensionMap() {
        Map<String, String> map = new HashMap<>();
        map.put("application/pdf", "pdf");
        map.put("application/msword", "doc");
        map.put("application/vnd.openxmlformats-officedocument.wordprocessingml.document", "docx");
        map.put("application/vnd.ms-excel", "xls");
        map.put("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "xlsx");
        map.put("application/vnd.ms-powerpoint", "ppt");
        map.put("application/vnd.openxmlformats-officedocument.presentationml.presentation", "pptx");
        map.put("text/plain", "txt");
        map.put("application/zip", "zip");
        map.put("application/x-zip-compressed", "zip");
        map.put("image/jpeg", "jpg");
        map.put("image/png", "png");
        return map;
    }
}

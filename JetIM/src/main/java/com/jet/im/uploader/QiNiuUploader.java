package com.jet.im.uploader;

import android.text.TextUtils;

import com.jet.im.model.upload.UploadQiNiuCred;
import com.qiniu.android.storage.UpCompletionHandler;
import com.qiniu.android.storage.UploadManager;
import com.qiniu.android.storage.UploadOptions;

/**
 * @author Ye_Guli
 * @create 2024-05-29 9:12
 */
public class QiNiuUploader extends BaseUploader {
    private final UploadQiNiuCred mQiNiuCred;
    private volatile boolean isCancel = false;

    public QiNiuUploader(String localPath, UploaderCallback uploaderCallback, UploadQiNiuCred qiNiuCred) {
        super(localPath, uploaderCallback);
        this.mQiNiuCred = qiNiuCred;
    }

    @Override
    public void start() {
        //判空文件地址
        if (TextUtils.isEmpty(mLocalPath)) {
            notifyFail();
            return;
        }
        //判空mQiNiuCred
        if (mQiNiuCred == null || TextUtils.isEmpty(mQiNiuCred.getToken()) || TextUtils.isEmpty(mQiNiuCred.getDomain())) {
            notifyFail();
            return;
        }
        //获取文件名
        String fileName = FileUtil.getFileName(mLocalPath);
        //判空文件名
        if (TextUtils.isEmpty(fileName)) {
            notifyFail();
            return;
        }
        //声明完成回调
        UpCompletionHandler completionHandler = (key, info, response) -> {
            if (!fileName.equals(key)) return;

            if (info.isCancelled()) {
                notifyCancel();
                return;
            }
            if (info.isOK()) {
                try {
                    String fileKey = response.getString("key");
                    String url = mQiNiuCred.getDomain() + "/" + fileKey;
                    notifySuccess(url);
                    return;
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
            notifyFail();
        };
        //声明进度回调
        UploadOptions options = new UploadOptions(null, null, false,
                (key, percent) -> {
                    if (!fileName.equals(key)) return;

                    notifyProgress((int) (percent * 100));
                },
                () -> isCancel);
        //开始上传
        UploadManager uploadManager = new UploadManager();
        uploadManager.put(mLocalPath, fileName, mQiNiuCred.getToken(), completionHandler, options);
    }

    @Override
    public void cancel() {
        isCancel = true;
    }
}
package com.jet.im.internal.uploader;

import android.annotation.SuppressLint;
import android.text.TextUtils;

import com.jet.im.internal.model.upload.UploadPreSignCred;
import com.jet.im.internal.util.JLogger;
import com.jet.im.internal.util.JThreadPoolExecutor;

import java.io.BufferedInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.net.HttpURLConnection;
import java.net.URL;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLSession;

/**
 * @author Ye_Guli
 * @create 2024-05-29 9:12
 */
public class PreSignUploader extends BaseUploader {
    private static final int BUFFER_SIZE = 4096; //缓冲区大小
    private static final int CONNECT_TIMEOUT = 20 * 1000; //连接超时时间
    private static final int READ_TIMEOUT = 20 * 1000; //读取超时时间

    private final UploadPreSignCred mPreSignCred;
    private volatile boolean mIsCancelled = false;

    public PreSignUploader(String localPath, UploaderCallback uploaderCallback, UploadPreSignCred preSignCred) {
        super(localPath, uploaderCallback);
        this.mPreSignCred = preSignCred;
    }

    @Override
    public void start() {
        //判空文件地址
        if (TextUtils.isEmpty(mLocalPath)) {
            JLogger.e("J-Uploader, PreSignUploader error, mLocalPath is empty");
            notifyFail();
            return;
        }
        //判空mPreSignCred
        if (mPreSignCred == null || TextUtils.isEmpty(mPreSignCred.getUrl())) {
            JLogger.e("J-Uploader, PreSignUploader error, mPreSignCred is null or empty");
            notifyFail();
            return;
        }
        //获取文件名
        String fileName = FileUtil.getFileName(mLocalPath);
        //判空文件名
        if (TextUtils.isEmpty(fileName)) {
            JLogger.e("J-Uploader, PreSignUploader error, fileName is empty");
            notifyFail();
            return;
        }
        //开始上传
        JThreadPoolExecutor.runInBackground(() -> {
            HttpURLConnection conn = null;
            FileInputStream fileInputStream = null;
            try {
                //创建URL对象
                URL url = new URL(mPreSignCred.getUrl());
                conn = (HttpURLConnection) url.openConnection();
                if (conn instanceof HttpsURLConnection) {
                    ((HttpsURLConnection) conn).setHostnameVerifier(new HostnameVerifier() {
                        @SuppressLint("BadHostnameVerifier")
                        @Override
                        public boolean verify(String hostname, SSLSession session) {
                            return true;
                        }
                    });
                }
                conn.setRequestMethod("PUT");
                conn.setDoInput(true);
                conn.setDoOutput(true);
                conn.setUseCaches(false);
                conn.setAllowUserInteraction(false);
                conn.setRequestProperty("Charset", "UTF-8");
                conn.setRequestProperty("connection", "keep-alive");

                //设置连接超时和读取超时
                conn.setConnectTimeout(CONNECT_TIMEOUT);
                conn.setReadTimeout(READ_TIMEOUT);

                //打开文件流
                File file = new File(mLocalPath);
                fileInputStream = new FileInputStream(file);
                BufferedInputStream bufferedInputStream = new BufferedInputStream(fileInputStream);

                //设置Content-Length
                conn.setRequestProperty("Content-Length", String.valueOf(file.length()));
                conn.setRequestProperty("Content-Type", null);

                //获取输出流
                DataOutputStream outputStream = new DataOutputStream(conn.getOutputStream());

                //读取文件内容并写入输出流
                byte[] buffer = new byte[BUFFER_SIZE];
                int bytesRead;
                long totalBytesRead = 0;
                long fileSize = file.length();
                while (!mIsCancelled && (bytesRead = bufferedInputStream.read(buffer)) != -1) {
                    outputStream.write(buffer, 0, bytesRead);
                    totalBytesRead += bytesRead;
                    //计算上传进度并通知
                    double percent = (double) totalBytesRead / fileSize;
                    notifyProgress((int) (percent * 100));
                }
                outputStream.flush();

                //检查是否取消上传
                if (mIsCancelled) {
                    JLogger.i("J-Uploader, PreSignUploader canceled");
                    notifyCancel();
                    return;
                }

                //获取响应码
                int responseCode = conn.getResponseCode();
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    //获取预签名上传地址去除query部分
                    String modifiedUrl = removeQueryFromUrl(mPreSignCred.getUrl());
                    //回调上传成功
                    notifySuccess(modifiedUrl);
                } else {
                    JLogger.e("J-Uploader, PreSignUploader error, responseCode is " + responseCode + ", responseMessage is " + conn.getResponseMessage());
                    //回调上传失败
                    notifyFail();
                }
            } catch (Exception e) {
                JLogger.e("J-Uploader, PreSignUploader error, exception is " + e.getMessage());
                notifyFail();
            } finally {
                if (conn != null) {
                    conn.disconnect();
                }
                try {
                    if (fileInputStream != null) {
                        fileInputStream.close();
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });
    }

    @Override
    public void cancel() {
        mIsCancelled = true;
    }

    //移除URL的query部分
    private String removeQueryFromUrl(String url) {
        int index = url.indexOf("?");
        if (index != -1) {
            return url.substring(0, index);
        }
        return url;
    }
}
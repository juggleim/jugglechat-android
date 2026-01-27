package com.juggle.im.android.chat.plugin;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import com.juggle.im.android.R;
import com.juggle.im.android.service.ImForegroundService;

public class FilePlugin extends MorePlugin {
    public static final String ID = "file";
    public static final int REQ = 12002;

    private Activity host;

    public FilePlugin(Callback callback) {
        super(callback);
    }

    @Override
    public String getId() { return ID; }

    @Override
    public int getIconRes() { return R.drawable.ic_input_file; }

    @Override
    public String getLabel(Context ctx) { return ctx.getString(R.string.attach_file); }

    @Override
    public String getAction() { return "file"; }

    @Override
    public String[] getRequiredPermissions() { return new String[]{}; }

    @Override
    public void onClick(Activity activity) {
        Activity act = activity != null ? activity : host;
        if (act == null) {
            callback.onPluginAction(getId(), getAction(), null);
            return;
        }
        Intent pickFile = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        pickFile.addCategory(Intent.CATEGORY_OPENABLE);
        pickFile.setType("*/*");
        callback.registerForActivityResult(REQ, this);
        act.startActivityForResult(Intent.createChooser(pickFile, "Select file"), REQ);

        startImForegroundService();
    }


    /**
     * 启动IM前台服务
     * 该服务会在后台保持IM连接，防止用户选择文件等场景下连接被系统回收
     */
    private void startImForegroundService() {
        Intent intent = new Intent(host, ImForegroundService.class);
        intent.setAction(ImForegroundService.ACTION_START);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            host.startForegroundService(intent);
        } else {
            host.startService(intent);
        }
    }

    private void stopImForegroundService() {
        // 停止IM前台服务
        Intent serviceIntent = new Intent(host, ImForegroundService.class);
        serviceIntent.setAction(ImForegroundService.ACTION_STOP);
        host.startService(serviceIntent);
    }

    @Override
    public void setHostActivity(Activity activity) {
        this.host = activity;
    }

    @Override
    public boolean onActivityResult(int requestCode, int resultCode, Intent data) {
        stopImForegroundService();
        if (requestCode != REQ) return false;
        if (resultCode != Activity.RESULT_OK) return true;
        if (data == null) return true;
        android.net.Uri uri = data.getData();
        if (uri != null && callback != null) {
            callback.onPluginAction(getId(), getAction(), uri);
        }
        return true;
    }
}

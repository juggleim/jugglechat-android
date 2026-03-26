package com.juggle.im.android.chat.plugin;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;

import com.juggle.im.android.R;
import com.juggle.im.android.utils.PermissionComponent;

public class VideoCallPlugin extends MorePlugin {
    public static final String ID = "call_video";
    public static final int REQ = 12007;

    public VideoCallPlugin(Callback callback) {
        super(callback);
    }

    @Override
    public String getId() {
        return ID;
    }

    @Override
    public int getIconRes() {
        return R.drawable.ic_more_video_call_design;
    }

    @Override
    public String getLabel(Context ctx) {
        return ctx.getString(R.string.call_video);
    }

    @Override
    public String getAction() {
        return ID;
    }

    @Override
    public String[] getRequiredPermissions() {
        return new String[]{Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO};
    }

    @Override
    public void onClick(Activity activity) {
        Activity act = activity != null ? activity : host;
        if (act == null) return;
        if (!PermissionComponent.hasAllPermissions(act, getRequiredPermissions())) {
            callback.requestPermissions(getRequiredPermissions(), REQ, getId());
            return;
        }
        callback.onPluginAction(getId(), getAction(), null);
    }

    @Override
    public void setHostActivity(Activity activity) {

    }

    @Override
    public boolean onActivityResult(int requestCode, int resultCode, Intent data) {
        return false;
    }
}

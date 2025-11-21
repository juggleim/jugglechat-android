package com.juggle.im.android.chat.plugin;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;

import androidx.core.content.ContextCompat;

import com.juggle.im.android.R;

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
        return R.drawable.ic_video_call;
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
        // check permissions
        boolean ok = true;
        for (String p : getRequiredPermissions()) {
            if (ContextCompat.checkSelfPermission(act, p) != PackageManager.PERMISSION_GRANTED) {
                ok = false; break;
            }
        }
        if (!ok) {
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

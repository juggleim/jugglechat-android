package com.juggle.im.android.chat.plugin;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;

import com.juggle.im.android.R;

public class TimedDeletePlugin extends MorePlugin {
    public static final String ID = "timed_delete";

    public TimedDeletePlugin(Callback callback) {
        super(callback);
    }

    @Override
    public String getId() {
        return ID;
    }

    @Override
    public int getIconRes() {
        return R.drawable.ic_more_timed_delete_design;
    }

    @Override
    public String getLabel(Context ctx) {
        return ctx.getString(R.string.timed_delete);
    }

    @Override
    public String getAction() {
        return ID;
    }

    @Override
    public String[] getRequiredPermissions() {
        return new String[0];
    }

    @Override
    public void onClick(Activity activity) {
        if (callback != null) {
            callback.onPluginAction(getId(), getAction(), null);
        }
    }

    @Override
    public boolean onActivityResult(int requestCode, int resultCode, Intent data) {
        return false;
    }
}

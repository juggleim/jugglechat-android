package com.juggle.im.android.chat.plugin;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;

import com.juggle.im.android.R;

public class LocationPlugin implements MorePlugin {
    public static final String ID = "location";
    public static final int REQ = 2003;

    @Override
    public String getId() { return ID; }

    @Override
    public int getIconRes() { return R.drawable.ic_input_location; }

    @Override
    public String getLabel(Context ctx) { return ctx.getString(R.string.location); }

    @Override
    public String getAction() { return "location"; }

    @Override
    public String[] getRequiredPermissions() { return new String[]{}; }

    @Override
    public void onClick(Activity activity, Callback callback) {
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

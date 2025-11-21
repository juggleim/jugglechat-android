package com.juggle.im.android.chat.plugin;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;

import com.juggle.im.android.R;

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
    }

    @Override
    public void setHostActivity(Activity activity) {
        this.host = activity;
    }

    @Override
    public boolean onActivityResult(int requestCode, int resultCode, Intent data) {
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

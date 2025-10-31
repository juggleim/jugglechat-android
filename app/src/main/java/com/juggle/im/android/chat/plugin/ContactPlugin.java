package com.juggle.im.android.chat.plugin;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;

import com.juggle.im.android.R;

public class ContactPlugin implements MorePlugin {
    public static final String ID = "contact";
    public static final int REQ = 2004;

    private Activity host;
    private MorePlugin.Callback callback;

    @Override
    public String getId() { return ID; }

    @Override
    public int getIconRes() { return R.drawable.ic_input_contact; }

    @Override
    public String getLabel(Context ctx) { return ctx.getString(R.string.contact); }

    @Override
    public String getAction() { return "contact"; }

    @Override
    public String[] getRequiredPermissions() { return new String[]{}; }

    @Override
    public void onClick(Activity activity, Callback callback) {
        this.callback = callback;
        Activity act = activity != null ? activity : host;
        if (act == null) {
            callback.onPluginAction(getId(), getAction(), null);
            return;
        }
        Intent pickContact = new Intent(Intent.ACTION_PICK, android.provider.ContactsContract.Contacts.CONTENT_URI);
        callback.registerForActivityResult(REQ, getId());
        act.startActivityForResult(pickContact, REQ);
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

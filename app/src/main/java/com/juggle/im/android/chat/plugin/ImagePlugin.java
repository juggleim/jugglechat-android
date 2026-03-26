package com.juggle.im.android.chat.plugin;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;

import com.juggle.im.android.R;
import com.juggle.im.android.chat.AlbumActivity;
import com.juggle.im.android.utils.PermissionComponent;

import java.util.ArrayList;

public class ImagePlugin extends MorePlugin {
    public static final String ID = "photo";
    public static final int REQ = 12001;


    public ImagePlugin(Callback callback) {
        super(callback);
    }

    @Override
    public String getId() { return ID; }

    @Override
    public int getIconRes() { return R.drawable.ic_more_album_design; }

    @Override
    public String getLabel(Context ctx) { return ctx.getString(R.string.photo); }

    @Override
    public String getAction() { return ID; }

    @Override
    public String[] getRequiredPermissions() {
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            return new String[] { android.Manifest.permission.READ_MEDIA_IMAGES };
        } else {
            return new String[] { android.Manifest.permission.READ_EXTERNAL_STORAGE };
        }
    }

    @Override
    public void onClick(Activity activity) {
        Activity act = activity != null ? activity : host;
        if (act == null) return;
        if (!PermissionComponent.hasAllPermissions(act, getRequiredPermissions())) {
            callback.requestPermissions(getRequiredPermissions(), REQ, getId());
            return;
        }
        // Intent pick = new Intent(Intent.ACTION_PICK);
        // pick.setType("image/*");
        callback.registerForActivityResult(REQ, this);
        // act.startActivityForResult(Intent.createChooser(pick, "Select image"), REQ);
        Intent intent = new Intent(act, AlbumActivity.class);
        act.startActivityForResult(intent, REQ);
    }

    @Override
    public boolean onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode != REQ) return false;
        if (resultCode != Activity.RESULT_OK) return true;
        if (data == null) return true;
        ArrayList<String> urls = data.getStringArrayListExtra("selected_images");
        if (urls != null && callback != null) {
            callback.onPluginAction(getId(), getAction(), urls);
        }
        return true;
    }

}

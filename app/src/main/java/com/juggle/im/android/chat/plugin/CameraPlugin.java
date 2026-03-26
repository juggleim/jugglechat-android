package com.juggle.im.android.chat.plugin;

import android.app.Activity;
import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import java.util.List;
import android.net.Uri;
import android.os.Build;
import android.provider.MediaStore;

import com.juggle.im.android.R;
import com.juggle.im.android.chat.utils.FileUtils;
import com.juggle.im.android.utils.PermissionComponent;

public class CameraPlugin extends MorePlugin {
    public static final String ID = "camera";
    public static final int REQ = 12005;

    public CameraPlugin(Callback callback) {
        super(callback);
    }

    @Override
    public String getId() { return ID; }

    @Override
    public int getIconRes() { return R.drawable.ic_more_camera_design; }

    @Override
    public String getLabel(Context ctx) { return ctx.getString(R.string.camera); }

    @Override
    public String getAction() { return ID; }

    @Override
    public String[] getRequiredPermissions() { return new String[]{Manifest.permission.CAMERA}; }

    private Activity host;

    private Uri photoUri;

    @Override
    public void setHostActivity(Activity activity) {
        this.host = activity;
    }

    @Override
    public void onClick(Activity activity) {
        Activity act = activity != null ? activity : host;
        if (act == null) return;
        if (!PermissionComponent.hasAllPermissions(act, getRequiredPermissions())) {
            callback.requestPermissions(getRequiredPermissions(), REQ, getId());
            return;
        }
        Intent take = new Intent(android.provider.MediaStore.ACTION_IMAGE_CAPTURE);
        // create tmp file using the actual Activity we will start from
        Uri photoURI = FileUtils.createTmpImageFile(act);
        photoUri = photoURI;
        take.putExtra(MediaStore.EXTRA_OUTPUT, photoURI);
        // grant temporary permissions to camera apps that will handle the intent
        take.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION | Intent.FLAG_GRANT_READ_URI_PERMISSION);
        List<ResolveInfo> resInfoList = act.getPackageManager().queryIntentActivities(take, PackageManager.MATCH_DEFAULT_ONLY);
        for (ResolveInfo resolveInfo : resInfoList) {
            String packageName = resolveInfo.activityInfo.packageName;
            act.grantUriPermission(packageName, photoURI, Intent.FLAG_GRANT_WRITE_URI_PERMISSION | Intent.FLAG_GRANT_READ_URI_PERMISSION);
        }
        callback.registerForActivityResult(REQ, this);
        act.startActivityForResult(take, REQ);
    }

    @Override
    public boolean onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode != REQ) return false;
        if (resultCode != Activity.RESULT_OK) return true;
        if (callback == null) return true;
        // Some camera apps return a small Bitmap in the data extras (thumbnail).
        // When EXTRA_OUTPUT is provided, many apps return null data and write to the provided Uri.
        if (data != null) {
            android.os.Bundle extras = data.getExtras();
            if (extras != null && extras.get("data") != null) {
                Object bm = extras.get("data");
                callback.onPluginAction(getId(), getAction(), bm);
                // clear stored uri since we already returned the bitmap
                photoUri = null;
                return true;
            }
        }
        // Fallback: return the photo Uri (as a string) if we created one
        if (photoUri != null) {
            callback.onPluginAction(getId(), getAction(), photoUri.toString());
            // revoke permissions we granted
            try {
                Activity act = host;
                if (act != null) {
                    List<ResolveInfo> resInfoList = act.getPackageManager().queryIntentActivities(new Intent(MediaStore.ACTION_IMAGE_CAPTURE), PackageManager.MATCH_DEFAULT_ONLY);
                    for (ResolveInfo resolveInfo : resInfoList) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            act.revokeUriPermission(resolveInfo.activityInfo.packageName, photoUri, Intent.FLAG_GRANT_WRITE_URI_PERMISSION | Intent.FLAG_GRANT_READ_URI_PERMISSION);
                        }
                    }
                }
            } catch (Exception ignored) {}
            photoUri = null;
        }
        return true;
    }
}

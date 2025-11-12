package com.juggle.im.android.chat.plugin;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;

/**
 * Interface describing a "more" panel plugin item.
 */
public abstract class MorePlugin {
    protected Callback callback = null;

    public MorePlugin(Callback callback) {
        this.callback = callback;
    }

    public abstract String getId();
    public abstract int getIconRes();
    public abstract String getLabel(Context ctx);
    /**
     * Secondary action id (optional) e.g. "camera", "gallery"; can be null.
     */
    public abstract String getAction();
    /**
     * Permissions required to execute this plugin (may be null or empty).
     */
    public abstract String[] getRequiredPermissions();

    /**
     * Called when the plugin is clicked. If permissions are missing, the plugin
     * should call callback.requestPermissions(...) and return. Otherwise it should
     * call callback.onPluginAction(...) to notify the host to perform the real work
     * (e.g. start camera intent).
     */
    public abstract void onClick(Activity activity);

    public interface Callback {
        /**
         * Notify host that plugin wants to perform an action.
         * @param pluginId plugin id
         * @param action action id or null
         * @param data optional extra object
         */
        void onPluginAction(String pluginId, String action, Object data);

        /**
         * Ask host to request runtime permissions on behalf of the plugin.
         * @param permissions array of permissions
         * @param requestCode request code the host will receive in onRequestPermissionsResult
         * @param pluginId plugin id (host will associate the pending request with this id)
         */
        void requestPermissions(String[] permissions, int requestCode, String pluginId);
        /**
         * Register that a plugin started an activity for result with requestCode so the host
         * can forward the result back to the plugin.
         */
        void registerForActivityResult(int requestCode, MorePlugin plugin);
    }

    /**
     * Give the plugin the host Activity so it can call startActivityForResult.
     */
    public abstract void setHostActivity(Activity activity);

    /**
     * Called by the host when an activity result for a requestCode registered by this plugin arrives.
     * Return true if the plugin consumed the result.
     */
    public abstract boolean onActivityResult(int requestCode, int resultCode, Intent data);
}

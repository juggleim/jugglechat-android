package com.juggle.im.android.utils;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

/**
 * 权限公共组件。
 *
 * 简要描述：
 * 统一处理运行时权限的“所需权限集合、是否已授权、发起请求、授权结果判断”，
 * 避免业务模块重复编写权限判断逻辑。
 */
public final class PermissionComponent {
    private PermissionComponent() {
    }

    /**
     * 获取通话所需权限集合。
     *
     * @param isVideoCall true=视频通话；false=语音通话
     * @return 通话所需权限数组
     */
    public static String[] getCallPermissions(boolean isVideoCall) {
        if (isVideoCall) {
            return new String[]{Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO};
        }
        return new String[]{Manifest.permission.RECORD_AUDIO};
    }

    /**
     * 判断权限是否全部授权。
     *
     * @param context     上下文
     * @param permissions 需检查权限列表
     * @return true=全部已授权
     */
    public static boolean hasAllPermissions(Context context, String... permissions) {
        if (context == null) {
            return false;
        }
        if (permissions == null || permissions.length == 0) {
            return true;
        }
        for (String permission : permissions) {
            if (ContextCompat.checkSelfPermission(context, permission) != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }

    /**
     * 发起权限申请。
     *
     * @param activity    Activity
     * @param requestCode 请求码
     * @param permissions 权限数组
     */
    public static void requestPermissions(Activity activity, int requestCode, String... permissions) {
        if (activity == null || permissions == null || permissions.length == 0) {
            return;
        }
        ActivityCompat.requestPermissions(activity, permissions, requestCode);
    }

    /**
     * 判断权限申请结果是否全部通过。
     *
     * @param grantResults 系统返回授权结果
     * @return true=全部授权
     */
    public static boolean areGrantResultsGranted(int[] grantResults) {
        if (grantResults == null || grantResults.length == 0) {
            return false;
        }
        for (int grantResult : grantResults) {
            if (grantResult != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }
}

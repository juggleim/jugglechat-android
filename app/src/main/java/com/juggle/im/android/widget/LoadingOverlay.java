package com.juggle.im.android.widget;

import android.app.Activity;
import android.os.Build;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

import com.juggle.im.android.R;

/**
 * 阻塞式加载遮罩。
 *
 * <p>用于没有按钮可以承载加载态的请求场景，例如从选人页返回后直接发起的转让群主、添加管理员。
 * 遮罩会拦截触摸，避免请求在途时重复触发或误操作。</p>
 */
public final class LoadingOverlay {

    private final ViewGroup parent;
    private final View overlayView;
    private boolean dismissed;

    private LoadingOverlay(@NonNull ViewGroup parent, @NonNull View overlayView) {
        this.parent = parent;
        this.overlayView = overlayView;
    }

    /**
     * 展示遮罩，文案取通用的"处理中"。
     *
     * @param activity 宿主页面
     * @return 遮罩实例；页面已销毁时返回 null
     */
    @Nullable
    public static LoadingOverlay show(@Nullable Activity activity) {
        return show(activity, R.string.common_processing);
    }

    /**
     * 展示遮罩并指定文案。
     *
     * @param activity 宿主页面
     * @param textRes  提示文案资源
     * @return 遮罩实例；页面已销毁时返回 null
     */
    @Nullable
    public static LoadingOverlay show(@Nullable Activity activity, @StringRes int textRes) {
        if (!isActivityAlive(activity)) {
            return null;
        }
        ViewGroup content = activity.findViewById(android.R.id.content);
        if (content == null) {
            return null;
        }
        View overlayView = LayoutInflater.from(activity)
                .inflate(R.layout.view_loading_overlay, content, false);
        TextView textView = overlayView.findViewById(R.id.tv_loading_overlay_text);
        CharSequence text = activity.getString(textRes);
        if (TextUtils.isEmpty(text)) {
            textView.setVisibility(View.GONE);
        } else {
            textView.setText(text);
        }
        content.addView(overlayView);
        return new LoadingOverlay(content, overlayView);
    }

    /**
     * 关闭遮罩，重复调用安全。
     */
    public void dismiss() {
        if (dismissed) {
            return;
        }
        dismissed = true;
        parent.removeView(overlayView);
    }

    /**
     * 关闭遮罩的空安全写法，便于对 {@link #show} 的可空返回值直接收尾。
     *
     * @param overlay 遮罩实例，可为 null
     */
    public static void dismiss(@Nullable LoadingOverlay overlay) {
        if (overlay != null) {
            overlay.dismiss();
        }
    }

    private static boolean isActivityAlive(@Nullable Activity activity) {
        if (activity == null || activity.isFinishing()) {
            return false;
        }
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN_MR1 || !activity.isDestroyed();
    }
}

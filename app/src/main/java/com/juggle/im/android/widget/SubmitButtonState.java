package com.juggle.im.android.widget;

import android.text.TextUtils;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

import com.juggle.im.android.R;

/**
 * 提交按钮的加载态封装。
 *
 * <p>统一处理"点击提交 → 按钮置灰并切换加载文案 → 请求结束恢复"的重复逻辑，
 * 同时用 {@link #begin()} 的返回值承担防重复提交的判断，调用方不必再自己维护布尔位。</p>
 */
public final class SubmitButtonState {

    /** 加载态下按钮的透明度 */
    private static final float LOADING_ALPHA = 0.6f;

    private final TextView button;
    private final View progressView;
    private final CharSequence loadingText;
    private CharSequence idleText;
    private boolean submitting;

    private SubmitButtonState(@NonNull TextView button,
            @Nullable View progressView,
            @NonNull CharSequence loadingText) {
        this.button = button;
        this.progressView = progressView;
        this.loadingText = loadingText;
        this.idleText = button.getText();
    }

    /**
     * 绑定提交按钮，加载文案取通用的"处理中"。
     *
     * @param button 提交按钮
     * @return 状态实例
     */
    public static SubmitButtonState bind(@NonNull TextView button) {
        return bind(button, R.string.common_processing);
    }

    /**
     * 绑定提交按钮并指定加载文案。
     *
     * @param button         提交按钮
     * @param loadingTextRes 加载态文案资源，如"保存中…"
     * @return 状态实例
     */
    public static SubmitButtonState bind(@NonNull TextView button, @StringRes int loadingTextRes) {
        return new SubmitButtonState(button, null, button.getResources().getString(loadingTextRes));
    }

    /**
     * 绑定提交按钮与独立的进度指示视图。
     *
     * @param button         提交按钮
     * @param progressView   加载时展示的进度视图，可为 null
     * @param loadingTextRes 加载态文案资源
     * @return 状态实例
     */
    public static SubmitButtonState bind(@NonNull TextView button,
            @Nullable View progressView,
            @StringRes int loadingTextRes) {
        return new SubmitButtonState(button, progressView,
                button.getResources().getString(loadingTextRes));
    }

    /**
     * 是否正在提交。
     *
     * @return true 表示上一次提交尚未结束
     */
    public boolean isSubmitting() {
        return submitting;
    }

    /**
     * 进入提交态。
     *
     * @return true 表示成功进入；false 表示已有请求在途，调用方应直接 return
     */
    public boolean begin() {
        if (submitting) {
            return false;
        }
        submitting = true;
        // TIPS: 每次都重取空闲文案，兼容按钮文案随业务状态变化（如"退出群聊/解散群聊"）的页面
        if (!TextUtils.isEmpty(button.getText()) && !TextUtils.equals(button.getText(), loadingText)) {
            idleText = button.getText();
        }
        button.setEnabled(false);
        button.setAlpha(LOADING_ALPHA);
        button.setText(loadingText);
        if (progressView != null) {
            progressView.setVisibility(View.VISIBLE);
        }
        return true;
    }

    /**
     * 结束提交态并恢复按钮。
     */
    public void end() {
        submitting = false;
        button.setEnabled(true);
        button.setAlpha(1f);
        button.setText(idleText);
        if (progressView != null) {
            progressView.setVisibility(View.GONE);
        }
    }
}

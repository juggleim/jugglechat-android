package com.juggle.im.android.widget;

import android.app.Activity;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Build;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;

import com.juggle.im.android.R;

/**
 * 统一确认弹窗组件，支持标题、内容、取消/确认按钮及点击回调。
 */
public final class AppConfirmDialog {

    private AppConfirmDialog() {
    }

    /**
     * 创建确认弹窗构建器。
     *
     * @param context 上下文
     * @return 构建器实例
     */
    public static Builder builder(@NonNull Context context) {
        return new Builder(context);
    }

    /**
     * 弹窗按钮点击回调。
     */
    @FunctionalInterface
    public interface OnActionClickListener {
        /**
         * 按钮点击事件。
         */
        void onClick();
    }

    /**
     * 确认弹窗参数构建器。
     */
    public static final class Builder {
        private final Context context;
        private CharSequence title;
        private CharSequence message;
        private CharSequence negativeText;
        private CharSequence positiveText;
        private boolean cancelable = true;
        private boolean canceledOnTouchOutside = true;
        private OnActionClickListener onPositiveClickListener;
        private OnActionClickListener onNegativeClickListener;

        private Builder(@NonNull Context context) {
            this.context = context;
        }

        /**
         * 设置弹窗标题。
         *
         * @param title 标题文案
         * @return 当前构建器
         */
        public Builder setTitle(@Nullable CharSequence title) {
            this.title = title;
            return this;
        }

        /**
         * 设置弹窗内容。
         *
         * @param message 内容文案，支持换行
         * @return 当前构建器
         */
        public Builder setMessage(@Nullable CharSequence message) {
            this.message = message;
            return this;
        }

        /**
         * 设置取消按钮文案。
         *
         * @param negativeText 取消按钮文案
         * @return 当前构建器
         */
        public Builder setNegativeText(@Nullable CharSequence negativeText) {
            this.negativeText = negativeText;
            return this;
        }

        /**
         * 设置确认按钮文案。
         *
         * @param positiveText 确认按钮文案
         * @return 当前构建器
         */
        public Builder setPositiveText(@Nullable CharSequence positiveText) {
            this.positiveText = positiveText;
            return this;
        }

        /**
         * 设置是否可通过返回键取消弹窗。
         *
         * @param cancelable 是否可取消
         * @return 当前构建器
         */
        public Builder setCancelable(boolean cancelable) {
            this.cancelable = cancelable;
            return this;
        }

        /**
         * 设置点击弹窗外区域是否关闭弹窗。
         *
         * @param canceledOnTouchOutside 是否可关闭
         * @return 当前构建器
         */
        public Builder setCanceledOnTouchOutside(boolean canceledOnTouchOutside) {
            this.canceledOnTouchOutside = canceledOnTouchOutside;
            return this;
        }

        /**
         * 设置确认按钮点击回调。
         *
         * @param listener 回调实例
         * @return 当前构建器
         */
        public Builder setOnPositiveClick(@Nullable OnActionClickListener listener) {
            this.onPositiveClickListener = listener;
            return this;
        }

        /**
         * 设置取消按钮点击回调。
         *
         * @param listener 回调实例
         * @return 当前构建器
         */
        public Builder setOnNegativeClick(@Nullable OnActionClickListener listener) {
            this.onNegativeClickListener = listener;
            return this;
        }

        /**
         * 展示弹窗。
         *
         * @return 弹窗实例；若当前上下文不可用则返回 null
         */
        @Nullable
        public AlertDialog show() {
            if (!isContextReady(context)) {
                return null;
            }
            View contentView = LayoutInflater.from(context).inflate(R.layout.dialog_app_confirm, null, false);
            TextView titleView = contentView.findViewById(R.id.tv_dialog_title);
            TextView messageView = contentView.findViewById(R.id.tv_dialog_message);
            TextView negativeButton = contentView.findViewById(R.id.btn_dialog_negative);
            TextView positiveButton = contentView.findViewById(R.id.btn_dialog_positive);

            bindText(titleView, title);
            bindText(messageView, message);
            negativeButton.setText(TextUtils.isEmpty(negativeText)
                    ? context.getString(R.string.txt_cancel)
                    : negativeText);
            positiveButton.setText(TextUtils.isEmpty(positiveText)
                    ? context.getString(R.string.create_group_confirm)
                    : positiveText);

            AlertDialog dialog = new AlertDialog.Builder(context)
                    .setView(contentView)
                    .create();
            dialog.setCancelable(cancelable);
            dialog.setCanceledOnTouchOutside(canceledOnTouchOutside);
            dialog.show();

            // 简要描述：去掉系统对话框默认白底和边距，确保视觉完全使用自定义样式。
            Window window = dialog.getWindow();
            if (window != null) {
                window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
                WindowManager.LayoutParams params = window.getAttributes();
                params.dimAmount = 0.58f;
                window.setAttributes(params);
            }

            negativeButton.setOnClickListener(v -> {
                dialog.dismiss();
                if (onNegativeClickListener != null) {
                    onNegativeClickListener.onClick();
                }
            });
            positiveButton.setOnClickListener(v -> {
                dialog.dismiss();
                if (onPositiveClickListener != null) {
                    onPositiveClickListener.onClick();
                }
            });
            return dialog;
        }

        private void bindText(@NonNull TextView textView, @Nullable CharSequence content) {
            if (TextUtils.isEmpty(content)) {
                textView.setVisibility(View.GONE);
                return;
            }
            textView.setVisibility(View.VISIBLE);
            textView.setText(content);
        }

        private static boolean isContextReady(@NonNull Context context) {
            if (!(context instanceof Activity)) {
                return true;
            }
            Activity activity = (Activity) context;
            // 简要描述：避免 Activity 生命周期结束后弹窗 show 导致 BadTokenException。
            if (activity.isFinishing()) {
                return false;
            }
            return Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN_MR1 || !activity.isDestroyed();
        }
    }
}

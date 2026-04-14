package com.juggle.im.android.widget;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Build;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.juggle.im.android.R;

import java.util.ArrayList;

/**
 * 底部选项弹窗（ActionSheet），从底部弹出，支持标题 + 多个选项按钮 + 取消按钮。
 * 适用于删除选择、操作选择等场景。
 *
 * <p>使用示例：</p>
 * <pre>
 * BottomActionSheet.builder(context)
 *     .setTitle("选择删除方式")
 *     .addItem("仅删除自己", () -> { ... })
 *     .addItem("删除对方和自己", "#FF3B30", () -> { ... })
 *     .show();
 * </pre>
 */
public final class BottomActionSheet {

    private BottomActionSheet() {
    }

    /**
     * 创建底部选项弹窗构建器。
     *
     * @param context 上下文
     * @return 构建器实例
     */
    public static Builder builder(@NonNull Context context) {
        return new Builder(context);
    }

    /**
     * 选项条目
     */
    public static final class ActionItem {
        private final CharSequence text;
        @ColorInt
        private final int textColor;
        private final OnActionClickListener listener;

        /**
         * @param text      选项文案
         * @param textColor 文字颜色
         * @param listener  点击回调
         */
        public ActionItem(@NonNull CharSequence text, @ColorInt int textColor, @Nullable OnActionClickListener listener) {
            this.text = text;
            this.textColor = textColor;
            this.listener = listener;
        }
    }

    /**
     * 按钮点击回调。
     */
    @FunctionalInterface
    public interface OnActionClickListener {
        /**
         * 按钮点击事件。
         */
        void onClick();
    }

    /**
     * 底部选项弹窗构建器。
     */
    public static final class Builder {
        private final Context context;
        private CharSequence title;
        private final ArrayList<ActionItem> items = new ArrayList<>();
        private boolean cancelable = true;

        private Builder(@NonNull Context context) {
            this.context = context;
        }

        /**
         * 设置弹窗标题（可选，不设置则不显示）。
         *
         * @param title 标题文案
         * @return 当前构建器
         */
        public Builder setTitle(@Nullable CharSequence title) {
            this.title = title;
            return this;
        }

        /**
         * 添加一个选项（默认文字颜色）。
         *
         * @param text     选项文案
         * @param listener 点击回调
         * @return 当前构建器
         */
        public Builder addItem(@NonNull CharSequence text, @Nullable OnActionClickListener listener) {
            items.add(new ActionItem(text, 0xFF141414, listener));
            return this;
        }

        /**
         * 添加一个选项（自定义文字颜色，可用于警示操作如删除）。
         *
         * @param text      选项文案
         * @param colorHex  文字颜色，如 "#FF3B30"
         * @param listener  点击回调
         * @return 当前构建器
         */
        public Builder addItem(@NonNull CharSequence text, @NonNull String colorHex, @Nullable OnActionClickListener listener) {
            int color;
            try {
                color = Color.parseColor(colorHex);
            } catch (Exception e) {
                color = 0xFF141414;
            }
            items.add(new ActionItem(text, color, listener));
            return this;
        }

        /**
         * 设置是否可通过返回键或点击外部取消弹窗。
         *
         * @param cancelable 是否可取消
         * @return 当前构建器
         */
        public Builder setCancelable(boolean cancelable) {
            this.cancelable = cancelable;
            return this;
        }

        /**
         * 展示弹窗。
         *
         * @return 弹窗 Dialog 实例；若上下文不可用则返回 null
         */
        @Nullable
        public Dialog show() {
            if (!isContextReady(context)) {
                return null;
            }

            View contentView = LayoutInflater.from(context).inflate(R.layout.dialog_bottom_action_sheet, null, false);
            TextView tvTitle = contentView.findViewById(R.id.tv_sheet_title);
            LinearLayout layoutActions = contentView.findViewById(R.id.layout_actions);
            TextView btnCancel = contentView.findViewById(R.id.btn_sheet_cancel);

            if (!TextUtils.isEmpty(title)) {
                tvTitle.setVisibility(View.VISIBLE);
                tvTitle.setText(title);
            } else {
                tvTitle.setVisibility(View.GONE);
            }

            Dialog dialog = new Dialog(context, R.style.BottomActionSheetDialog);
            dialog.setContentView(contentView);
            dialog.setCancelable(cancelable);
            dialog.setCanceledOnTouchOutside(cancelable);

            Window window = dialog.getWindow();
            if (window != null) {
                window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
                window.setDimAmount(0.5f);
                WindowManager.LayoutParams params = window.getAttributes();
                params.width = WindowManager.LayoutParams.MATCH_PARENT;
                params.height = WindowManager.LayoutParams.WRAP_CONTENT;
                params.gravity = Gravity.BOTTOM;
                window.setAttributes(params);
            }

            final Dialog dialogRef = dialog;
            int itemCount = items.size();
            for (int i = 0; i < itemCount; i++) {
                ActionItem item = items.get(i);
                TextView actionBtn = new TextView(context);
                actionBtn.setText(item.text);
                actionBtn.setTextColor(item.textColor);
                actionBtn.setTextSize(16);
                actionBtn.setGravity(Gravity.CENTER);
                actionBtn.setPadding(0, dp(16), 0, dp(16));

                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                );
                actionBtn.setLayoutParams(lp);
                actionBtn.setClickable(true);
                actionBtn.setFocusable(true);

                // tips: 非最后一项添加分割线
                if (i < itemCount - 1) {
                    actionBtn.setBackgroundResource(android.R.color.transparent);
                }

                final OnActionClickListener clickListener = item.listener;
                actionBtn.setOnClickListener(v -> {
                    dialogRef.dismiss();
                    if (clickListener != null) {
                        clickListener.onClick();
                    }
                });

                layoutActions.addView(actionBtn);

                // 分割线
                if (i < itemCount - 1) {
                    View divider = new View(context);
                    LinearLayout.LayoutParams dividerLp = new LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT, 1
                    );
                    dividerLp.leftMargin = dp(16);
                    dividerLp.rightMargin = dp(16);
                    divider.setLayoutParams(dividerLp);
                    divider.setBackgroundColor(0xFFE5E8ED);
                    layoutActions.addView(divider);
                }
            }

            btnCancel.setOnClickListener(v -> dialogRef.dismiss());

            dialog.show();
            return dialog;
        }

        private static boolean isContextReady(@NonNull Context context) {
            if (!(context instanceof Activity)) {
                return true;
            }
            Activity activity = (Activity) context;
            if (activity.isFinishing()) {
                return false;
            }
            return Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN_MR1 || !activity.isDestroyed();
        }

        private int dp(int value) {
            return (int) (context.getResources().getDisplayMetrics().density * value + 0.5f);
        }
    }
}

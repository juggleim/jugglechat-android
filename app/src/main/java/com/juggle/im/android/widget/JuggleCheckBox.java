package com.juggle.im.android.widget;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.widget.Checkable;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.juggle.im.android.R;

/**
 * 通用 Checkbox 组件，支持 checked、unchecked、disabled 三种状态。
 */
public class JuggleCheckBox extends View implements Checkable {

    private static final int[] CHECKED_STATE_SET = {android.R.attr.state_checked};
    private static final int[] DISABLED_STATE_SET = {-android.R.attr.state_enabled};

    private boolean checked = false;
    private boolean disabled = false;
    private OnCheckedChangeListener onCheckedChangeListener;

    public JuggleCheckBox(@NonNull Context context) {
        this(context, null);
    }

    public JuggleCheckBox(@NonNull Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public JuggleCheckBox(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context, attrs);
    }

    private void init(Context context, AttributeSet attrs) {
        if (attrs != null) {
            android.content.res.TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.JuggleCheckBox);
            checked = a.getBoolean(R.styleable.JuggleCheckBox_checked, false);
            disabled = a.getBoolean(R.styleable.JuggleCheckBox_disabled, false);
            a.recycle();
        }
        updateDrawable();
        setClickable(true);
        setFocusable(true);
    }

    @Override
    public boolean performClick() {
        if (disabled) {
            return false;
        }
        toggle();
        return super.performClick();
    }

    @Override
    public void setChecked(boolean checked) {
        if (this.checked != checked) {
            this.checked = checked;
            updateDrawable();
            if (onCheckedChangeListener != null) {
                onCheckedChangeListener.onCheckedChanged(this, checked);
            }
        }
    }

    @Override
    public boolean isChecked() {
        return checked;
    }

    @Override
    public void toggle() {
        setChecked(!checked);
    }

    /**
     * 设置禁用状态
     */
    public void setDisabled(boolean disabled) {
        if (this.disabled != disabled) {
            this.disabled = disabled;
            updateDrawable();
        }
    }

    /**
     * 获取禁用状态
     */
    public boolean isDisabled() {
        return disabled;
    }

    /**
     * 设置选中状态监听器
     */
    public void setOnCheckedChangeListener(OnCheckedChangeListener listener) {
        this.onCheckedChangeListener = listener;
    }

    private void updateDrawable() {
        if (disabled) {
            setImageResource(R.drawable.icon_checkbox_disable);
        } else if (checked) {
            setImageResource(R.drawable.icon_checkbox_checked);
        } else {
            setImageResource(R.drawable.icon_checkbox_uncheck);
        }
    }

    private void setImageResource(int resId) {
        setBackgroundResource(resId);
    }

    @Override
    protected int[] onCreateDrawableState(int extraSpace) {
        int[] drawableState = super.onCreateDrawableState(extraSpace + 2);
        if (checked) {
            mergeDrawableStates(drawableState, CHECKED_STATE_SET);
        }
        if (disabled) {
            mergeDrawableStates(drawableState, DISABLED_STATE_SET);
        }
        return drawableState;
    }

    /**
     * 选中状态变化监听接口
     */
    public interface OnCheckedChangeListener {
        void onCheckedChanged(JuggleCheckBox checkBox, boolean isChecked);
    }
}
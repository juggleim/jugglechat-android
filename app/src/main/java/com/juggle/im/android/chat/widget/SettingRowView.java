package com.juggle.im.android.chat.widget;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Color;
import android.util.AttributeSet;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.juggle.im.android.R;
import com.juggle.im.android.widget.JuggleSwitch;

/**
 * 设置行组件，支持多种右侧模式：
 * - ARROW: 显示右箭头（默认）
 * - SWITCH: 显示开关
 * - NONE: 不显示右侧内容
 *
 * 支持 enable/disable 状态
 */
public class SettingRowView extends FrameLayout {

    public static final int MODE_ARROW = 0;
    public static final int MODE_SWITCH = 1;
    public static final int MODE_NONE = 2;

    private LinearLayout containerLayout;
    private View rootView;
    private ImageView iconView;
    private TextView titleView;
    private TextView subtitleView;
    private JuggleSwitch switchView;
    private ImageView arrowView;
    private View dividerView;

    private int currentMode = MODE_ARROW;
    private boolean isEnabled = true;
    private OnSwitchCheckedChangeListener switchListener;

    public SettingRowView(@NonNull Context context) {
        super(context);
        init(context, null);
    }

    public SettingRowView(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init(context, attrs);
    }

    public SettingRowView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context, attrs);
    }

    private void init(Context context, AttributeSet attrs) {
        rootView = inflate(context, R.layout.view_setting_row, this);
        containerLayout = rootView.findViewById(R.id.row_content);
        iconView = rootView.findViewById(R.id.iv_row_icon);
        titleView = rootView.findViewById(R.id.tv_row_title);
        subtitleView = rootView.findViewById(R.id.tv_row_subtitle);
        switchView = rootView.findViewById(R.id.switch_row);
        arrowView = rootView.findViewById(R.id.iv_row_arrow);
        dividerView = rootView.findViewById(R.id.row_divider);

        // 读取自定义属性
        if (attrs != null) {
            TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.SettingRowView);
            String title = a.getString(R.styleable.SettingRowView_srv_title);
            String subtitle = a.getString(R.styleable.SettingRowView_srv_subtitle);
            int iconRes = a.getResourceId(R.styleable.SettingRowView_srv_icon, 0);
            int mode = a.getInt(R.styleable.SettingRowView_srv_mode, MODE_ARROW);
            boolean showDivider = a.getBoolean(R.styleable.SettingRowView_srv_showDivider, true);
            boolean enabled = a.getBoolean(R.styleable.SettingRowView_srv_enabled, true);

            a.recycle();

            if (title != null) {
                titleView.setText(title);
            }
            if (subtitle != null) {
                subtitleView.setText(subtitle);
                subtitleView.setVisibility(VISIBLE);
            }
            if (iconRes != 0) {
                iconView.setImageResource(iconRes);
                iconView.setVisibility(VISIBLE);
            } else {
                iconView.setVisibility(GONE);
            }
            setMode(mode);
            setDividerVisible(showDivider);
            setRowEnabled(enabled);
        }

        // 开关监听
        switchView.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (switchListener != null && isEnabled) {
                switchListener.onCheckedChanged(SettingRowView.this, isChecked);
            }
        });
    }

    /**
     * 设置图标
     */
    public void setIcon(@DrawableRes int iconRes) {
        iconView.setImageResource(iconRes);
    }

    /**
     * 设置标题
     */
    public void setTitle(CharSequence title) {
        titleView.setText(title);
    }

    /**
     * 设置副标题
     */
    public void setSubtitle(CharSequence subtitle) {
        subtitleView.setText(subtitle);
        subtitleView.setVisibility(VISIBLE);
    }

    /**
     * 隐藏副标题
     */
    public void hideSubtitle() {
        subtitleView.setVisibility(GONE);
    }

    /**
     * 设置右侧模式
     * @param mode MODE_ARROW, MODE_SWITCH, MODE_NONE
     */
    public void setMode(int mode) {
        currentMode = mode;
        switch (mode) {
            case MODE_SWITCH:
                switchView.setVisibility(VISIBLE);
                arrowView.setVisibility(GONE);
                break;
            case MODE_NONE:
                switchView.setVisibility(GONE);
                arrowView.setVisibility(GONE);
                break;
            case MODE_ARROW:
            default:
                switchView.setVisibility(GONE);
                arrowView.setVisibility(VISIBLE);
                break;
        }
    }

    /**
     * 获取当前模式
     */
    public int getMode() {
        return currentMode;
    }

    /**
     * 设置开关状态（仅 MODE_SWITCH 模式有效）
     */
    public void setSwitchChecked(boolean checked) {
        switchView.setChecked(checked);
    }

    /**
     * 获取开关状态
     */
    public boolean isSwitchChecked() {
        return switchView.isChecked();
    }

    /**
     * 设置开关监听器
     */
    public void setOnSwitchCheckedChangeListener(OnSwitchCheckedChangeListener listener) {
        this.switchListener = listener;
    }

    /**
     * 设置分割线是否可见
     */
    public void setDividerVisible(boolean visible) {
        dividerView.setVisibility(visible ? VISIBLE : GONE);
    }

    /**
     * 设置整行是否可用
     */
    public void setRowEnabled(boolean enabled) {
        isEnabled = enabled;
        rootView.setAlpha(enabled ? 1f : 0.4f);
        rootView.setEnabled(enabled);
        switchView.setEnabled(enabled);
        arrowView.setAlpha(enabled ? 1f : 0.4f);
    }

    /**
     * 获取整行是否可用
     */
    public boolean isRowEnabled() {
        return isEnabled;
    }

    /**
     * 设置点击监听
     */
    public void setOnRowClickListener(OnClickListener listener) {
        rootView.setOnClickListener(listener);
    }

    /**
     * 获取 Switch 控件（用于需要更精细控制的场景）
     */
    public JuggleSwitch getSwitchView() {
        return switchView;
    }

    /**
     * 开关状态变化监听接口
     */
    public interface OnSwitchCheckedChangeListener {
        void onCheckedChanged(SettingRowView view, boolean isChecked);
    }
}
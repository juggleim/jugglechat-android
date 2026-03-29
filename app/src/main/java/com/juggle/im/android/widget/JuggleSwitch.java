package com.juggle.im.android.widget;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.content.res.TypedArray;
import android.util.AttributeSet;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.juggle.im.android.R;

/**
 * 通用的 Switch 组件
 * 支持开、关、禁用三种状态
 * 尺寸：Track 50dp x 28dp，Thumb 24dp x 24dp
 */
public class JuggleSwitch extends FrameLayout {

    private static final int ANIMATION_DURATION = 200;
    private static final int TRACK_WIDTH_DP = 50;
    private static final int TRACK_HEIGHT_DP = 28;
    private static final int THUMB_SIZE_DP = 24;
    private static final int THUMB_MARGIN_DP = 2;

    private ImageView trackView;
    private ImageView thumbView;

    private boolean isChecked = false;
    private boolean isEnabled = true;
    private boolean isAnimating = false;

    private OnCheckedChangeListener listener;

    private int thumbStartX;
    private int thumbEndX;

    public JuggleSwitch(@NonNull Context context) {
        super(context);
        init(context, null);
    }

    public JuggleSwitch(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init(context, attrs);
    }

    public JuggleSwitch(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context, attrs);
    }

    private void init(Context context, AttributeSet attrs) {
        // 读取自定义属性
        if (attrs != null) {
            TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.JuggleSwitch);
            isChecked = a.getBoolean(R.styleable.JuggleSwitch_js_checked, false);
            isEnabled = a.getBoolean(R.styleable.JuggleSwitch_js_enabled, true);
            a.recycle();
        }

        // 计算尺寸
        float density = context.getResources().getDisplayMetrics().density;
        int trackWidth = (int) (TRACK_WIDTH_DP * density);
        int trackHeight = (int) (TRACK_HEIGHT_DP * density);
        int thumbSize = (int) (THUMB_SIZE_DP * density);
        int thumbMargin = (int) (THUMB_MARGIN_DP * density);

        // 设置自身尺寸
        ViewGroup.LayoutParams lp = getLayoutParams();
        if (lp == null) {
            lp = new ViewGroup.LayoutParams(trackWidth, trackHeight);
        } else {
            lp.width = trackWidth;
            lp.height = trackHeight;
        }
        setLayoutParams(lp);

        // 创建 Track
        trackView = new ImageView(context);
        trackView.setId(R.id.switch_track);
        FrameLayout.LayoutParams trackLp = new FrameLayout.LayoutParams(
                trackWidth, trackHeight
        );
        trackLp.gravity = android.view.Gravity.CENTER;
        trackView.setLayoutParams(trackLp);
        addView(trackView);

        // 创建 Thumb
        thumbView = new ImageView(context);
        thumbView.setId(R.id.switch_thumb);
        FrameLayout.LayoutParams thumbLp = new FrameLayout.LayoutParams(
                thumbSize, thumbSize
        );
        thumbLp.gravity = android.view.Gravity.CENTER_VERTICAL | android.view.Gravity.START;
        thumbLp.leftMargin = thumbMargin;
        thumbLp.rightMargin = thumbMargin;
        thumbView.setLayoutParams(thumbLp);
        addView(thumbView);

        // 计算 Thumb 的起始和结束位置
        thumbStartX = thumbMargin;
        thumbEndX = trackWidth - thumbSize - thumbMargin;

        // 设置初始状态
        setEnabled(isEnabled);
        setChecked(isChecked, false);

        // 点击事件
        setOnClickListener(v -> {
            if (isEnabled && !isAnimating) {
                toggle();
            }
        });
    }

    /**
     * 切换状态
     */
    public void toggle() {
        setChecked(!isChecked, true);
    }

    /**
     * 设置选中状态
     * @param checked 是否选中
     * @param animate 是否动画
     */
    public void setChecked(boolean checked, boolean animate) {
        if (this.isChecked == checked) {
            return;
        }
        this.isChecked = checked;

        // 更新 Track 状态
        updateTrackState();

        // 更新 Thumb 位置
        if (animate) {
            animateThumb(checked);
        } else {
            setThumbPosition(checked);
        }

        // 回调监听器
        if (listener != null) {
            listener.onCheckedChanged(this, checked);
        }
    }

    /**
     * 设置选中状态（无动画）
     */
    public void setChecked(boolean checked) {
        setChecked(checked, false);
    }

    /**
     * 获取选中状态
     */
    public boolean isChecked() {
        return isChecked;
    }

    /**
     * 设置是否可用
     */
    @Override
    public void setEnabled(boolean enabled) {
        this.isEnabled = enabled;
        super.setEnabled(enabled);
        updateTrackState();
        updateThumbState();
    }

    /**
     * 获取是否可用
     */
    @Override
    public boolean isEnabled() {
        return isEnabled;
    }

    /**
     * 设置状态变化监听器
     */
    public void setOnCheckedChangeListener(OnCheckedChangeListener listener) {
        this.listener = listener;
    }

    /**
     * 更新 Track 状态
     */
    private void updateTrackState() {
        if (trackView == null) return;

        if (!isEnabled) {
            trackView.setImageResource(R.drawable.juggle_switch_track_disabled);
        } else if (isChecked) {
            trackView.setImageResource(R.drawable.juggle_switch_track_on);
        } else {
            trackView.setImageResource(R.drawable.juggle_switch_track_off);
        }
    }

    /**
     * 更新 Thumb 状态
     */
    private void updateThumbState() {
        if (thumbView == null) return;

        if (!isEnabled) {
            thumbView.setImageResource(R.drawable.juggle_switch_thumb_disabled);
        } else {
            thumbView.setImageResource(R.drawable.juggle_switch_thumb);
        }
    }

    /**
     * 设置 Thumb 位置
     */
    private void setThumbPosition(boolean checked) {
        FrameLayout.LayoutParams thumbLp = (FrameLayout.LayoutParams) thumbView.getLayoutParams();
        thumbLp.leftMargin = checked ? thumbEndX : thumbStartX;
        thumbView.setLayoutParams(thumbLp);
    }

    /**
     * 动画移动 Thumb
     */
    private void animateThumb(boolean checked) {
        isAnimating = true;

        int startX = checked ? thumbStartX : thumbEndX;
        int endX = checked ? thumbEndX : thumbStartX;

        ValueAnimator animator = ValueAnimator.ofInt(startX, endX);
        animator.setDuration(ANIMATION_DURATION);
        animator.addUpdateListener(animation -> {
            int margin = (int) animation.getAnimatedValue();
            FrameLayout.LayoutParams thumbLp = (FrameLayout.LayoutParams) thumbView.getLayoutParams();
            thumbLp.leftMargin = margin;
            thumbView.setLayoutParams(thumbLp);
        });
        animator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                isAnimating = false;
            }
        });
        animator.start();
    }

    /**
     * 状态变化监听接口
     */
    public interface OnCheckedChangeListener {
        void onCheckedChanged(JuggleSwitch view, boolean isChecked);
    }
}
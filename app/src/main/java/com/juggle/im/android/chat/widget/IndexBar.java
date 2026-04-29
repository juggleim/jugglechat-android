package com.juggle.im.android.chat.widget;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

import androidx.core.content.ContextCompat;

import com.juggle.im.android.R;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class IndexBar extends View {
    private static final List<String> DEFAULT_LETTERS = Arrays.asList(
            "A", "B", "C", "D", "E", "F", "G", "H",
            "I", "J", "K", "L", "M", "N", "O", "P",
            "Q", "R", "S", "T", "U", "V", "W", "X",
            "Y", "Z", "#");

    private int selectedIndex = -1;
    private OnIndexSelectedListener listener;
    private Paint paint;
    private final List<String> letters = new ArrayList<>(DEFAULT_LETTERS);
    private float contentTop = 0f;
    private float itemHeight = 0f;

    public IndexBar(Context context) {
        this(context, null);
    }

    public IndexBar(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public IndexBar(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setTextSize(spToPx(12));
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setColor(ContextCompat.getColor(getContext(), R.color.group_text_secondary));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (letters.isEmpty()) {
            return;
        }

        IndexLayout layout = buildIndexLayout();
        contentTop = layout.contentTop;
        itemHeight = layout.itemHeight;

        for (int i = 0; i < letters.size(); i++) {
            float x = getWidth() / 2f;
            float y = contentTop + (i + 0.5f) * itemHeight
                    + (paint.descent() - paint.ascent()) / 2f - paint.descent();

            paint.setColor(i == selectedIndex ?
                    ContextCompat.getColor(getContext(), R.color.group_primary) :
                    ContextCompat.getColor(getContext(), R.color.group_text_secondary));

            canvas.drawText(letters.get(i), x, y, paint);
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (letters.isEmpty()) {
            return super.onTouchEvent(event);
        }
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
            case MotionEvent.ACTION_MOVE:
                IndexLayout layout = buildIndexLayout();
                contentTop = layout.contentTop;
                itemHeight = layout.itemHeight;

                if (itemHeight <= 0f) {
                    return true;
                }

                float relativeY = event.getY() - contentTop;
                int index = (int) Math.floor(relativeY / itemHeight);
                index = Math.max(0, Math.min(index, letters.size() - 1));
                if (index != selectedIndex) {
                    selectedIndex = index;
                    if (listener != null) {
                        listener.onIndexSelected(letters.get(selectedIndex));
                    }
                    invalidate();
                }
                return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                selectedIndex = -1;
                invalidate();
                return true;
        }
        return super.onTouchEvent(event);
    }

    public void setOnIndexSelectedListener(OnIndexSelectedListener listener) {
        this.listener = listener;
    }

    public void setLetters(List<String> dynamicLetters) {
        letters.clear();
        if (dynamicLetters != null) {
            for (String letter : dynamicLetters) {
                if (!TextUtils.isEmpty(letter)) {
                    letters.add(letter);
                }
            }
        }
        if (letters.isEmpty()) {
            letters.addAll(DEFAULT_LETTERS);
        }
        selectedIndex = -1;
        invalidate();
    }

    public void setActiveLetter(String letter) {
        if (TextUtils.isEmpty(letter)) {
            selectedIndex = -1;
            invalidate();
            return;
        }
        int index = letters.indexOf(letter);
        selectedIndex = index;
        invalidate();
    }

    public interface OnIndexSelectedListener {
        void onIndexSelected(String letter);
    }

    private float spToPx(float sp) {
        return sp * getResources().getDisplayMetrics().scaledDensity;
    }

    /**
     * 计算索引字母在当前 View 内的垂直布局。
     *
     * <p>简要描述：优先使用“文字高度 + 固定间距”形成紧凑字母块，并在可用高度内垂直居中；若空间不足则按可用高度均分，保证可见与可点。</p>
     *
     * @return 字母布局信息
     */
    private IndexLayout buildIndexLayout() {
        if (letters.isEmpty()) {
            return new IndexLayout(0f, 0f);
        }
        float availableHeight = Math.max(0f, getHeight() - getPaddingTop() - getPaddingBottom());
        if (availableHeight <= 0f) {
            return new IndexLayout(getPaddingTop(), 0f);
        }
        float textHeight = paint.descent() - paint.ascent();
        float targetItemHeight = textHeight + dpToPx(6f);
        float totalTargetHeight = targetItemHeight * letters.size();
        if (totalTargetHeight <= availableHeight) {
            float top = getPaddingTop() + (availableHeight - totalTargetHeight) / 2f;
            return new IndexLayout(top, targetItemHeight);
        }
        float compactItemHeight = availableHeight / letters.size();
        return new IndexLayout(getPaddingTop(), compactItemHeight);
    }

    /**
     * dp 转 px。
     *
     * @param dp dp 值
     * @return 像素值
     */
    private float dpToPx(float dp) {
        return dp * getResources().getDisplayMetrics().density;
    }

    /**
     * 索引字母布局结果。
     */
    private static final class IndexLayout {
        final float contentTop;
        final float itemHeight;

        IndexLayout(float contentTop, float itemHeight) {
            this.contentTop = contentTop;
            this.itemHeight = itemHeight;
        }
    }
}

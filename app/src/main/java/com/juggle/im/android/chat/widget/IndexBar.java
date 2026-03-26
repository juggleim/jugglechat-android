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
        int itemHeight = getHeight() / letters.size();
        for (int i = 0; i < letters.size(); i++) {
            float x = getWidth() / 2f;
            float y = (i + 1) * itemHeight - itemHeight / 2f +
                    (paint.descent() - paint.ascent()) / 2f - paint.descent();

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
                int index = (int) (event.getY() / getHeight() * letters.size());
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
}

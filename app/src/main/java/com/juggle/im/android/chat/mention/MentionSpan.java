package com.juggle.im.android.chat.mention;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.text.style.ReplacementSpan;

import androidx.annotation.NonNull;

public class MentionSpan extends ReplacementSpan {

    public final String userId;
    public final String displayName;

    private int padding = 20;
    private float radius = 30f;

    public MentionSpan(String userId, String displayName) {
        this.userId = userId;
        this.displayName = displayName;
    }

    @Override
    public int getSize(@NonNull Paint paint, CharSequence text,
                       int start, int end, Paint.FontMetricsInt fm) {
        // 使用实际文本范围的字符串作为宽度参考，例如 "@张三"
        return (int) paint.measureText(text.subSequence(start, end).toString());
    }

    @Override
    public void draw(@NonNull Canvas canvas, CharSequence text,
                     int start, int end, float x, int top, int y, int bottom,
                     @NonNull Paint paint) {

        String label = text.subSequence(start, end).toString();  // "@张三"
        paint.setColor(Color.BLACK);
        canvas.drawText(label, x, y, paint);
    }

}

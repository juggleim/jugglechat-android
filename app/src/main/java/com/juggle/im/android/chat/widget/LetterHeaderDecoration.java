package com.juggle.im.android.chat.widget;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.Map;

public class LetterHeaderDecoration extends RecyclerView.ItemDecoration {
    private final Map<Integer, String> headers;
    private final int headerHeight;
    private final Paint paint;

    public LetterHeaderDecoration(Context context, Map<Integer, String> headers) {
        this.headers = headers;
        this.headerHeight = dpToPx(context, 32);
        this.paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setTextSize(spToPx(context, 14));
        paint.setColor(Color.parseColor("#858585"));
    }

    @Override
    public void onDrawOver(@NonNull Canvas c, @NonNull RecyclerView parent,
                          @NonNull RecyclerView.State state) {
        super.onDrawOver(c, parent, state);
        for (Map.Entry<Integer, String> entry : headers.entrySet()) {
            int position = entry.getKey();
            String letter = entry.getValue();
            View child = parent.getChildAt(position);
            if (child == null) return;

            int top = child.getTop() - headerHeight;
            c.drawText(letter, dpToPx(parent.getContext(), 16),
                    top + headerHeight / 2f + paint.getTextSize() / 2, paint);
        }
    }

    @Override
    public void getItemOffsets(@NonNull Rect outRect, @NonNull View view,
                              @NonNull RecyclerView parent,
                              @NonNull RecyclerView.State state) {
        int position = parent.getChildAdapterPosition(view);
        if (headers.containsKey(position)) {
            outRect.top = headerHeight;
        }
    }

    private int dpToPx(Context context, int dp) {
        return (int) (dp * context.getResources().getDisplayMetrics().density);
    }

    private float spToPx(Context context, float sp) {
        return sp * context.getResources().getDisplayMetrics().scaledDensity;
    }
}

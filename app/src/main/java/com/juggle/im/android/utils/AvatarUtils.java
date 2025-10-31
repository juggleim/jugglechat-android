package com.juggle.im.android.utils;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.text.TextUtils;
import android.util.TypedValue;
import android.widget.ImageView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.resource.bitmap.CircleCrop;
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions;
import com.juggle.im.android.R;

/**
 * Simple avatar helper: loads avatar from url if present, otherwise generates a circular bitmap
 * with the initial letter and a gradient background deterministically derived from the letter.
 */
public final class AvatarUtils {
    private AvatarUtils() {
    }

    public static void loadAvatar(ImageView iv, String url, String name) {
        if (iv == null) return;
        Context ctx = iv.getContext();
        if (!TextUtils.isEmpty(url)) {
            Glide.with(iv)
                    .load(url)
                    .centerCrop()
                    .placeholder(R.drawable.ic_avatar_placeholder)
                    .transform(new CircleCrop())
                    .transition(DrawableTransitionOptions.withCrossFade())
                    .into(iv);
            return;
        }

        // generate placeholder bitmap with initial
        String initial = extractInitial(name);
        int sizePx = dpToPx(ctx, 40); // reasonable default avatar size
        Bitmap bmp = createInitialsBitmap(sizePx, initial);
        Glide.with(iv).load(bmp).circleCrop().placeholder(R.drawable.ic_avatar_placeholder).into(iv);
    }

    public static void loadImage(ImageView iv, String url) {
        Glide.with(iv)
                .load(url)
                .centerCrop()
                .placeholder(R.drawable.default_image)
                .transition(DrawableTransitionOptions.withCrossFade())
                .into(iv);
    }

    private static String extractInitial(String name) {
        if (TextUtils.isEmpty(name)) return "";
        name = name.trim();
        if (name.length() == 0) return "";
        // use first code point
        int cp = name.codePointAt(0);
        return new String(Character.toChars(cp)).toUpperCase();
    }

    private static Bitmap createInitialsBitmap(int sizePx, String initial) {
        if (sizePx <= 0) sizePx = 64;
        Bitmap bmp = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bmp);

        // background gradient based on initial hash
        int[] colors = colorsForString(initial);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        Shader shader = new LinearGradient(0, 0, sizePx, sizePx, colors[0], colors[1], Shader.TileMode.CLAMP);
        paint.setShader(shader);
        RectF r = new RectF(0, 0, sizePx, sizePx);
        c.drawRoundRect(r, sizePx / 2f, sizePx / 2f, paint);

        if (!initial.isEmpty()) {
            Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            textPaint.setColor(0xFFFFFFFF);
            // center text
            textPaint.setTextSize(sizePx * 0.45f);
            textPaint.setTextAlign(Paint.Align.CENTER);
            Paint.FontMetrics fm = textPaint.getFontMetrics();
            float x = sizePx / 2f;
            float y = sizePx / 2f - (fm.ascent + fm.descent) / 2f;
            c.drawText(initial, x, y, textPaint);
        }

        return bmp;
    }

    private static int[] colorsForString(String s) {
        if (s == null || s.isEmpty()) {
            return new int[]{0xFF888888, 0xFFBBBBBB};
        }
        int h = s.hashCode();
        // derive two colors from hash
        int r1 = 80 + (Math.abs(h) % 120);
        int g1 = 80 + (Math.abs(h / 31) % 120);
        int b1 = 80 + (Math.abs(h / 17) % 120);

        int r2 = 120 + (Math.abs(h / 13) % 120);
        int g2 = 120 + (Math.abs(h / 7) % 120);
        int b2 = 120 + (Math.abs(h / 3) % 120);

        int c1 = 0xFF000000 | ((r1 & 0xFF) << 16) | ((g1 & 0xFF) << 8) | (b1 & 0xFF);
        int c2 = 0xFF000000 | ((r2 & 0xFF) << 16) | ((g2 & 0xFF) << 8) | (b2 & 0xFF);
        return new int[]{c1, c2};
    }

    private static int dpToPx(Context ctx, int dp) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp, ctx.getResources().getDisplayMetrics());
    }
}

package com.juggle.im.android.utils;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.text.TextUtils;
import android.util.TypedValue;
import android.widget.ImageView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.resource.bitmap.CircleCrop;
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions;
import com.bumptech.glide.request.RequestOptions;
import com.juggle.im.android.R;

/**
 * Avatar helper:
 * 1) url 有效时加载网络头像
 * 2) url 为空时，按 userId 首字符 ASCII % 6 选定固定底色生成默认头像
 */
public final class AvatarUtils {
    private AvatarUtils() {
    }

    // 使用一个不会与其他资源冲突的 ID 作为 tag key
    private static final int TAG_URL = 0x7F0A0001;
    // 颜色规则：ASCII % 6
    private static final int[] AVATAR_COLORS = new int[]{
            0xFFFE812E, // 橙色
            0xFFF6A502, // 金黄色
            0xFF28C841, // 绿色
            0xFF00CFBA, // 青绿色
            0xFF2465FF, // 蓝色
            0xFFBD24FF  // 紫色
    };

    public static void loadAvatar(ImageView iv, String url, String name) {
        loadAvatar(iv, url, name, null);
    }

    public static void loadAvatar(ImageView iv, String url, String name, String userId) {
        if (iv == null) return;
        Context ctx = iv.getContext();

        // 检查 URL 是否与当前已加载的相同，避免重复加载导致闪烁
        String currentUrl = (String) iv.getTag(TAG_URL);
        if (!TextUtils.isEmpty(url)) {
            if (url.equals(currentUrl)) {
                return; // URL 相同，跳过加载
            }
            iv.setTag(TAG_URL, url);
            RequestOptions options = RequestOptions.circleCropTransform();
            Glide.with(iv)
                    .load(url)
                    .apply(options)
                    .dontAnimate() // 禁用动画，避免闪烁
                    .error(
                            Glide.with(iv.getContext())
                                    .load(R.drawable.icon_default_avatar)
                                    .apply(options)
                                    .dontAnimate()
                    )
                    .into(iv);
            return;
        }

        // 无网络头像时：颜色由 userId 决定，字符优先展示 name 首字母。
        String generatedTag = "generated:" + safeValue(userId) + ":" + safeValue(name);
        if (generatedTag.equals(currentUrl)) {
            return; // 相同的生成头像，跳过
        }
        iv.setTag(TAG_URL, generatedTag);

        String initial = extractDisplayInitial(name, userId);
        int bgColor = colorForUserId(userId);
        int sizePx = dpToPx(ctx, 40);
        Bitmap bmp = createInitialsBitmap(sizePx, initial, bgColor);
        Glide.with(iv).load(bmp).circleCrop().dontAnimate().into(iv);
    }

    public static void loadImage(ImageView iv, String url) {
        Glide.with(iv)
                .load(url)
                .centerCrop()
                .placeholder(R.drawable.default_image)
                .dontAnimate() // 禁用动画，避免图片更新时闪烁
                .into(iv);
    }

    private static String extractDisplayInitial(String name, String userId) {
        String source = !TextUtils.isEmpty(name) ? name.trim() : safeValue(userId);
        if (TextUtils.isEmpty(source)) {
            return "A";
        }
        int cp = source.codePointAt(0);
        return new String(Character.toChars(cp)).toUpperCase();
    }

    private static Bitmap createInitialsBitmap(int sizePx, String initial, int bgColor) {
        if (sizePx <= 0) sizePx = 64;
        Bitmap bmp = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bmp);

        // 固定纯色背景，不使用渐变。
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setColor(bgColor);
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

    private static int colorForUserId(String userId) {
        String normalized = safeValue(userId);
        char first = normalized.isEmpty() ? 'A' : normalized.charAt(0);
        int index = first % AVATAR_COLORS.length;
        return AVATAR_COLORS[index];
    }

    private static String safeValue(String value) {
        return value == null ? "" : value.trim();
    }

    private static int dpToPx(Context ctx, int dp) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp, ctx.getResources().getDisplayMetrics());
    }
}

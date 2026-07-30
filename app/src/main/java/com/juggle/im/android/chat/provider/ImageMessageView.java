package com.juggle.im.android.chat.provider;

import android.content.Intent;
import android.graphics.BitmapFactory;
import android.text.TextUtils;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import androidx.annotation.NonNull;

import com.bumptech.glide.Glide;
import com.juggle.im.android.R;
import com.juggle.im.android.chat.ImagePreviewActivity;
import com.juggle.im.android.model.UiMessage;
import com.juggle.im.model.messages.ImageMessage;

/**
 * Image message view powered by Glide. Avoids reflection by using ImageMessage APIs.
 */
public class ImageMessageView extends MessageView<UiMessage, ImageMessage> {
    private static final int DEFAULT_WIDTH_DP = 176;
    private static final int DEFAULT_HEIGHT_DP = 132;
    private static final int MAX_WIDTH_DP = 240;
    private static final int MAX_HEIGHT_DP = 260;
    private static final int MIN_EDGE_DP = 92;
    private static final float MAX_WIDTH_SCREEN_RATIO = 0.58f;
    private static final float LANDSCAPE_RATIO_THRESHOLD = 1.35f;
    private static final float PORTRAIT_RATIO_THRESHOLD = 0.75f;
    private static final float EXTREME_RATIO_LIMIT = 2.80f;
    /** 竖版占位图比例，与 iOS message_image_placeholder_primary(512x908) 一致 */
    private static final float PLACEHOLDER_PRIMARY_RATIO = 512f / 908f;
    /** 横版占位图比例，与 iOS message_image_placeholder_secondary(512x289) 一致 */
    private static final float PLACEHOLDER_SECONDARY_RATIO = 512f / 289f;

    public ImageMessageView(@NonNull ViewGroup root) {
        super(root, R.layout.content_image);
    }

    @Override
    protected boolean shouldShowBubble(UiMessage message, ImageMessage content) {
        return false;
    }

    @Override
    public void bindItem(UiMessage m, ImageMessage img, boolean isGroup) {
        ImageView imageView = this.itemView.findViewById(R.id.image_message_thumb);
        int[] displaySize = applyBestSize(imageView, img);
        imageView.setClipToOutline(true);
        int placeholderRes = resolvePlaceholderRes(displaySize[0], displaySize[1]);

        String url = resolveImageSource(img);
        if (TextUtils.isEmpty(url)) {
            imageView.setImageResource(placeholderRes);
        } else {
            Glide.with(imageView)
                    .load(url)
                    .centerCrop()
                    .placeholder(placeholderRes)
                    .error(placeholderRes)
                    .dontAnimate()
                    .into(imageView);
        }

        // Open full screen preview when tapping the thumbnail
        final String previewUrl = url;
        imageView.setOnClickListener(v -> {
            Intent it = new Intent(v.getContext(), ImagePreviewActivity.class);
            it.putExtra("image_url", previewUrl);
            // If context is not an Activity, need this flag
            if (!(v.getContext() instanceof android.app.Activity)) {
                it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            }
            v.getContext().startActivity(it);
        });
        // Forward long-clicks on the image to the parent itemView so the adapter's
        // long-click listener (e.g. for selection/actions) can run.
        imageView.setOnLongClickListener(v -> {
            View parent = (View) this.itemView.getParent();
            if (parent != null) {
                parent.performLongClick();
            }
            return true;
        });
    }

    /**
     * 解析图片消息可用的数据源。
     *
     * @param img 图片消息
     * @return 优先使用本地原图，再回退缩略图和远端地址
     */
    private String resolveImageSource(ImageMessage img) {
        if (!TextUtils.isEmpty(img.getLocalPath())) {
            return img.getLocalPath();
        }
        if (!TextUtils.isEmpty(img.getThumbnailLocalPath())) {
            return img.getThumbnailLocalPath();
        }
        if (!TextUtils.isEmpty(img.getThumbnailUrl())) {
            return img.getThumbnailUrl();
        }
        return img.getUrl();
    }

    /**
     * 选择与展示比例最接近的占位图。
     * TIPS: 与 iOS MessageImagePlaceholderRenderer 同一套规则——两张灰底占位图分别对应竖图(512:908)
     * 和横图(512:289)，按目标宽高比就近取用，配合 ImageView 的 centerCrop 铺满整块图片区域，
     * 避免出现原来那种"小图标被裁切/拉伸 + 透明边"的观感。
     *
     * @param displayWidth  展示宽度（px）
     * @param displayHeight 展示高度（px）
     * @return 占位图资源 id
     */
    private int resolvePlaceholderRes(int displayWidth, int displayHeight) {
        float targetRatio = displayWidth / (float) Math.max(displayHeight, 1);
        float primaryDistance = Math.abs(targetRatio - PLACEHOLDER_PRIMARY_RATIO);
        float secondaryDistance = Math.abs(targetRatio - PLACEHOLDER_SECONDARY_RATIO);
        return primaryDistance <= secondaryDistance
                ? R.drawable.message_image_placeholder_primary
                : R.drawable.message_image_placeholder_secondary;
    }

    /**
     * 计算并应用图片展示尺寸。
     *
     * @param imageView 图片控件
     * @param img       图片消息
     * @return 实际应用的宽高（px）
     */
    private int[] applyBestSize(ImageView imageView, ImageMessage img) {
        int width = img.getWidth();
        int height = img.getHeight();

        int[] localSize = resolveLocalImageSize(img);
        if (localSize != null) {
            width = localSize[0];
            height = localSize[1];
        }

        int[] target = calculateDisplaySize(imageView, width, height);
        ViewGroup.LayoutParams params = imageView.getLayoutParams();
        if (params == null) {
            params = new ViewGroup.LayoutParams(target[0], target[1]);
        } else {
            params.width = target[0];
            params.height = target[1];
        }
        imageView.setLayoutParams(params);
        return target;
    }

    private int[] resolveLocalImageSize(ImageMessage img) {
        String localPath = !TextUtils.isEmpty(img.getLocalPath()) ? img.getLocalPath() : img.getThumbnailLocalPath();
        if (TextUtils.isEmpty(localPath)) {
            return null;
        }
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(localPath, options);
        if (options.outWidth > 0 && options.outHeight > 0) {
            return new int[] { options.outWidth, options.outHeight };
        }
        return null;
    }

    /**
     * 计算图片消息展示尺寸。
     * <p>
     * 简要描述：
     * 按「横图 / 竖图 / 近方图」三种展示策略计算尺寸，目标是像微信一样在保留主视觉比例的同时，
     * 保证气泡宽高不会过大或过小。
     *
     * @param imageView 图片控件
     * @param rawWidth 原始宽
     * @param rawHeight 原始高
     * @return 目标宽高（px）
     */
    private int[] calculateDisplaySize(ImageView imageView, int rawWidth, int rawHeight) {
        int screenWidth = imageView.getResources().getDisplayMetrics().widthPixels;
        int maxWidth = Math.min(dp(imageView, MAX_WIDTH_DP), Math.round(screenWidth * MAX_WIDTH_SCREEN_RATIO));
        int maxHeight = dp(imageView, MAX_HEIGHT_DP);
        int minEdge = dp(imageView, MIN_EDGE_DP);
        int defaultWidth = dp(imageView, DEFAULT_WIDTH_DP);
        int defaultHeight = dp(imageView, DEFAULT_HEIGHT_DP);

        if (rawWidth <= 0 || rawHeight <= 0) {
            return new int[] { defaultWidth, defaultHeight };
        }

        float originRatio = rawWidth / (float) rawHeight;
        float safeRatio = clamp(originRatio, 1f / EXTREME_RATIO_LIMIT, EXTREME_RATIO_LIMIT);
        int targetWidth;
        int targetHeight;

        if (safeRatio >= LANDSCAPE_RATIO_THRESHOLD) {
            // 横图：优先拉满宽度，再按比例反算高度。
            targetWidth = maxWidth;
            targetHeight = Math.round(targetWidth / safeRatio);
            if (targetHeight < minEdge) {
                targetHeight = minEdge;
                targetWidth = Math.min(maxWidth, Math.round(targetHeight * safeRatio));
            }
        } else if (safeRatio <= PORTRAIT_RATIO_THRESHOLD) {
            // 竖图：优先拉满高度，再按比例反算宽度。
            targetHeight = maxHeight;
            targetWidth = Math.round(targetHeight * safeRatio);
            if (targetWidth < minEdge) {
                targetWidth = minEdge;
                targetHeight = Math.min(maxHeight, Math.round(targetWidth / safeRatio));
            }
        } else {
            // 近方图：使用统一基准边，保证视觉整齐。
            int baseEdge = Math.min(maxWidth, maxHeight);
            if (safeRatio >= 1f) {
                targetWidth = baseEdge;
                targetHeight = Math.round(baseEdge / safeRatio);
            } else {
                targetHeight = baseEdge;
                targetWidth = Math.round(baseEdge * safeRatio);
            }
            targetWidth = Math.max(minEdge, targetWidth);
            targetHeight = Math.max(minEdge, targetHeight);
        }

        float boundScale = Math.min(1f, Math.min(maxWidth / (float) targetWidth, maxHeight / (float) targetHeight));
        targetWidth = Math.max(1, Math.round(targetWidth * boundScale));
        targetHeight = Math.max(1, Math.round(targetHeight * boundScale));
        return new int[] { targetWidth, targetHeight };
    }

    private float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    private int dp(ImageView imageView, int value) {
        return Math.round(value * imageView.getResources().getDisplayMetrics().density);
    }
}

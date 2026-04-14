package com.juggle.im.android.chat.provider;

import android.content.Intent;
import android.graphics.BitmapFactory;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import androidx.annotation.NonNull;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.resource.bitmap.RoundedCorners;
import com.juggle.im.android.R;
import com.juggle.im.android.chat.ImagePreviewActivity;
import com.juggle.im.android.model.UiMessage;
import com.juggle.im.model.messages.ImageMessage;

/**
 * Image message view powered by Glide. Avoids reflection by using ImageMessage APIs.
 */
public class ImageMessageView extends MessageView<UiMessage, ImageMessage> {
    private static final float BUBBLE_SCALE = 0.7f;
    private static final int DEFAULT_WIDTH_DP = 160;
    private static final int DEFAULT_HEIGHT_DP = 120;
    private static final int MAX_WIDTH_DP = 220;
    private static final int MAX_HEIGHT_DP = 260;
    private static final int MIN_EDGE_DP = 92;

    public ImageMessageView(@NonNull ViewGroup root) {
        super(root, R.layout.content_image);
    }

    @Override
    public void bindItem(UiMessage m, ImageMessage img, boolean isGroup) {
        ImageView imageView = this.itemView.findViewById(R.id.image_message_thumb);
        applyBestSize(imageView, img);

        String url = img.getLocalPath() != null ? img.getLocalPath() : (img.getThumbnailUrl() != null ? img.getThumbnailUrl() : img.getUrl());
        if (TextUtils.isEmpty(url)) {
            imageView.setImageResource(R.drawable.ic_default_img);
        } else {
            int cornerRadius = Math.round(TypedValue.applyDimension(
                    TypedValue.COMPLEX_UNIT_DIP,
                    12f,
                    imageView.getResources().getDisplayMetrics()
            ));
            Glide.with(imageView)
                    .load(url)
                    .fitCenter()
                    .placeholder(R.drawable.ic_default_img)
                    .transform(new RoundedCorners(cornerRadius))
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
            return false;
        });
    }

    private void applyBestSize(ImageView imageView, ImageMessage img) {
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

    private int[] calculateDisplaySize(ImageView imageView, int rawWidth, int rawHeight) {
        int maxWidth = dp(imageView, MAX_WIDTH_DP);
        int maxHeight = dp(imageView, MAX_HEIGHT_DP);
        int minEdge = dp(imageView, MIN_EDGE_DP);
        int defaultWidth = dp(imageView, DEFAULT_WIDTH_DP);
        int defaultHeight = dp(imageView, DEFAULT_HEIGHT_DP);

        if (rawWidth <= 0 || rawHeight <= 0) {
            return new int[] { defaultWidth, defaultHeight };
        }

        float scale = Math.min(maxWidth / (float) rawWidth, maxHeight / (float) rawHeight);
        int targetWidth = Math.max(1, Math.round(rawWidth * scale));
        int targetHeight = Math.max(1, Math.round(rawHeight * scale));

        int shorterEdge = Math.min(targetWidth, targetHeight);
        if (shorterEdge < minEdge) {
            float upscale = minEdge / (float) shorterEdge;
            targetWidth = Math.round(targetWidth * upscale);
            targetHeight = Math.round(targetHeight * upscale);

            if (targetWidth > maxWidth || targetHeight > maxHeight) {
                float downscale = Math.min(maxWidth / (float) targetWidth, maxHeight / (float) targetHeight);
                targetWidth = Math.max(1, Math.round(targetWidth * downscale));
                targetHeight = Math.max(1, Math.round(targetHeight * downscale));
            }
        }
        targetWidth = Math.max(1, Math.round(targetWidth * BUBBLE_SCALE));
        targetHeight = Math.max(1, Math.round(targetHeight * BUBBLE_SCALE));
        return new int[] { targetWidth, targetHeight };
    }

    private int dp(ImageView imageView, int value) {
        return Math.round(value * imageView.getResources().getDisplayMetrics().density);
    }
}

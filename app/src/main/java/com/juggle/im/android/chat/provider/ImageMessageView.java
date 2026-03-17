package com.juggle.im.android.chat.provider;

import android.content.Intent;
import android.text.TextUtils;
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

    public ImageMessageView(@NonNull ViewGroup root) {
        super(root, R.layout.content_image);
    }

    @Override
    public void bindItem(UiMessage m, ImageMessage img, boolean isGroup) {
        ImageView imageView = this.itemView.findViewById(R.id.image_message_thumb);
        String url = img.getLocalPath() != null ? img.getLocalPath() : (img.getThumbnailUrl() != null ? img.getThumbnailUrl() : img.getUrl());
        if (TextUtils.isEmpty(url)) {
            imageView.setImageResource(R.drawable.ic_default_img);
        } else {
            Glide.with(imageView)
                    .load(url)
                    .centerCrop()
                    .transform(new RoundedCorners(20))
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
}

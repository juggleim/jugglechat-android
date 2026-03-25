package com.juggle.im.android.app;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.widget.ImageView;

import com.juggle.im.android.component.AbsAppActivity;

import com.bumptech.glide.Glide;
import com.juggle.im.android.R;

import java.io.File;

public class AvatarPreviewActivity extends AbsAppActivity {
    public static final String EXTRA_IMAGE_PATH = "extra_image_path";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_avatar_preview);

        String imagePath = getIntent().getStringExtra(EXTRA_IMAGE_PATH);
        if (TextUtils.isEmpty(imagePath)) {
            finish();
            return;
        }

        ImageView preview = findViewById(R.id.iv_preview);
        Glide.with(this)
                .load(new File(imagePath))
                .placeholder(R.drawable.default_image)
                .error(R.drawable.default_image)
                .into(preview);

        findViewById(R.id.iv_back).setOnClickListener(v -> finish());
        findViewById(R.id.btn_cancel).setOnClickListener(v -> finish());
        findViewById(R.id.btn_confirm).setOnClickListener(v -> {
            Intent intent = new Intent();
            intent.putExtra(EXTRA_IMAGE_PATH, imagePath);
            setResult(RESULT_OK, intent);
            finish();
        });
    }
}

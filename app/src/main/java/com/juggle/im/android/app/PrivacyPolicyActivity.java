package com.juggle.im.android.app;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import com.juggle.im.android.component.AbsAppActivity;

import com.juggle.im.android.R;

/**
 * 隐私政策页面
 */
public class PrivacyPolicyActivity extends AbsAppActivity {

    public static void start(@NonNull Context context) {
        Intent intent = new Intent(context, PrivacyPolicyActivity.class);
        if (!(context instanceof android.app.Activity)) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        }
        context.startActivity(intent);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_privacy_policy);

        ImageView backView = findViewById(R.id.iv_back);
        TextView titleView = findViewById(R.id.tv_title);

        titleView.setText(getString(R.string.auth_privacy_policy_page_title));
        backView.setOnClickListener(v -> finish());

        // 打开 WebView 页面显示隐私政策
        WebViewPageActivity.navToPrivacy(this);
        finish();
    }
}

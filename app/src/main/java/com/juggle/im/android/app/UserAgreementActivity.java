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
 * 用户协议页面
 */
public class UserAgreementActivity extends AbsAppActivity {

    public static void start(@NonNull Context context) {
        Intent intent = new Intent(context, UserAgreementActivity.class);
        if (!(context instanceof android.app.Activity)) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        }
        context.startActivity(intent);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_user_agreement);

        ImageView backView = findViewById(R.id.iv_back);
        TextView titleView = findViewById(R.id.tv_title);

        titleView.setText("用户协议");
        backView.setOnClickListener(v -> finish());

        // 打开WebView页面显示用户协议
        WebViewPageActivity.navToUserAgreement(this);
        finish();
    }
}

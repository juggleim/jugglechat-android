package com.juggle.im.android.app;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import com.juggle.im.android.component.AbsAppActivity;

import com.juggle.im.android.R;
import com.juggle.im.android.utils.ToastUtils;

/**
 * 通用 Web 页面承载页，用于展示协议类静态页面。
 */
public class WebViewPageActivity extends AbsAppActivity {
    private static final String EXTRA_URL = "extra_url";
    private static final String EXTRA_TITLE = "extra_title";

    private static final String USER_AGREEMENT_URL = "https://secretchat.im/user/user.html";
    private static final String PRIVACY_POLICY_URL = "https://secretchat.im/user/privacy.html";

    private WebView webView;

    public static void open(@NonNull Context context, @NonNull String url, @NonNull String title) {
        Intent intent = new Intent(context, WebViewPageActivity.class);
        intent.putExtra(EXTRA_URL, url);
        intent.putExtra(EXTRA_TITLE, title);
        if (!(context instanceof Activity)) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        }
        context.startActivity(intent);
    }

    public static void navToUserAgreement(@NonNull Context context) {
        open(context, USER_AGREEMENT_URL, context.getString(R.string.auth_user_agreement_page_title));
    }

    public static void navToPrivacy(@NonNull Context context) {
        open(context, PRIVACY_POLICY_URL, context.getString(R.string.auth_privacy_policy_page_title));
    }

    /**
     * 兼容旧命名，避免后续接入方拼写不一致导致找不到入口。
     */
    public static void navToUseragreement(@NonNull Context context) {
        navToUserAgreement(context);
    }

    /**
     * 兼容旧命名，避免后续接入方拼写不一致导致找不到入口。
     */
    public static void navToPrivace(@NonNull Context context) {
        navToPrivacy(context);
    }

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_web_view_page);

        ImageView backView = findViewById(R.id.webBack);
        TextView titleView = findViewById(R.id.webTitle);
        webView = findViewById(R.id.webView);

        backView.setOnClickListener(v -> finish());

        String url = getIntent().getStringExtra(EXTRA_URL);
        String title = getIntent().getStringExtra(EXTRA_TITLE);
        if (TextUtils.isEmpty(url)) {
            ToastUtils.show(this, R.string.operation_failed);
            finish();
            return;
        }
        titleView.setText(TextUtils.isEmpty(title) ? getString(R.string.app_name) : title);

        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setBuiltInZoomControls(false);
        settings.setDisplayZoomControls(false);
        settings.setLoadWithOverviewMode(true);
        settings.setUseWideViewPort(true);
        settings.setSupportZoom(false);
        settings.setAllowFileAccess(false);

        webView.setWebViewClient(new WebViewClient());
        webView.loadUrl(url);
    }

    @Override
    protected void onDestroy() {
        if (webView != null) {
            webView.stopLoading();
            webView.clearHistory();
            webView.removeAllViews();
            webView.destroy();
            webView = null;
        }
        super.onDestroy();
    }
}

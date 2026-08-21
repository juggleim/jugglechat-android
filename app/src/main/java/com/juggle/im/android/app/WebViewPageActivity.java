package com.juggle.im.android.app;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.RawRes;
import com.juggle.im.android.component.AbsAppActivity;

import com.juggle.im.android.R;
import com.juggle.im.android.i18n.AppRes;
import com.juggle.im.android.i18n.LanguageManager;
import com.juggle.im.android.utils.LogUtils;
import com.juggle.im.android.utils.ToastUtils;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/**
 * 通用 Web 页面承载页，用于展示协议类静态页面。
 */
public class WebViewPageActivity extends AbsAppActivity {
    private static final String EXTRA_DOCUMENT = "extra_document";
    private static final String EXTRA_TITLE = "extra_title";
    private static final String EXTRA_URL = "extra_url";

    private static final int DOCUMENT_USER_AGREEMENT = 1;
    private static final int DOCUMENT_PRIVACY_POLICY = 2;

    private WebView webView;

    private static void open(@NonNull Context context, int document, @NonNull String title) {
        Intent intent = new Intent(context, WebViewPageActivity.class);
        intent.putExtra(EXTRA_DOCUMENT, document);
        intent.putExtra(EXTRA_TITLE, title);
        if (!(context instanceof Activity)) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        }
        context.startActivity(intent);
    }

    /**
     * 打开与当前 App 语言一致的内置用户协议。
     *
     * @param context 页面上下文
     */
    public static void navToUserAgreement(@NonNull Context context) {
        open(context, DOCUMENT_USER_AGREEMENT,
                AppRes.string(R.string.auth_user_agreement_page_title));
    }

    /**
     * 打开与当前 App 语言一致的内置隐私政策。
     *
     * @param context 页面上下文
     */
    public static void navToPrivacy(@NonNull Context context) {
        open(context, DOCUMENT_PRIVACY_POLICY,
                AppRes.string(R.string.auth_privacy_policy_page_title));
    }

    /**
     * 打开一个远程网页（聊天消息里的链接等）。
     *
     * <p>仅 http/https 在应用内 WebView 打开；其他协议（tel、mailto 等）交给系统处理。</p>
     *
     * @param context 页面上下文
     * @param url     目标地址
     */
    public static void navToUrl(@NonNull Context context, String url) {
        if (TextUtils.isEmpty(url)) {
            return;
        }
        if (!isHttpUrl(url)) {
            openWithSystem(context, url);
            return;
        }
        Intent intent = new Intent(context, WebViewPageActivity.class);
        intent.putExtra(EXTRA_URL, url);
        if (!(context instanceof Activity)) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        }
        context.startActivity(intent);
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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_web_view_page);

        ImageView backView = findViewById(R.id.webBack);
        TextView titleView = findViewById(R.id.webTitle);
        webView = findViewById(R.id.webView);

        backView.setOnClickListener(v -> onBackPressed());

        String url = getIntent().getStringExtra(EXTRA_URL);
        if (!TextUtils.isEmpty(url)) {
            titleView.setText(url);
            loadRemoteUrl(url, titleView);
            return;
        }

        int document = getIntent().getIntExtra(EXTRA_DOCUMENT, 0);
        String title = getIntent().getStringExtra(EXTRA_TITLE);
        int documentResource = resolveDocumentResource(document);
        if (documentResource == 0) {
            ToastUtils.show(this, R.string.operation_failed);
            finish();
            return;
        }
        titleView.setText(TextUtils.isEmpty(title) ? getString(R.string.app_name) : title);

        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(false);
        settings.setDomStorageEnabled(false);
        settings.setDatabaseEnabled(false);
        settings.setBuiltInZoomControls(false);
        settings.setDisplayZoomControls(false);
        settings.setLoadWithOverviewMode(true);
        settings.setUseWideViewPort(true);
        settings.setSupportZoom(false);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(false);
        settings.setBlockNetworkLoads(true);

        webView.setWebViewClient(new WebViewClient());
        try {
            webView.loadDataWithBaseURL(
                    null,
                    readDocument(documentResource),
                    "text/html",
                    StandardCharsets.UTF_8.name(),
                    null);
        } catch (IOException e) {
            LogUtils.e("WebViewPageActivity", "-", "legal", "loadDocument",
                    "fail", e.getMessage());
            ToastUtils.show(this, R.string.operation_failed);
            finish();
        }
    }

    /**
     * 加载远程网页。
     *
     * <p>TIPS：内置浏览器只允许 http/https 在 WebView 里跳转，其余协议交给系统，
     * 防止 javascript:/file: 等协议被恶意链接利用；同时关闭文件访问且不注入任何 JS 接口。</p>
     *
     * @param url       目标地址
     * @param titleView 标题视图，拿到网页标题后回填
     */
    private void loadRemoteUrl(final String url, TextView titleView) {
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setBuiltInZoomControls(true);
        settings.setDisplayZoomControls(false);
        settings.setLoadWithOverviewMode(true);
        settings.setUseWideViewPort(true);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(false);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                return handleUrlLoading(request.getUrl() == null ? null : request.getUrl().toString());
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String requestUrl) {
                return handleUrlLoading(requestUrl);
            }

            @Override
            public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                // TIPS：App 全局禁止明文流量，http 页面在 WebView 内必然失败，
                // 主文档加载失败时兜底交给系统浏览器，避免用户停在白屏上。
                if (!request.isForMainFrame()) {
                    return;
                }
                LogUtils.e("WebViewPageActivity", "-", "link", "loadUrl",
                        "fail", String.valueOf(error.getErrorCode()));
                ToastUtils.show(WebViewPageActivity.this, R.string.chat_link_fallback_browser);
                openWithSystem(WebViewPageActivity.this,
                        request.getUrl() == null ? url : request.getUrl().toString());
                finish();
            }
        });
        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onReceivedTitle(WebView view, String title) {
                if (!TextUtils.isEmpty(title)) {
                    titleView.setText(title);
                }
            }
        });
        webView.loadUrl(url);
    }

    /**
     * 决定链接是留在 WebView 内还是交给系统应用打开。
     *
     * @param url 即将加载的地址
     * @return true 表示已由外部处理，WebView 不再加载
     */
    private boolean handleUrlLoading(String url) {
        if (TextUtils.isEmpty(url) || isHttpUrl(url)) {
            return false;
        }
        openWithSystem(this, url);
        return true;
    }

    private static boolean isHttpUrl(String url) {
        String lower = url.trim().toLowerCase(Locale.ROOT);
        return lower.startsWith("http://") || lower.startsWith("https://");
    }

    /**
     * 交给系统应用打开非 http/https 链接，无应用可处理时提示失败。
     */
    private static void openWithSystem(Context context, String url) {
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            if (!(context instanceof Activity)) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            }
            context.startActivity(intent);
        } catch (ActivityNotFoundException | SecurityException e) {
            LogUtils.e("WebViewPageActivity", "-", "link", "openWithSystem",
                    "fail", e.getMessage());
            ToastUtils.show(context, R.string.operation_failed);
        }
    }

    @RawRes
    private int resolveDocumentResource(int document) {
        boolean chinese = Locale.CHINESE.getLanguage()
                .equals(LanguageManager.currentLocale().getLanguage());
        if (document == DOCUMENT_USER_AGREEMENT) {
            return chinese ? R.raw.user_agreement_zh : R.raw.user_agreement_en;
        }
        if (document == DOCUMENT_PRIVACY_POLICY) {
            return chinese ? R.raw.privacy_policy_zh : R.raw.privacy_policy_en;
        }
        return 0;
    }

    private String readDocument(@RawRes int documentResource) throws IOException {
        // TIPS：协议必须从 APK 内置资源读取，禁止回退到远程 URL，确保离线内容与发布版本一致。
        try (InputStream inputStream = getResources().openRawResource(documentResource);
             BufferedReader reader = new BufferedReader(
                     new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            StringBuilder content = new StringBuilder();
            char[] buffer = new char[4096];
            int count;
            while ((count = reader.read(buffer)) != -1) {
                content.append(buffer, 0, count);
            }
            return content.toString();
        }
    }

    @Override
    public void onBackPressed() {
        if (webView != null && webView.canGoBack()) {
            webView.goBack();
            return;
        }
        super.onBackPressed();
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

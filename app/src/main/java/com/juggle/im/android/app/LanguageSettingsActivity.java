package com.juggle.im.android.app;

import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.juggle.im.android.R;
import com.juggle.im.android.component.AbsAppActivity;
import com.juggle.im.android.i18n.AppLanguage;
import com.juggle.im.android.i18n.LanguageManager;

/**
 * 语言设置页面：跟随系统 / 简体中文 / English 三选一。
 */
public class LanguageSettingsActivity extends AbsAppActivity {

    private static final AppLanguage[] OPTIONS = new AppLanguage[]{
            AppLanguage.FOLLOW_SYSTEM,
            AppLanguage.SIMPLIFIED_CHINESE,
            AppLanguage.ENGLISH
    };

    /**
     * 遮罩展示时长：让"切换中"的反馈可见，避免重启太快像闪退
     */
    private static final long RESTART_DELAY_MS = 320L;

    private LinearLayout optionContainer;
    private View switchingMask;
    private TextView switchingTip;
    private boolean switching;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_language_settings);

        findViewById(R.id.iv_back).setOnClickListener(v -> finish());
        optionContainer = findViewById(R.id.ll_options);
        switchingMask = findViewById(R.id.fl_switching_mask);
        switchingTip = findViewById(R.id.tv_switching_tip);
        renderOptions();
    }

    private void renderOptions() {
        optionContainer.removeAllViews();
        AppLanguage current = LanguageManager.getCurrentLanguage();
        LayoutInflater inflater = LayoutInflater.from(this);
        for (int i = 0; i < OPTIONS.length; i++) {
            AppLanguage language = OPTIONS[i];
            View item = inflater.inflate(R.layout.item_language_option, optionContainer, false);
            TextView name = item.findViewById(R.id.tv_language_name);
            ImageView checked = item.findViewById(R.id.iv_language_checked);
            name.setText(language.getDisplayNameRes());
            checked.setVisibility(language == current ? View.VISIBLE : View.GONE);
            item.findViewById(R.id.v_language_divider)
                    .setVisibility(i == OPTIONS.length - 1 ? View.GONE : View.VISIBLE);
            item.setOnClickListener(v -> onLanguageSelected(language));
            optionContainer.addView(item);
        }
    }

    private void onLanguageSelected(AppLanguage language) {
        if (switching || language == LanguageManager.getCurrentLanguage()) {
            return;
        }
        switching = true;
        showSwitchingMask(language);
        // TIPS: 延后一帧再重启，保证遮罩已经绘制出来，用户看到的是"切换中"而不是应用突然消失
        new Handler(Looper.getMainLooper()).postDelayed(
                () -> LanguageManager.switchLanguageAndRestart(this, language), RESTART_DELAY_MS);
    }

    /**
     * 展示切换中遮罩，提示文案用**目标语言**，符合用户刚做出的选择。
     */
    private void showSwitchingMask(AppLanguage language) {
        Context targetContext = LanguageManager.contextFor(language);
        switchingTip.setText(targetContext == null
                ? getString(R.string.language_switching)
                : targetContext.getString(R.string.language_switching));
        switchingMask.setVisibility(View.VISIBLE);
    }
}

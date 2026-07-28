package com.juggle.im.android.i18n;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.StringRes;

/**
 * 全局文案取值入口，保证在任何上下文下都拿到当前应用语言的文案。
 *
 * <p>TIPS：Activity/Fragment 内可以直接用 getString；本类用于无 Context 的静态工具类、
 * Adapter、Service 等场景 —— 它们拿到的 Application Resources 在 API 33 以下不会跟随
 * per-app locale，直接 getString 会取到系统语言的文案。</p>
 */
public final class AppRes {

    private AppRes() {
    }

    /**
     * 取本地化文案。
     *
     * @param resId 文案资源 id
     * @return 当前应用语言下的文案
     */
    @NonNull
    public static String string(@StringRes int resId) {
        Context context = LanguageManager.localizedContext();
        return context == null ? "" : context.getString(resId);
    }

    /**
     * 取带占位符的本地化文案。
     *
     * @param resId 文案资源 id
     * @param args  占位符参数
     * @return 格式化后的文案
     */
    @NonNull
    public static String string(@StringRes int resId, @NonNull Object... args) {
        Context context = LanguageManager.localizedContext();
        return context == null ? "" : context.getString(resId, args);
    }
}

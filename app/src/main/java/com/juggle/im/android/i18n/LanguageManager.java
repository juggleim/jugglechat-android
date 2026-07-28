package com.juggle.im.android.i18n;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.os.Build;

import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.os.LocaleListCompat;

import java.util.Locale;

/**
 * 应用语言管理：持久化用户选择、应用到 UI、并为无 Activity 上下文的场景提供本地化 Context。
 *
 * <p>TIPS：语言生效分两条链路，缺一不可</p>
 * <ol>
 *   <li>Activity 链路：{@link AppCompatDelegate#setApplicationLocales} 由 AppCompat 统一作用到所有
 *       AppCompatActivity，切换时自动重建界面；</li>
 *   <li>非 Activity 链路：Application / Service / 静态工具类拿到的 Resources 在 API 33 以下不受
 *       per-app locale 影响，必须走 {@link #localizedContext()} 取串（见 {@link AppRes}）。</li>
 * </ol>
 */
public final class LanguageManager {

    private static final String PREF_NAME = "my_settings";
    private static final String KEY_LANGUAGE = "app_language";

    private static Context sAppContext;
    private static AppLanguage sCurrentLanguage = AppLanguage.FOLLOW_SYSTEM;

    /**
     * 本地化 Context 缓存，语言切换或系统语言变化时失效
     */
    private static Context sLocalizedContext;
    private static Locale sLocalizedContextLocale;

    private LanguageManager() {
    }

    /**
     * 在 Application#onCreate 中调用：读取已保存的语言并立刻生效。
     *
     * @param context Application 上下文
     */
    @MainThread
    public static void init(@NonNull Context context) {
        sAppContext = context.getApplicationContext();
        sCurrentLanguage = AppLanguage.fromTag(prefs().getString(KEY_LANGUAGE, ""));
        applyToDelegate(sCurrentLanguage);
    }

    /**
     * 当前生效的语言设置。
     */
    @NonNull
    public static AppLanguage getCurrentLanguage() {
        return sCurrentLanguage;
    }

    /**
     * 切换应用语言：持久化 + 立即生效（AppCompat 会自动重建已有 Activity）。
     * <p>
     * 需要"整个应用以新语言重新打开"的场景请用 {@link #switchLanguageAndRestart}。
     *
     * @param language 目标语言
     */
    @MainThread
    public static void setLanguage(@NonNull AppLanguage language) {
        if (language == sCurrentLanguage) {
            return;
        }
        persist(language);
        applyToDelegate(language);
    }

    /**
     * 切换语言并以新语言重启应用。
     * <p>
     * TIPS：不走 AppCompat 的"逐个重建返回栈里的 Activity"，是因为那样用户会看到设置页、上级页依次重绘（闪屏），
     * 且重建后的页面拿不到 SDK 的一次性回调（连接态、会话首屏）。这里改成杀进程重启：
     * 语言先落盘（commit 而非 apply，保证进程被杀前写入），再让系统按冷启动重新拉起首页，
     * 走的是和正常启动完全一致的路径。
     *
     * @param activity 触发切换的页面
     * @param language 目标语言
     */
    @MainThread
    public static void switchLanguageAndRestart(@NonNull Activity activity, @NonNull AppLanguage language) {
        if (language != sCurrentLanguage) {
            persist(language);
            applyToDelegate(language);
        }
        Intent launchIntent = activity.getPackageManager()
                .getLaunchIntentForPackage(activity.getPackageName());
        if (launchIntent == null) {
            return;
        }
        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        activity.startActivity(launchIntent);
        activity.finish();
        Runtime.getRuntime().exit(0);
    }

    /**
     * 取指定语言的 Context，用于在切换生效前就按目标语言展示文案（如切换中的提示）。
     *
     * @param language 目标语言
     * @return 已应用该语言的 Context；未初始化时返回 null
     */
    public static Context contextFor(@NonNull AppLanguage language) {
        if (sAppContext == null) {
            return null;
        }
        Locale locale = language == AppLanguage.FOLLOW_SYSTEM
                ? systemLocale() : Locale.forLanguageTag(language.getTag());
        return createLocaleContext(locale);
    }

    private static void persist(@NonNull AppLanguage language) {
        sCurrentLanguage = language;
        // TIPS: 这里必须 commit——switchLanguageAndRestart 紧接着会杀进程，apply 的异步写入可能来不及落盘
        prefs().edit().putString(KEY_LANGUAGE, language.getTag()).commit();
        invalidateLocalizedContext();
    }

    /**
     * 当前应实际使用的 Locale：跟随系统时返回系统语言。
     */
    @NonNull
    public static Locale currentLocale() {
        if (sCurrentLanguage == AppLanguage.FOLLOW_SYSTEM) {
            return systemLocale();
        }
        return Locale.forLanguageTag(sCurrentLanguage.getTag());
    }

    /**
     * 供无 Activity 场景（Service、静态工具类、后台线程）取本地化资源的 Context。
     *
     * @return 已应用当前语言的 Context；未初始化时返回 null
     */
    public static Context localizedContext() {
        if (sAppContext == null) {
            return null;
        }
        Locale locale = currentLocale();
        // TIPS: 跟随系统时系统语言可能在运行中被改，这里用 locale 做缓存键，变化即重建
        if (sLocalizedContext != null && locale.equals(sLocalizedContextLocale)) {
            return sLocalizedContext;
        }
        sLocalizedContext = createLocaleContext(locale);
        sLocalizedContextLocale = locale;
        return sLocalizedContext;
    }

    private static Context createLocaleContext(@NonNull Locale locale) {
        Configuration configuration = new Configuration(sAppContext.getResources().getConfiguration());
        configuration.setLocale(locale);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            configuration.setLocales(new android.os.LocaleList(locale));
        }
        return sAppContext.createConfigurationContext(configuration);
    }

    /**
     * 系统语言变化时（Application#onConfigurationChanged）调用，丢弃缓存。
     */
    public static void invalidateLocalizedContext() {
        sLocalizedContext = null;
        sLocalizedContextLocale = null;
    }

    private static void applyToDelegate(@NonNull AppLanguage language) {
        LocaleListCompat locales = language == AppLanguage.FOLLOW_SYSTEM
                ? LocaleListCompat.getEmptyLocaleList()
                : LocaleListCompat.forLanguageTags(language.getTag());
        // TIPS: 冷启动时系统通常已带上 per-app locale，重复下发会多触发一次 Activity 重建（首帧闪回系统语言）
        if (locales.equals(AppCompatDelegate.getApplicationLocales())) {
            return;
        }
        AppCompatDelegate.setApplicationLocales(locales);
    }

    private static Locale systemLocale() {
        Configuration systemConfiguration = Resources.getSystem().getConfiguration();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            return systemConfiguration.getLocales().get(0);
        }
        return systemConfiguration.locale;
    }

    private static SharedPreferences prefs() {
        return sAppContext.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }
}

package com.juggle.im.android.server.http;

import com.juggle.im.android.model.ConfigUtils;

import java.io.InputStream;
import java.security.KeyStore;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;

import javax.net.ssl.*;

import okhttp3.OkHttpClient;

public class SSLHelper {
    // 获取TrustManager
    public static X509TrustManager getTrustManager(InputStream certInputStream) {
        try {
            CertificateFactory certificateFactory = CertificateFactory.getInstance("X.509");
            X509Certificate certificate = (X509Certificate) certificateFactory.generateCertificate(certInputStream);

            // 创建TrustManager并返回
            KeyStore keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
            keyStore.load(null, null);
            keyStore.setCertificateEntry("ca", certificate);

            TrustManagerFactory trustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            trustManagerFactory.init(keyStore);
            TrustManager[] trustManagers = trustManagerFactory.getTrustManagers();
            return (X509TrustManager) trustManagers[0];
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    // 获取自签名证书的SSL Socket Factory
    public static SSLSocketFactory getSSLSocketFactory(InputStream certInputStream) {
        try {
            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(null, new TrustManager[]{getTrustManager(certInputStream)}, null);
            return sslContext.getSocketFactory();
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    // 获取一个信任所有证书的SSL Socket Factory
    private static SSLSocketFactory getTrustAllSSLSocketFactory(X509TrustManager trustManager) {
        try {
            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(null, new TrustManager[]{trustManager}, null);
            return sslContext.getSocketFactory();
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    // 获取一个信任所有证书的TrustManager
    private static X509TrustManager getTrustAllManager() {
        return new X509TrustManager() {
            @Override
            public void checkClientTrusted(X509Certificate[] chain, String authType) {
            }

            @Override
            public void checkServerTrusted(X509Certificate[] chain, String authType) {
            }

            @Override
            public X509Certificate[] getAcceptedIssuers() {
                return new X509Certificate[0];
            }
        };
    }

    /**
     * 不安全 TLS 仅允许在 DEBUG + 手动开关场景下生效。
     */
    public static boolean isUnsafeTlsEnabled(boolean isDebugBuild, boolean manualSwitch) {
        return isDebugBuild && manualSwitch;
    }

    public static boolean isUnsafeTlsEnabled() {
        return isUnsafeTlsEnabled(isDebugBuild(), ConfigUtils.allowInsecureTlsForDebug);
    }

    /**
     * 将 TLS 策略应用到 OkHttpClient.Builder：
     * - 默认不改动（使用系统默认校验链路）
     * - 仅在调试且显式打开开关时，放宽证书/域名校验用于联调
     */
    public static void applyTlsPolicy(OkHttpClient.Builder builder) {
        if (!isUnsafeTlsEnabled()) {
            return;
        }
        X509TrustManager trustManager = getTrustAllManager();
        SSLSocketFactory socketFactory = getTrustAllSSLSocketFactory(trustManager);
        if (socketFactory == null) {
            return;
        }
        builder.sslSocketFactory(socketFactory, trustManager);
        builder.hostnameVerifier((hostname, session) -> true);
    }

    private static boolean isDebugBuild() {
        return readDebugFlag("com.juggle.im.app.android.BuildConfig")
                || readDebugFlag("com.juggle.im.android.BuildConfig");
    }

    private static boolean readDebugFlag(String buildConfigClassName) {
        try {
            Class<?> clazz = Class.forName(buildConfigClassName);
            return clazz.getField("DEBUG").getBoolean(null);
        } catch (Throwable ignore) {
            return false;
        }
    }
}

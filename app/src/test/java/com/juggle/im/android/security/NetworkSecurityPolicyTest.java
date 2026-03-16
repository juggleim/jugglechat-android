package com.juggle.im.android.security;

import com.juggle.im.android.model.ConfigUtils;
import com.juggle.im.android.server.http.SSLHelper;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * 网络安全基线测试：确保不再默认走不安全 TLS 策略。
 */
public class NetworkSecurityPolicyTest {

    @Test
    public void unsafeTlsShouldRequireDebugAndManualSwitch() {
        assertFalse(SSLHelper.isUnsafeTlsEnabled(false, true));
        assertFalse(SSLHelper.isUnsafeTlsEnabled(true, false));
        assertTrue(SSLHelper.isUnsafeTlsEnabled(true, true));
    }

    @Test
    public void insecureSwitchShouldDefaultToDisabled() {
        assertFalse(ConfigUtils.allowInsecureTlsForDebug);
    }
}

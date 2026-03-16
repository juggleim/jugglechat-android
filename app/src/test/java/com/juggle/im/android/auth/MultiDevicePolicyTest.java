package com.juggle.im.android.auth;

import com.juggle.im.android.server.beans.UserInfoRequest;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class MultiDevicePolicyTest {

    @Test
    public void shouldForceLogoutWhenConnectErrorCodeIs11011() {
        assertTrue(MultiDevicePolicy.shouldForceLogout(11011));
        assertFalse(MultiDevicePolicy.shouldForceLogout(500));
        assertFalse(MultiDevicePolicy.shouldForceLogout(0));
    }

    @Test
    public void shouldNormalizeAvatarAndValidateUrlProtocol() {
        UserInfoRequest request = new UserInfoRequest();
        request.setAvatar("  https://cdn.example.com/avatar.png  ");
        assertEquals("https://cdn.example.com/avatar.png", request.getAvatar());
        assertTrue(request.hasValidAvatarProtocol());

        request.setAvatar("file_id_123");
        assertFalse(request.hasValidAvatarProtocol());
    }
}

package com.juggle.im.android.auth;

import com.juggle.im.android.server.beans.LoginRequest;
import com.juggle.im.android.server.beans.RegisterRequest;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class AuthRequestFactoryTest {

    @Test
    public void buildAccountLoginRequest_shouldHashPasswordAndSetAccount() {
        LoginRequest request = AuthRequestFactory.buildAccountLoginRequest("demo001", "123123");

        assertEquals("demo001", request.getAccount());
        assertEquals("4297f44b13955235245b2497399d7a93", request.getPassword());
        assertNull(request.getEmail());
        assertNull(request.getCode());
    }

    @Test
    public void buildEmailLoginRequest_shouldSetEmailAndCode() {
        LoginRequest request = AuthRequestFactory.buildEmailLoginRequest("demo@test.com", "123456");

        assertEquals("demo@test.com", request.getEmail());
        assertEquals("123456", request.getCode());
        assertNull(request.getAccount());
        assertNull(request.getPassword());
    }

    @Test
    public void buildRegisterRequest_shouldHashPasswordAndSetAccount() {
        RegisterRequest request = AuthRequestFactory.buildRegisterRequest("demo001", "123123");

        assertEquals("demo001", request.getAccount());
        assertEquals("4297f44b13955235245b2497399d7a93", request.getPassword());
        assertNull(request.getPhone());
        assertNull(request.getEmail());
        assertNull(request.getCode());
    }
}

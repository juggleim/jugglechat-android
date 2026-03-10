package com.juggle.im.android.auth;

import com.juggle.im.android.server.beans.LoginRequest;
import com.juggle.im.android.server.beans.RegisterRequest;

public final class AuthRequestFactory {

    private AuthRequestFactory() {
    }

    public static LoginRequest buildAccountLoginRequest(String account, String plainPassword) {
        return new LoginRequest(safeTrim(account), HashUtils.md5(safeTrim(plainPassword)));
    }

    public static LoginRequest buildEmailLoginRequest(String email, String code) {
        return LoginRequest.forEmail(safeTrim(email), safeTrim(code));
    }

    public static RegisterRequest buildRegisterRequest(String account, String plainPassword) {
        RegisterRequest request = new RegisterRequest();
        request.setAccount(safeTrim(account));
        request.setPassword(HashUtils.md5(safeTrim(plainPassword)));
        return request;
    }

    private static String safeTrim(String value) {
        return value == null ? "" : value.trim();
    }
}

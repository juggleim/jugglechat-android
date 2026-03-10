package com.juggle.im.android.server.beans;

public class LoginRequest {
    private String account;
    private String password;
    private String email;
    private String code;

    public LoginRequest(String account, String password) {
        this.account = account;
        this.password = password;
    }

    public static LoginRequest forEmail(String email, String code) {
        LoginRequest request = new LoginRequest(null, null);
        request.setEmail(email);
        request.setCode(code);
        return request;
    }

    public String getAccount() {
        return account;
    }

    public void setAccount(String account) {
        this.account = account;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    // Backward-compatible alias for legacy callers.
    public String getPhone() {
        return account;
    }

    // Backward-compatible alias for legacy callers.
    public void setPhone(String phone) {
        this.account = phone;
    }
}

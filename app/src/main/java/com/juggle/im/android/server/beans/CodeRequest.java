package com.juggle.im.android.server.beans;

public class CodeRequest {
    private String phone;
    private String email;

    public CodeRequest() {
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }
}

package com.juggle.im.android.auth;

import com.juggle.im.android.R;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class LoginFlowValidatorTest {

    @Test
    public void shouldReturnAccountErrorForInvalidAccountLogin() {
        assertEquals(R.string.auth_error_account_password_required,
                AuthInputValidator.validateLoginErrorResId(false, "", "123456"));
        assertEquals(R.string.auth_error_account_password_required,
                AuthInputValidator.validateLoginErrorResId(false, "demo", ""));
        assertEquals(0, AuthInputValidator.validateLoginErrorResId(false, "demo", "123456"));
    }

    @Test
    public void shouldReturnEmailErrorForInvalidEmailLogin() {
        assertEquals(R.string.auth_error_invalid_email_and_code,
                AuthInputValidator.validateLoginErrorResId(true, "", "123456"));
        assertEquals(R.string.auth_error_invalid_email_and_code,
                AuthInputValidator.validateLoginErrorResId(true, "demo@test.com", "12"));
        assertEquals(0, AuthInputValidator.validateLoginErrorResId(true, "demo@test.com", "123456"));
    }
}

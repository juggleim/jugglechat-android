package com.juggle.im.android.auth;

import com.juggle.im.android.R;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class AuthInputValidatorTest {

    @Test
    public void accountLogin_shouldRequireAccountAndPassword() {
        assertFalse(AuthInputValidator.canSubmitAccountLogin("", "123456"));
        assertFalse(AuthInputValidator.canSubmitAccountLogin("demo", ""));
        assertTrue(AuthInputValidator.canSubmitAccountLogin("demo", "123456"));
    }

    @Test
    public void emailLogin_shouldRequireEmailAndSixDigitCode() {
        assertFalse(AuthInputValidator.canSubmitEmailLogin("", "123456"));
        assertFalse(AuthInputValidator.canSubmitEmailLogin("demo@test.com", "12345"));
        assertFalse(AuthInputValidator.canSubmitEmailLogin("demo@test.com", "abc123"));
        assertTrue(AuthInputValidator.canSubmitEmailLogin("demo@test.com", "123456"));
    }

    @Test
    public void registerValidation_shouldReturnExpectedErrorMessage() {
        assertEquals(R.string.auth_error_register_empty_account,
                AuthInputValidator.validateRegisterErrorResId("", "123456", "123456"));
        assertEquals(R.string.auth_error_register_account_invalid,
                AuthInputValidator.validateRegisterErrorResId("demo_01", "123456", "123456"));
        assertEquals(R.string.auth_error_register_account_length,
                AuthInputValidator.validateRegisterErrorResId("demo", "123456", "123456"));
        assertEquals(R.string.auth_error_register_empty_password,
                AuthInputValidator.validateRegisterErrorResId("demo001", "", ""));
        assertEquals(R.string.auth_error_register_password_length,
                AuthInputValidator.validateRegisterErrorResId("demo001", "12345", "12345"));
        assertEquals(R.string.auth_error_register_password_not_match,
                AuthInputValidator.validateRegisterErrorResId("demo001", "123456", "123457"));
        assertEquals(0, AuthInputValidator.validateRegisterErrorResId("demo001", "123456", "123456"));
    }
}

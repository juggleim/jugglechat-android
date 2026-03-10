package com.juggle.im.android.auth;

import com.juggle.im.android.R;

public final class AuthInputValidator {

    private AuthInputValidator() {
    }

    public static boolean canSubmitAccountLogin(String account, String password) {
        return !isEmpty(safeTrim(account)) && !isEmpty(safeTrim(password));
    }

    public static boolean canSubmitEmailLogin(String email, String code) {
        String trimmedEmail = safeTrim(email);
        String trimmedCode = safeTrim(code);
        return !isEmpty(trimmedEmail) && trimmedCode.matches("\\d{6}");
    }

    public static int validateRegisterErrorResId(String account, String password, String confirmPassword) {
        String trimmedAccount = safeTrim(account);
        String trimmedPassword = safeTrim(password);
        String trimmedConfirmPassword = safeTrim(confirmPassword);

        if (isEmpty(trimmedAccount)) {
            return R.string.auth_error_register_empty_account;
        }
        if (!trimmedAccount.matches("^[A-Za-z0-9]+$")) {
            return R.string.auth_error_register_account_invalid;
        }
        if (trimmedAccount.length() < 6 || trimmedAccount.length() > 20) {
            return R.string.auth_error_register_account_length;
        }
        if (isEmpty(trimmedPassword)) {
            return R.string.auth_error_register_empty_password;
        }
        if (trimmedPassword.length() < 6) {
            return R.string.auth_error_register_password_length;
        }
        if (!trimmedPassword.equals(trimmedConfirmPassword)) {
            return R.string.auth_error_register_password_not_match;
        }
        return 0;
    }

    private static String safeTrim(String value) {
        return value == null ? "" : value.trim();
    }

    private static boolean isEmpty(String value) {
        return value == null || value.isEmpty();
    }
}

package com.juggle.im.android.app;

import static android.view.View.GONE;
import static android.view.View.VISIBLE;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.RadioGroup;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;

import com.juggle.im.android.R;
import com.juggle.im.android.server.beans.CodeRequest;
import com.juggle.im.android.server.beans.LoginResult;
import com.juggle.im.android.server.beans.RegisterRequest;
import com.juggle.im.android.server.http.ApiCallback;
import com.juggle.im.android.server.http.ServiceManager;

// retrofit no longer used in RegisterActivity after switching UserService implementation

public class RegisterActivity extends AppCompatActivity {
    private RadioGroup registerTypeGroup;
    private EditText inputField;
    private EditText verificationCode;
    private Button getCodeButton;
    private EditText passwordInput;
    private Button registerButton;
    private CardView registerFormContainer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_register);

        initViews();
        setupListeners();
        // 默认选中账号注册
        registerTypeGroup.check(R.id.accountRadio);
    }

    private void initViews() {
        registerTypeGroup = findViewById(R.id.registerTypeGroup);
        inputField = findViewById(R.id.inputField);
        verificationCode = findViewById(R.id.verificationCode);
        getCodeButton = findViewById(R.id.getCodeButton);
        passwordInput = findViewById(R.id.passwordInput);
        registerButton = findViewById(R.id.registerButton);
        registerFormContainer = findViewById(R.id.register_form_container);
    }

    private void setupListeners() {
        registerTypeGroup.setOnCheckedChangeListener((group, checkedId) -> {
            if (checkedId == R.id.accountRadio) {
                inputField.setHint("请输入账号 (5-20个字符)");
                inputField.setInputType(android.text.InputType.TYPE_CLASS_TEXT);
                getCodeButton.setVisibility(GONE);
                findViewById(R.id.getCodeButtonContainer).setVisibility(GONE);
                verificationCode.setVisibility(GONE);
            } else if (checkedId == R.id.phoneRadio) {
                inputField.setHint("请输入手机号");
                inputField.setInputType(android.text.InputType.TYPE_CLASS_PHONE);
                getCodeButton.setVisibility(VISIBLE);
                findViewById(R.id.getCodeButtonContainer).setVisibility(VISIBLE);
                verificationCode.setVisibility(VISIBLE);
            } else if (checkedId == R.id.emailRadio) {
                inputField.setHint("请输入邮箱地址");
                inputField.setInputType(android.text.InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
                getCodeButton.setVisibility(VISIBLE);
                findViewById(R.id.getCodeButtonContainer).setVisibility(VISIBLE);
                verificationCode.setVisibility(VISIBLE);
            }
        });

        getCodeButton.setOnClickListener(v -> getVerificationCode());
        registerButton.setOnClickListener(v -> handleRegister());
    }

    private void getVerificationCode() {
        String input = inputField.getText().toString().trim();
        if (TextUtils.isEmpty(input)) {
            Toast.makeText(this, "请输入手机号或邮箱", Toast.LENGTH_SHORT).show();
            return;
        }

        CodeRequest request = new CodeRequest();
        if (registerTypeGroup.getCheckedRadioButtonId() == R.id.phoneRadio) {
            request.setPhone(input);
        } else if (registerTypeGroup.getCheckedRadioButtonId() == R.id.emailRadio) {
            request.setEmail(input);
        }

        ServiceManager.getUserService().getSmsVerificationCode(request, new ApiCallback<Void>() {
            @Override
            public void onSuccess(Void data) {
                Toast.makeText(RegisterActivity.this, "验证码已发送", Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onError(int code, String message) {
                Toast.makeText(RegisterActivity.this, "验证码发送失败: " + message, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void handleRegister() {
        String input = inputField.getText().toString().trim();
        String code = verificationCode.getText().toString().trim();
        String password = passwordInput.getText().toString().trim();

        if (TextUtils.isEmpty(input) || TextUtils.isEmpty(password)) {
            Toast.makeText(this, "请填写所有必填项", Toast.LENGTH_SHORT).show();
            return;
        }

        RegisterRequest request = new RegisterRequest();
        int checkedId = registerTypeGroup.getCheckedRadioButtonId();

        if (checkedId == R.id.accountRadio) {
            if (input.length() < 5 || input.length() > 20) {
                Toast.makeText(this, "账号长度应为5-20个字符", Toast.LENGTH_SHORT).show();
                return;
            }
            request.setAccount(input);
        } else if (checkedId == R.id.phoneRadio) {
            if (TextUtils.isEmpty(code)) {
                Toast.makeText(this, "请输入验证码", Toast.LENGTH_SHORT).show();
                return;
            }
            request.setPhone(input);
            request.setCode(code);
        } else if (checkedId == R.id.emailRadio) {
            if (TextUtils.isEmpty(code)) {
                Toast.makeText(this, "请输入验证码", Toast.LENGTH_SHORT).show();
                return;
            }
            request.setEmail(input);
            request.setCode(code);
        }

        request.setPassword(password);

        ServiceManager.getUserService().register(request, new ApiCallback<LoginResult>() {
            @Override
            public void onSuccess(LoginResult data) {
                Toast.makeText(RegisterActivity.this, "注册成功", Toast.LENGTH_SHORT).show();
                finish();
            }

            @Override
            public void onError(int code, String message) {
                Toast.makeText(RegisterActivity.this, "注册失败: " + message, Toast.LENGTH_SHORT).show();
            }
        });
    }
}
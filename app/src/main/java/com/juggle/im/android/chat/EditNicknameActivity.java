package com.juggle.im.android.chat;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.juggle.im.android.R;

public class EditNicknameActivity extends AppCompatActivity {

    private EditText etNickname;
    private TextView tvSave;
    private String currentNickname;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_edit_nickname);

        currentNickname = getIntent().getStringExtra("current_nickname");

        initViews();
    }

    private void initViews() {
        etNickname = findViewById(R.id.et_nickname);
        tvSave = findViewById(R.id.tv_save);
        TextView tvCancel = findViewById(R.id.tv_cancel);

        if (!TextUtils.isEmpty(currentNickname)) {
            etNickname.setText(currentNickname);
            etNickname.setSelection(currentNickname.length());
        }

        tvSave.setOnClickListener(v -> saveNickname());
        tvCancel.setOnClickListener(v -> finish());

        etNickname.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                updateSaveButton();
            }
        });

        updateSaveButton();
    }

    private void updateSaveButton() {
        String newNickname = etNickname.getText().toString().trim();
        boolean isChanged = !TextUtils.equals(currentNickname, newNickname) && !TextUtils.isEmpty(newNickname);
        tvSave.setEnabled(isChanged);
        tvSave.setAlpha(isChanged ? 1.0f : 0.5f);
    }

    private void saveNickname() {
        String newNickname = etNickname.getText().toString().trim();
        if (TextUtils.isEmpty(newNickname)) {
            return;
        }

        Intent resultIntent = new Intent();
        resultIntent.putExtra("new_nickname", newNickname);
        setResult(Activity.RESULT_OK, resultIntent);
        finish();
    }
}
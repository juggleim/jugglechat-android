package com.juggle.im.android.chat;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Window;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import com.juggle.im.android.component.AbsAppActivity;
import androidx.core.view.WindowInsetsControllerCompat;

import com.juggle.im.android.R;
import com.juggle.im.android.server.http.ApiCallback;
import com.juggle.im.android.server.http.ServiceManager;

public class GroupAnnouncementActivity extends AbsAppActivity {
    private static final String EXTRA_GROUP_ID = "extra_group_id";
    private static final String EXTRA_CONTENT = "extra_content";
    private static final String EXTRA_EDITABLE = "extra_editable";

    private String groupId;
    private boolean editable;

    private EditText contentInput;
    private TextView publishView;

    public static Intent intentFor(Context context, String groupId, String content, boolean editable) {
        Intent intent = new Intent(context, GroupAnnouncementActivity.class);
        intent.putExtra(EXTRA_GROUP_ID, groupId);
        intent.putExtra(EXTRA_CONTENT, content);
        intent.putExtra(EXTRA_EDITABLE, editable);
        return intent;
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_group_announcement);
        setupWindowStyle();

        groupId = getIntent().getStringExtra(EXTRA_GROUP_ID);
        String content = getIntent().getStringExtra(EXTRA_CONTENT);
        editable = getIntent().getBooleanExtra(EXTRA_EDITABLE, false);

        contentInput = findViewById(R.id.et_content);
        publishView = findViewById(R.id.tv_publish);

        if (TextUtils.isEmpty(content) == false) {
            contentInput.setText(content);
            contentInput.setSelection(contentInput.getText().length());
        }

        contentInput.setEnabled(editable);
        publishView.setEnabled(editable);
        publishView.setAlpha(editable ? 1f : 0f);

        findViewById(R.id.iv_back).setOnClickListener(v -> finish());
        publishView.setOnClickListener(v -> saveAnnouncement());
    }

    private void setupWindowStyle() {
        Window window = getWindow();
        window.setStatusBarColor(getColor(R.color.white));
        window.setNavigationBarColor(getColor(R.color.white));
        WindowInsetsControllerCompat controller = new WindowInsetsControllerCompat(window, window.getDecorView());
        controller.setAppearanceLightStatusBars(true);
    }

    private void saveAnnouncement() {
        if (editable == false) {
            return;
        }
        String content = contentInput.getText() == null ? "" : contentInput.getText().toString().trim();
        ServiceManager.getUserService().setGroupAnnouncement(groupId, content, new ApiCallback<Void>() {
            @Override
            public void onSuccess(Void data) {
                Toast.makeText(GroupAnnouncementActivity.this, "发布成功", Toast.LENGTH_SHORT).show();
                finish();
            }

            @Override
            public void onError(int code, String message) {
                Toast.makeText(GroupAnnouncementActivity.this,
                        "发布失败：" + message,
                        Toast.LENGTH_SHORT).show();
            }
        });
    }
}

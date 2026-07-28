package com.juggle.im.android.chat;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.View;
import android.view.Window;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import com.juggle.im.android.component.AbsAppActivity;
import androidx.core.view.WindowInsetsControllerCompat;

import com.juggle.im.android.R;
import com.juggle.im.android.server.beans.GroupAnnouncementBean;
import com.juggle.im.android.server.http.ApiCallback;
import com.juggle.im.android.server.http.ServiceManager;
import com.juggle.im.android.utils.LogUtils;

/**
 * 群公告页面
 * 支持查询群公告（GET /jim/groups/getgrpannouncement）和设置群公告（POST /jim/groups/setgrpannouncement）
 */
public class GroupAnnouncementActivity extends AbsAppActivity {
    private static final String EXTRA_GROUP_ID = "extra_group_id";
    private static final String EXTRA_EDITABLE = "extra_editable";

    private String groupId;
    private boolean editable;

    private ProgressBar progressBar;
    private EditText contentInput;
    private TextView publishView;

    /**
     * 构建启动 Intent
     *
     * @param context  上下文
     * @param groupId  群组 ID
     * @param editable 是否可编辑（管理员/群主可编辑）
     * @return 启动 Intent
     */
    public static Intent intentFor(Context context, String groupId, boolean editable) {
        Intent intent = new Intent(context, GroupAnnouncementActivity.class);
        intent.putExtra(EXTRA_GROUP_ID, groupId);
        intent.putExtra(EXTRA_EDITABLE, editable);
        return intent;
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_group_announcement);
        setupWindowStyle();

        groupId = getIntent().getStringExtra(EXTRA_GROUP_ID);
        editable = getIntent().getBooleanExtra(EXTRA_EDITABLE, false);

        progressBar = findViewById(R.id.progress_bar);
        contentInput = findViewById(R.id.et_content);
        publishView = findViewById(R.id.tv_publish);

        contentInput.setEnabled(false);
        publishView.setEnabled(false);
        publishView.setAlpha(0.5f);

        findViewById(R.id.iv_back).setOnClickListener(v -> finish());
        publishView.setOnClickListener(v -> saveAnnouncement());

        if (editable) {
            contentInput.addTextChangedListener(new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                }

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {
                }

                @Override
                public void afterTextChanged(Editable s) {
                    updatePublishButton();
                }
            });
        }

        loadAnnouncement();
    }

    private void setupWindowStyle() {
        Window window = getWindow();
        window.setStatusBarColor(getColor(R.color.white));
        window.setNavigationBarColor(getColor(R.color.white));
        WindowInsetsControllerCompat controller = new WindowInsetsControllerCompat(window, window.getDecorView());
        controller.setAppearanceLightStatusBars(true);
    }

    /**
     * 查询群公告
     * GET /jim/groups/getgrpannouncement?group_id=xxx
     */
    private void loadAnnouncement() {
        progressBar.setVisibility(View.VISIBLE);
        ServiceManager.getUserService().getGroupAnnouncement(groupId, new ApiCallback<GroupAnnouncementBean>() {
            @Override
            public void onSuccess(GroupAnnouncementBean data) {
                progressBar.setVisibility(View.GONE);
                String content = (data != null && data.getContent() != null) ? data.getContent() : "";
                if (!TextUtils.isEmpty(content)) {
                    contentInput.setText(content);
                    contentInput.setSelection(contentInput.getText().length());
                }
                contentInput.setEnabled(editable);
                updatePublishButton();
            }

            @Override
            public void onError(int code, String message) {
                progressBar.setVisibility(View.GONE);
                contentInput.setEnabled(editable);
                updatePublishButton();
                LogUtils.serverError("group", "loadAnnouncement", code, message);
                Toast.makeText(GroupAnnouncementActivity.this,
                        R.string.group_announcement_load_failed,
                        Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void updatePublishButton() {
        if (!editable) {
            publishView.setEnabled(false);
            publishView.setAlpha(0f);
            return;
        }
        String content = contentInput.getText() == null ? "" : contentInput.getText().toString().trim();
        boolean hasContent = !TextUtils.isEmpty(content);
        publishView.setEnabled(hasContent);
        publishView.setAlpha(hasContent ? 1f : 0.5f);
    }

    /**
     * 设置群公告
     * POST /jim/groups/setgrpannouncement {group_id: "xxx", content: "xxx"}
     */
    private void saveAnnouncement() {
        if (!editable) {
            return;
        }
        String content = contentInput.getText() == null ? "" : contentInput.getText().toString().trim();
        if (TextUtils.isEmpty(content)) {
            Toast.makeText(this, R.string.group_announcement_empty, Toast.LENGTH_SHORT).show();
            return;
        }

        publishView.setEnabled(false);
        progressBar.setVisibility(View.VISIBLE);
        ServiceManager.getUserService().setGroupAnnouncement(groupId, content, new ApiCallback<Void>() {
            @Override
            public void onSuccess(Void data) {
                progressBar.setVisibility(View.GONE);
                Toast.makeText(GroupAnnouncementActivity.this, R.string.group_announcement_publish_success, Toast.LENGTH_SHORT).show();

                // tips: 通知调用方刷新公告预览
                Intent result = new Intent();
                result.putExtra("announcement_content", content);
                setResult(Activity.RESULT_OK, result);
                finish();
            }

            @Override
            public void onError(int code, String message) {
                progressBar.setVisibility(View.GONE);
                publishView.setEnabled(true);
                LogUtils.serverError("group", "publishAnnouncement", code, message);
                Toast.makeText(GroupAnnouncementActivity.this,
                        R.string.group_announcement_publish_failed,
                        Toast.LENGTH_SHORT).show();
            }
        });
    }
}

package com.juggle.im.android.chat;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Window;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import com.juggle.im.android.component.AbsAppActivity;
import androidx.core.view.WindowInsetsControllerCompat;

import com.juggle.im.JIM;
import com.juggle.im.JIMConst;
import com.juggle.im.android.R;
import com.juggle.im.android.chat.utils.FileUtils;
import com.juggle.im.android.server.beans.GroupDetailBean;
import com.juggle.im.android.server.http.ApiCallback;
import com.juggle.im.android.server.http.ServiceManager;
import com.juggle.im.android.utils.AvatarUtils;

import java.util.ArrayList;

public class GroupInfoActivity extends AbsAppActivity {
    private static final String EXTRA_GROUP_ID = "extra_group_id";
    private static final int REQ_PICK_GROUP_AVATAR = 3201;

    private String groupId;
    private String portrait;
    private boolean uploadingAvatar;

    private ImageView avatarView;
    private EditText groupNameInput;

    public static Intent intentFor(Context context, String groupId) {
        Intent intent = new Intent(context, GroupInfoActivity.class);
        intent.putExtra(EXTRA_GROUP_ID, groupId);
        return intent;
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_group_info);
        setupWindowStyle();

        groupId = getIntent().getStringExtra(EXTRA_GROUP_ID);

        avatarView = findViewById(R.id.iv_group_avatar);
        groupNameInput = findViewById(R.id.et_group_name);

        findViewById(R.id.iv_back).setOnClickListener(v -> finish());
        findViewById(R.id.tv_change_avatar).setOnClickListener(v -> pickGroupAvatar());
        findViewById(R.id.tv_save).setOnClickListener(v -> saveGroupInfo());

        loadGroupInfo();
    }

    private void setupWindowStyle() {
        Window window = getWindow();
        window.setStatusBarColor(getColor(R.color.white));
        window.setNavigationBarColor(getColor(R.color.white));
        WindowInsetsControllerCompat controller = new WindowInsetsControllerCompat(window, window.getDecorView());
        controller.setAppearanceLightStatusBars(true);
    }

    private void loadGroupInfo() {
        ServiceManager.getUserService().getGroupInfo(groupId, new ApiCallback<GroupDetailBean>() {
            @Override
            public void onSuccess(GroupDetailBean data) {
                if (data == null) {
                    return;
                }
                String name = TextUtils.isEmpty(data.getGroupName()) ? groupId : data.getGroupName();
                portrait = data.getPortrait();
                AvatarUtils.loadAvatar(avatarView, portrait, name, data.getGroupId());
                groupNameInput.setText(name);
                groupNameInput.setSelection(groupNameInput.getText().length());
            }

            @Override
            public void onError(int code, String message) {
                Toast.makeText(GroupInfoActivity.this,
                        "加载群信息失败：" + message,
                        Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void pickGroupAvatar() {
        Intent intent = new Intent(this, AlbumActivity.class);
        startActivityForResult(intent, REQ_PICK_GROUP_AVATAR);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQ_PICK_GROUP_AVATAR || resultCode != RESULT_OK || data == null) {
            return;
        }
        ArrayList<String> selected = data.getStringArrayListExtra("selected_images");
        if (selected == null || selected.isEmpty()) {
            return;
        }
        String selectedPath = FileUtils.convertContentUriToFile(this, selected.get(0));
        if (TextUtils.isEmpty(selectedPath)) {
            Toast.makeText(this, "读取图片失败", Toast.LENGTH_SHORT).show();
            return;
        }
        uploadGroupAvatar(selectedPath);
    }

    private void uploadGroupAvatar(String localPath) {
        if (uploadingAvatar) {
            return;
        }
        uploadingAvatar = true;
        findViewById(R.id.tv_save).setEnabled(false);
        String groupName = groupNameInput.getText() == null ? groupId : groupNameInput.getText().toString().trim();
        AvatarUtils.loadAvatar(avatarView, localPath, groupName, groupId);
        Toast.makeText(this, "头像上传中...", Toast.LENGTH_SHORT).show();

        JIM.getInstance().getMessageManager().uploadImage(localPath, new JIMConst.IResultCallback<String>() {
            @Override
            public void onSuccess(String url) {
                uploadingAvatar = false;
                findViewById(R.id.tv_save).setEnabled(true);
                portrait = url;
                AvatarUtils.loadAvatar(avatarView, portrait, groupName, groupId);
                Toast.makeText(GroupInfoActivity.this, "头像上传成功", Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onError(int code) {
                uploadingAvatar = false;
                findViewById(R.id.tv_save).setEnabled(true);
                Toast.makeText(GroupInfoActivity.this, "头像上传失败", Toast.LENGTH_SHORT).show();
                loadGroupInfo();
            }
        });
    }

    private void saveGroupInfo() {
        if (uploadingAvatar) {
            Toast.makeText(this, "头像上传中，请稍候", Toast.LENGTH_SHORT).show();
            return;
        }
        String groupName = groupNameInput.getText() == null ? "" : groupNameInput.getText().toString().trim();
        if (TextUtils.isEmpty(groupName)) {
            Toast.makeText(this, "群组名称不能为空", Toast.LENGTH_SHORT).show();
            return;
        }
        ServiceManager.getUserService().updateGroupInfo(groupId, groupName, portrait, new ApiCallback<Void>() {
            @Override
            public void onSuccess(Void data) {
                Toast.makeText(GroupInfoActivity.this, "保存成功", Toast.LENGTH_SHORT).show();
                finish();
            }

            @Override
            public void onError(int code, String message) {
                Toast.makeText(GroupInfoActivity.this,
                        "保存失败：" + message,
                        Toast.LENGTH_SHORT).show();
            }
        });
    }
}

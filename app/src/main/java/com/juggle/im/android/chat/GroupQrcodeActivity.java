package com.juggle.im.android.chat;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Base64;
import android.view.Window;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import com.juggle.im.android.component.AbsAppActivity;
import androidx.core.view.WindowInsetsControllerCompat;

import com.juggle.im.android.R;
import com.juggle.im.android.server.beans.QRCodeBean;
import com.juggle.im.android.server.http.ApiCallback;
import com.juggle.im.android.server.http.ServiceManager;
import com.juggle.im.android.utils.AvatarUtils;

public class GroupQrcodeActivity extends AbsAppActivity {
    private static final String EXTRA_GROUP_ID = "extra_group_id";
    private static final String EXTRA_GROUP_NAME = "extra_group_name";
    private static final String EXTRA_GROUP_AVATAR = "extra_group_avatar";

    private String groupId;
    private String groupName;
    private String groupAvatar;

    private ImageView avatarView;
    private TextView nameView;
    private ImageView qrcodeView;
    private ProgressBar progressBar;

    public static Intent intentFor(Context context, String groupId, String groupName, String groupAvatar) {
        Intent intent = new Intent(context, GroupQrcodeActivity.class);
        intent.putExtra(EXTRA_GROUP_ID, groupId);
        intent.putExtra(EXTRA_GROUP_NAME, groupName);
        intent.putExtra(EXTRA_GROUP_AVATAR, groupAvatar);
        return intent;
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_group_qrcode);
        setupWindowStyle();

        groupId = getIntent().getStringExtra(EXTRA_GROUP_ID);
        groupName = getIntent().getStringExtra(EXTRA_GROUP_NAME);
        groupAvatar = getIntent().getStringExtra(EXTRA_GROUP_AVATAR);

        avatarView = findViewById(R.id.iv_group_avatar);
        nameView = findViewById(R.id.tv_group_name);
        qrcodeView = findViewById(R.id.iv_group_qrcode);
        progressBar = findViewById(R.id.progress_bar);

        findViewById(R.id.iv_back).setOnClickListener(v -> finish());

        String displayName = TextUtils.isEmpty(groupName) ? groupId : groupName;
        nameView.setText(displayName);
        AvatarUtils.loadAvatar(avatarView, groupAvatar, displayName, groupId);

        loadGroupQrcode();
    }

    private void setupWindowStyle() {
        Window window = getWindow();
        window.setStatusBarColor(getColor(R.color.white));
        window.setNavigationBarColor(getColor(R.color.white));
        WindowInsetsControllerCompat controller = new WindowInsetsControllerCompat(window, window.getDecorView());
        controller.setAppearanceLightStatusBars(true);
    }

    private void loadGroupQrcode() {
        progressBar.setVisibility(android.view.View.VISIBLE);
        ServiceManager.getUserService().getGroupQRCode(groupId, new ApiCallback<QRCodeBean>() {
            @Override
            public void onSuccess(QRCodeBean data) {
                progressBar.setVisibility(android.view.View.GONE);
                String encoded = data == null ? "" : data.getQrCode();
                if (TextUtils.isEmpty(encoded)) {
                    Toast.makeText(GroupQrcodeActivity.this, "二维码加载失败", Toast.LENGTH_SHORT).show();
                    return;
                }
                Bitmap bitmap = decodeBase64Bitmap(encoded);
                if (bitmap == null) {
                    Toast.makeText(GroupQrcodeActivity.this, "二维码解析失败", Toast.LENGTH_SHORT).show();
                    return;
                }
                qrcodeView.setImageBitmap(bitmap);
            }

            @Override
            public void onError(int code, String message) {
                progressBar.setVisibility(android.view.View.GONE);
                Toast.makeText(GroupQrcodeActivity.this, "二维码加载失败：" + message, Toast.LENGTH_SHORT).show();
            }
        });
    }

    @Nullable
    private Bitmap decodeBase64Bitmap(String base64Data) {
        if (TextUtils.isEmpty(base64Data)) {
            return null;
        }
        String cleanValue = base64Data.trim();
        int commaIndex = cleanValue.indexOf(',');
        if (cleanValue.startsWith("data:") && commaIndex > 0) {
            cleanValue = cleanValue.substring(commaIndex + 1);
        }
        try {
            byte[] bytes = Base64.decode(cleanValue, Base64.DEFAULT);
            return BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}

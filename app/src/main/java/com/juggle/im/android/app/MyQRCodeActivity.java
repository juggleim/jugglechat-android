package com.juggle.im.android.app;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Base64;
import android.view.View;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.juggle.im.JIM;
import com.juggle.im.android.R;
import com.juggle.im.android.component.AbsAppActivity;
import com.juggle.im.android.server.beans.QRCodeBean;
import com.juggle.im.android.server.beans.UserInfoBean;
import com.juggle.im.android.server.http.ApiCallback;
import com.juggle.im.android.server.http.ServiceManager;
import com.juggle.im.android.utils.AvatarUtils;

/**
 * 我的二维码页面
 */
public class MyQRCodeActivity extends AbsAppActivity {
    private ImageView avatarView;
    private TextView nameView;
    private ImageView qrCodeView;
    private ProgressBar progressBar;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_my_qrcode);

        avatarView = findViewById(R.id.iv_user_avatar);
        nameView = findViewById(R.id.tv_user_name);
        qrCodeView = findViewById(R.id.iv_qrcode);
        progressBar = findViewById(R.id.progress_bar);

        ((TextView) findViewById(R.id.tv_title)).setText("我的二维码");

        findViewById(R.id.iv_back).setOnClickListener(v -> finish());
        findViewById(R.id.tv_save).setOnClickListener(v -> Toast.makeText(this, "保存功能开发中", Toast.LENGTH_SHORT).show());

        loadProfile();
        loadQrCode();
    }

    private void loadProfile() {
        String userId = JIM.getInstance().getCurrentUserId();
        ServiceManager.getUserService().getUserInfo(userId, new ApiCallback<UserInfoBean>() {
            @Override
            public void onSuccess(UserInfoBean data) {
                if (data == null) {
                    return;
                }
                String name = safeText(data.getNickname(), userId);
                nameView.setText(name);
                AvatarUtils.loadAvatar(avatarView, safeText(data.getAvatar(), ""), name, userId);
            }

            @Override
            public void onError(int code, String message) {
                nameView.setText(userId);
            }
        });
    }

    private void loadQrCode() {
        progressBar.setVisibility(View.VISIBLE);
        ServiceManager.getUserService().getQRCode(new ApiCallback<QRCodeBean>() {
            @Override
            public void onSuccess(QRCodeBean data) {
                progressBar.setVisibility(View.GONE);
                String encoded = data == null ? "" : data.getQrCode();
                if (TextUtils.isEmpty(encoded)) {
                    Toast.makeText(MyQRCodeActivity.this, "二维码加载失败", Toast.LENGTH_SHORT).show();
                    return;
                }
                Bitmap bitmap = decodeBase64Bitmap(encoded);
                if (bitmap == null) {
                    Toast.makeText(MyQRCodeActivity.this, "二维码解析失败", Toast.LENGTH_SHORT).show();
                    return;
                }
                qrCodeView.setImageBitmap(bitmap);
            }

            @Override
            public void onError(int code, String message) {
                progressBar.setVisibility(View.GONE);
                Toast.makeText(MyQRCodeActivity.this, "二维码加载失败：" + safeText(message, "未知错误"), Toast.LENGTH_SHORT).show();
            }
        });
    }

    @Nullable
    private Bitmap decodeBase64Bitmap(String base64Data) {
        if (TextUtils.isEmpty(base64Data)) {
            return null;
        }
        String clean = base64Data.trim();
        int commaIndex = clean.indexOf(',');
        if (clean.startsWith("data:") && commaIndex > 0) {
            clean = clean.substring(commaIndex + 1);
        }
        try {
            byte[] bytes = Base64.decode(clean, Base64.DEFAULT);
            return BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private String safeText(String value, String fallback) {
        if (value == null) {
            return fallback;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? fallback : trimmed;
    }
}

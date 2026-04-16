package com.juggle.im.android.app;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.juggle.im.android.R;
import com.juggle.im.android.component.AbsAppActivity;
import com.juggle.im.android.server.beans.FriendApplicationBean;
import com.juggle.im.android.server.beans.UserInfoBean;
import com.juggle.im.android.server.http.ApiCallback;
import com.juggle.im.android.server.http.ServiceManager;
import com.juggle.im.android.utils.AvatarUtils;

/**
 * 用户详情页面。
 * 展示通过扫码或搜索找到的用户信息，支持发送好友申请。
 * 通过 Intent extra "user_id" 传入目标用户 ID。
 */
public class ContactDetailActivity extends AbsAppActivity {

    public static final String EXTRA_USER_ID = "user_id";

    private ImageView ivAvatar;
    private TextView tvNickname;
    private TextView tvAccount;
    private View layoutUserInfo;
    private ProgressBar progressBar;
    private TextView tvAddFriend;
    private TextView tvAlreadyFriend;

    private String userId;
    private boolean isFriend = false;
    private boolean addRequestSent = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_contact_detail);

        userId = getIntent().getStringExtra(EXTRA_USER_ID);
        if (TextUtils.isEmpty(userId)) {
            finish();
            return;
        }

        ivAvatar = findViewById(R.id.iv_avatar);
        tvNickname = findViewById(R.id.tv_nickname);
        tvAccount = findViewById(R.id.tv_account);
        layoutUserInfo = findViewById(R.id.layout_user_info);
        progressBar = findViewById(R.id.progress_bar);
        tvAddFriend = findViewById(R.id.tv_add_friend);
        tvAlreadyFriend = findViewById(R.id.tv_already_friend);

        findViewById(R.id.btn_back).setOnClickListener(v -> finish());

        tvAddFriend.setOnClickListener(v -> applyFriend());

        loadUserInfo();
    }

    /**
     * 从服务端加载目标用户信息。
     * GET /jim/users/info?user_id=xxx
     */
    private void loadUserInfo() {
        progressBar.setVisibility(View.VISIBLE);
        layoutUserInfo.setVisibility(View.GONE);

        ServiceManager.getUserService().getUserInfo(userId, new ApiCallback<UserInfoBean>() {
            @Override
            public void onSuccess(UserInfoBean data) {
                progressBar.setVisibility(View.GONE);
                if (data == null) {
                    Toast.makeText(ContactDetailActivity.this,
                            R.string.contact_detail_load_failed, Toast.LENGTH_SHORT).show();
                    return;
                }
                bindUserInfo(data);
            }

            @Override
            public void onError(int code, String message) {
                progressBar.setVisibility(View.GONE);
                Toast.makeText(ContactDetailActivity.this,
                        R.string.contact_detail_load_failed, Toast.LENGTH_SHORT).show();
            }
        });
    }

    /**
     * 将用户信息绑定到 UI。
     *
     * @param user 服务端返回的用户信息
     */
    private void bindUserInfo(UserInfoBean user) {
        layoutUserInfo.setVisibility(View.VISIBLE);
        String name = user.getNickname();
        if (TextUtils.isEmpty(name)) {
            name = user.getUserId();
        }
        tvNickname.setText(name);
        AvatarUtils.loadAvatar(ivAvatar, user.getAvatar(), name, user.getUserId());

        // tips: 仅在账号非空且不等于用户ID时显示账号行，避免冗余信息
        String account = user.getUserId();
        if (!TextUtils.isEmpty(account)) {
            tvAccount.setText(getString(R.string.contact_detail_account, account));
            tvAccount.setVisibility(View.VISIBLE);
        } else {
            tvAccount.setVisibility(View.GONE);
        }

        isFriend = user.isFriend();
        updateActionButtons();
    }

    private void updateActionButtons() {
        if (addRequestSent) {
            tvAddFriend.setVisibility(View.GONE);
            tvAlreadyFriend.setVisibility(View.VISIBLE);
            tvAlreadyFriend.setText(R.string.contact_detail_request_sent);
            return;
        }
        if (isFriend) {
            tvAddFriend.setVisibility(View.GONE);
            tvAlreadyFriend.setVisibility(View.VISIBLE);
        } else {
            tvAlreadyFriend.setVisibility(View.GONE);
            tvAddFriend.setVisibility(View.VISIBLE);
        }
    }

    /**
     * 发送好友申请。
     * POST /jim/friends/apply {friend_id: userId}
     */
    private void applyFriend() {
        if (TextUtils.isEmpty(userId)) return;
        tvAddFriend.setEnabled(false);

        ServiceManager.getUserService().applyFriend(userId,
                new ApiCallback<FriendApplicationBean>() {
                    @Override
                    public void onSuccess(FriendApplicationBean data) {
                        addRequestSent = true;
                        updateActionButtons();
                        Toast.makeText(ContactDetailActivity.this,
                                R.string.contact_detail_request_sent, Toast.LENGTH_SHORT).show();
                    }

                    @Override
                    public void onError(int code, String message) {
                        tvAddFriend.setEnabled(true);
                        Toast.makeText(ContactDetailActivity.this,
                                getString(R.string.contact_detail_add_failed, message),
                                Toast.LENGTH_SHORT).show();
                    }
                });
    }
}

package com.juggle.im.android.app;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.juggle.im.JIM;
import com.juggle.im.android.R;
import com.juggle.im.android.chat.ConversationActivity;
import com.juggle.im.android.chat.call.BaseCallActivity;
import com.juggle.im.android.component.AbsAppActivity;
import com.juggle.im.android.server.beans.FriendApplicationBean;
import com.juggle.im.android.server.beans.UserInfoBean;
import com.juggle.im.android.server.http.ApiCallback;
import com.juggle.im.android.server.http.ServiceManager;
import com.juggle.im.android.utils.AvatarUtils;
import com.juggle.im.android.widget.AppConfirmDialog;

import java.util.ArrayList;

/**
 * 联系人详情页面。
 * 展示联系人头像、昵称、账号及操作入口。
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
    private View cardFriendActions, cardFriendTips;
    private View rowSendMessage;
    private View rowAudioVideoCall;
    private View rowDeleteContact;
    private View rowReportContact;

    private String userId;
    private String displayName;
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
        cardFriendActions = findViewById(R.id.card_friend_actions);
        cardFriendTips = findViewById(R.id.card_friend_tips);
        rowSendMessage = findViewById(R.id.row_send_message);
        rowAudioVideoCall = findViewById(R.id.row_audio_video_call);
        rowDeleteContact = findViewById(R.id.row_delete_contact);
        rowReportContact = findViewById(R.id.row_report_contact);

        bindActionRows();

        findViewById(R.id.btn_back).setOnClickListener(v -> finish());
        tvAddFriend.setOnClickListener(v -> applyFriend());
        rowSendMessage.setOnClickListener(v -> openConversation());
        rowAudioVideoCall.setOnClickListener(v -> startVoiceCall());
        rowDeleteContact.setOnClickListener(v -> confirmDeleteContact());
        rowReportContact.setOnClickListener(v -> openReportPage());

        loadUserInfo();
    }

    /**
     * 绑定页面操作行样式与文案。
     */
    private void bindActionRows() {
        bindActionRow(rowSendMessage, R.drawable.icon_msg,
                R.string.contact_detail_send_message, false, false);
        bindActionRow(rowAudioVideoCall, R.drawable.ic_video_call,
                R.string.contact_detail_audio_video_call, false, true);
        bindActionRow(rowDeleteContact, R.drawable.ic_msg_action_delete,
                R.string.contact_detail_delete_contact, true, false);
        bindActionRow(rowReportContact, R.drawable.icon_warning,
                R.string.contact_detail_report_contact, true, true);
    }

    /**
     * 配置单个操作行的图标、文案和视觉状态。
     *
     * @param row 目标行根视图
     * @param iconRes 图标资源
     * @param titleRes 标题文案资源
     * @param danger 是否为危险操作
     * @param hideDivider 是否隐藏底部分割线
     */
    private void bindActionRow(View row, int iconRes, int titleRes, boolean danger, boolean hideDivider) {
        ImageView iconView = row.findViewById(R.id.iv_row_icon);
        TextView titleView = row.findViewById(R.id.tv_row_title);
        TextView subtitleView = row.findViewById(R.id.tv_row_subtitle);
        ImageView arrowView = row.findViewById(R.id.iv_row_arrow);
        View dividerView = row.findViewById(R.id.row_divider);

        iconView.setImageResource(iconRes);
        titleView.setText(titleRes);
        titleView.setTextColor(getColor(danger ? R.color.red : R.color.conversation_primary_text));
        subtitleView.setVisibility(View.GONE);
        arrowView.setVisibility(View.GONE);
        dividerView.setVisibility(hideDivider ? View.GONE : View.VISIBLE);
    }

    /**
     * 从服务端加载目标用户信息。
     * GET /jim/users/info?user_id=xxx
     */
    private void loadUserInfo() {
        progressBar.setVisibility(View.VISIBLE);
        layoutUserInfo.setVisibility(View.GONE);
        tvAddFriend.setVisibility(View.GONE);
        cardFriendActions.setVisibility(View.GONE);
        cardFriendTips.setVisibility(View.GONE);

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
        displayName = user.getNickname();
        if (TextUtils.isEmpty(displayName)) {
            displayName = user.getUserId();
        }
        tvNickname.setText(displayName);
        AvatarUtils.loadAvatar(ivAvatar, user.getAvatar(), displayName, user.getUserId());

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

    /**
     * 根据好友关系切换页面底部操作区。
     */
    private void updateActionButtons() {
        if (addRequestSent || !isFriend) {
            cardFriendActions.setVisibility(View.GONE);
            cardFriendTips.setVisibility(View.GONE);
            tvAddFriend.setVisibility(View.VISIBLE);
            tvAddFriend.setText(addRequestSent
                    ? R.string.contact_detail_request_sent
                    : R.string.contact_detail_add_to_contacts);
            tvAddFriend.setEnabled(!addRequestSent);
        } else {
            tvAddFriend.setVisibility(View.GONE);
            cardFriendActions.setVisibility(View.VISIBLE);
            cardFriendTips.setVisibility(View.VISIBLE);

        }
    }

    /**
     * 发送好友申请。
     * POST /jim/friends/apply {friend_id: userId}
     */
    private void applyFriend() {
        if (TextUtils.isEmpty(userId) || addRequestSent) {
            return;
        }
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

    /**
     * 跳转私聊会话页面。
     */
    private void openConversation() {
        Intent intent = ConversationActivity.intentFor(this, userId, false, displayName);
        startActivity(intent);
    }

    /**
     * 发起单人语音通话。
     */
    private void startVoiceCall() {
        ArrayList<String> targetUserIds = new ArrayList<>();
        targetUserIds.add(userId);
        BaseCallActivity.startSingleCall(
                this,
                userId,
                false,
                false,
                JIM.getInstance().getCurrentUserId(),
                targetUserIds,
                "outgoing");
    }

    /**
     * 展示删除联系人确认框。
     */
    private void confirmDeleteContact() {
        AppConfirmDialog.builder(this)
                .setTitle(getString(R.string.contact_detail_delete_title))
                .setMessage(getString(R.string.contact_detail_delete_message, displayName))
                .setNegativeText(getString(R.string.txt_cancel))
                .setPositiveText(getString(R.string.delete))
                .setOnPositiveClick(() -> Toast.makeText(
                        this,
                        R.string.contact_detail_delete_todo,
                        Toast.LENGTH_SHORT
                ).show())
                .show();
    }

    /**
     * 跳转举报页面。
     */
    private void openReportPage() {
        startActivity(FeedbackActivity.intentForReport(this, userId));
    }
}

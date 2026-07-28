package com.juggle.im.android.chat;

import static com.juggle.im.android.chat.SelectMemberActivity.GROUP_ID;
import static com.juggle.im.android.chat.SelectMemberActivity.SELECTED_MEMBERS;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.view.Window;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import com.juggle.im.android.component.AbsAppActivity;
import androidx.core.view.WindowInsetsControllerCompat;

import com.juggle.im.android.R;
import com.juggle.im.android.chat.component.UserListAdapter;
import com.juggle.im.android.chat.widget.SettingRowView;
import com.juggle.im.android.server.beans.GroupDetailBean;
import com.juggle.im.android.server.beans.GroupManagementBean;
import com.juggle.im.android.server.http.ApiCallback;
import com.juggle.im.android.server.http.ServiceManager;
import com.juggle.im.android.widget.AppConfirmDialog;

import java.util.ArrayList;
import com.juggle.im.android.utils.LogUtils;

public class GroupManagementActivity extends AbsAppActivity {
    private static final String EXTRA_GROUP_ID = "extra_group_id";
    private static final int REQ_CHANGE_OWNER = 2001;
    private static final int REQ_ROLE_SETTING = 2002;

    private static final int ROLE_OWNER = 1;

    private static final String KEY_ADD_MEMBER = "group_add_member_right";
    private static final String KEY_TOP_MSG = "group_top_msg_right";
    private static final String KEY_MENTION_ALL = "group_mention_all_right";
    private static final String KEY_EDIT_MSG = "group_edit_msg_right";
    private static final String KEY_SEND_MSG = "group_send_msg_right";
    private static final String KEY_SET_MSG_LIFE = "group_set_msg_life_right";

    private String groupId;
    private GroupDetailBean groupDetail;
    private GroupManagementBean management;

    private SettingRowView rowAddMember;
    private SettingRowView rowTop;
    private SettingRowView rowMention;
    private SettingRowView rowEdit;
    private SettingRowView rowChat;
    private SettingRowView rowLife;
    private SettingRowView rowHistory;
    private SettingRowView rowAdmins;
    private SettingRowView rowTransferOwner;

    private View cardOwnerActions;
    private TextView dissolveButton;

    private boolean bindingHistorySwitch;

    /**
     * 构建群组管理页面的启动参数。
     *
     * @param context 页面上下文
     * @param groupId 群组 ID
     * @return 启动群组管理页的 Intent
     */
    public static Intent intentFor(Context context, String groupId) {
        Intent intent = new Intent(context, GroupManagementActivity.class);
        intent.putExtra(EXTRA_GROUP_ID, groupId);
        return intent;
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_group_management);
        setupWindowStyle();

        groupId = getIntent().getStringExtra(EXTRA_GROUP_ID);

        findViewById(R.id.iv_back).setOnClickListener(v -> finish());
        findViewById(R.id.tv_confirm).setOnClickListener(v -> finish());

        cardOwnerActions = findViewById(R.id.card_owner_actions);
        dissolveButton = findViewById(R.id.btn_dissolve_group);

        // 绑定设置行
        rowAddMember = findViewById(R.id.row_setting_add_member);
        rowTop = findViewById(R.id.row_setting_pin);
        rowMention = findViewById(R.id.row_setting_mention);
        rowEdit = findViewById(R.id.row_setting_edit);
        rowChat = findViewById(R.id.row_setting_chat);
        rowLife = findViewById(R.id.row_setting_life);
        rowHistory = findViewById(R.id.row_history_visible);
        rowAdmins = findViewById(R.id.row_group_admins);
        rowTransferOwner = findViewById(R.id.row_transfer_owner);

        // 设置默认副标题
        rowAddMember.setSubtitle(getString(R.string.group_role_all_members));
        rowTop.setSubtitle(getString(R.string.group_role_all_members));
        rowMention.setSubtitle(getString(R.string.group_role_all_members));
        rowEdit.setSubtitle(getString(R.string.group_role_all_members));
        rowChat.setSubtitle(getString(R.string.group_role_all_members));
        rowLife.setSubtitle(getString(R.string.group_role_all_members));

        setupHistoryRow();
        bindActions();

        loadGroupInfo();
    }

    private void setupWindowStyle() {
        Window window = getWindow();
        window.setStatusBarColor(getColor(R.color.white));
        window.setNavigationBarColor(getColor(R.color.white));
        WindowInsetsControllerCompat controller = new WindowInsetsControllerCompat(window, window.getDecorView());
        controller.setAppearanceLightStatusBars(true);
    }

    private void bindActions() {
        rowAddMember.setOnRowClickListener(v -> openRoleSettingPage(getString(R.string.group_perm_add_member),
                KEY_ADD_MEMBER,
                management == null ? GroupManagementRoleHelper.SETTING_ALL : management.getGroupAddMemberRight()));
        rowTop.setOnRowClickListener(v -> openRoleSettingPage(getString(R.string.group_perm_top_message),
                KEY_TOP_MSG,
                management == null ? GroupManagementRoleHelper.SETTING_ALL : management.getGroupTopMsgRight()));
        rowMention.setOnRowClickListener(v -> openRoleSettingPage(getString(R.string.group_perm_mention_all),
                KEY_MENTION_ALL,
                management == null ? GroupManagementRoleHelper.SETTING_ALL : management.getGroupMentionAllRight()));
        rowEdit.setOnRowClickListener(v -> openRoleSettingPage(getString(R.string.group_perm_edit_info),
                KEY_EDIT_MSG,
                management == null ? GroupManagementRoleHelper.SETTING_ALL : management.getGroupEditMsgRight()));
        rowChat.setOnRowClickListener(v -> openRoleSettingPage(getString(R.string.group_perm_send_message),
                KEY_SEND_MSG,
                management == null ? GroupManagementRoleHelper.SETTING_ALL : management.getGroupSendMsgRight()));
        rowLife.setOnRowClickListener(v -> openRoleSettingPage(getString(R.string.group_perm_lifetime),
                KEY_SET_MSG_LIFE,
                management == null ? GroupManagementRoleHelper.SETTING_ALL : management.getGroupSetMsgLifeRight()));

        rowAdmins.setOnRowClickListener(v -> {
            if (groupDetail == null) {
                return;
            }
            String ownerId = groupDetail.getOwner() == null ? "" : groupDetail.getOwner().getUserId();
            startActivity(GroupAdminsActivity.intentFor(this, groupId, ownerId));
        });

        rowTransferOwner.setOnRowClickListener(v -> {
            Intent intent = new Intent(this, SelectMemberActivity.class);
            intent.putExtra(GROUP_ID, groupId);
            intent.putExtra("mode", UserListAdapter.LIST_MODE_SELECT_MEMBER);
            startActivityForResult(intent, REQ_CHANGE_OWNER);
        });

        dissolveButton.setOnClickListener(v -> AppConfirmDialog.builder(this)
                .setTitle(getString(R.string.group_dissolve))
                .setMessage(getString(R.string.group_dissolve_confirm))
                .setNegativeText(getString(R.string.txt_cancel))
                .setPositiveText(getString(R.string.create_group_confirm))
                .setOnPositiveClick(() -> ServiceManager.getUserService().dissolveGroup(groupId, new ApiCallback<Void>() {
                    @Override
                    public void onSuccess(Void data) {
                        Toast.makeText(GroupManagementActivity.this, R.string.group_dissolve_success, Toast.LENGTH_SHORT).show();
                        finish();
                    }

                    @Override
                    public void onError(int code, String message) {
                        LogUtils.serverError("group", "dissolveGroup", code, message);
                        Toast.makeText(GroupManagementActivity.this,
                                R.string.group_dissolve_failed,
                                Toast.LENGTH_SHORT).show();
                    }
                }))
                .show());
    }

    /**
     * 打开权限二级设置页，保持与 snailchat 一致的“进入页面后保存”的交互。
     */
    private void openRoleSettingPage(String title, String key, int currentValue) {
        Intent intent = GroupManagementRoleSettingActivity.intentFor(this, groupId, title, key, currentValue);
        startActivityForResult(intent, REQ_ROLE_SETTING);
    }

    private void setupHistoryRow() {
        rowHistory.setOnSwitchCheckedChangeListener((view, isChecked) -> {
            if (bindingHistorySwitch || management == null) {
                return;
            }
            ServiceManager.getUserService().setGroupHistoryMessageVisible(groupId, isChecked, new ApiCallback<Void>() {
                @Override
                public void onSuccess(Void data) {
                    management.setHistoryMessageVisible(isChecked ? 1 : 0);
                }

                @Override
                public void onError(int code, String message) {
                    bindingHistorySwitch = true;
                    rowHistory.setSwitchChecked(!isChecked);
                    bindingHistorySwitch = false;
                    LogUtils.serverError("group", "saveHistorySetting", code, message);
                    Toast.makeText(GroupManagementActivity.this,
                            R.string.group_management_save_failed,
                            Toast.LENGTH_SHORT).show();
                }
            });
        });
    }

    private void loadGroupInfo() {
        ServiceManager.getUserService().getGroupInfo(groupId, new ApiCallback<GroupDetailBean>() {
            @Override
            public void onSuccess(GroupDetailBean data) {
                groupDetail = data;
                management = data == null ? null : data.getGroupManagement();
                if (management == null) {
                    management = new GroupManagementBean();
                }
                renderManagement();
            }

            @Override
            public void onError(int code, String message) {
                LogUtils.serverError("group", "loadGroupManagement", code, message);
                Toast.makeText(GroupManagementActivity.this,
                        R.string.group_management_load_failed,
                        Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void renderManagement() {
        rowAddMember.setSubtitle(GroupManagementRoleHelper.roleToText(management.getGroupAddMemberRight()));
        rowTop.setSubtitle(GroupManagementRoleHelper.roleToText(management.getGroupTopMsgRight()));
        rowMention.setSubtitle(GroupManagementRoleHelper.roleToText(management.getGroupMentionAllRight()));
        rowEdit.setSubtitle(GroupManagementRoleHelper.roleToText(management.getGroupEditMsgRight()));
        rowChat.setSubtitle(GroupManagementRoleHelper.roleToText(management.getGroupSendMsgRight()));
        rowLife.setSubtitle(GroupManagementRoleHelper.roleToText(management.getGroupSetMsgLifeRight()));

        bindingHistorySwitch = true;
        rowHistory.setSwitchChecked(management.getHistoryMessageVisible() == 1);
        bindingHistorySwitch = false;

        boolean isOwner = groupDetail != null && groupDetail.getMyRole() == ROLE_OWNER;
        cardOwnerActions.setVisibility(isOwner ? View.VISIBLE : View.GONE);
        dissolveButton.setVisibility(isOwner ? View.VISIBLE : View.GONE);
    }

    private void updateManagementValue(String key, int value) {
        if (management == null) {
            return;
        }
        switch (key) {
            case KEY_ADD_MEMBER:
                management.setGroupAddMemberRight(value);
                break;
            case KEY_TOP_MSG:
                management.setGroupTopMsgRight(value);
                break;
            case KEY_MENTION_ALL:
                management.setGroupMentionAllRight(value);
                break;
            case KEY_EDIT_MSG:
                management.setGroupEditMsgRight(value);
                break;
            case KEY_SEND_MSG:
                management.setGroupSendMsgRight(value);
                break;
            case KEY_SET_MSG_LIFE:
                management.setGroupSetMsgLifeRight(value);
                break;
            default:
                break;
        }
    }

    /**
     * 简要描述：
     * 统一处理二级权限页返回，避免每个设置项单独写一套回传解析逻辑。
     */
    private void handleRoleSettingResult(@Nullable Intent data) {
        if (data == null) {
            return;
        }
        String key = data.getStringExtra(GroupManagementRoleSettingActivity.RESULT_SETTING_KEY);
        if (TextUtils.isEmpty(key)) {
            return;
        }
        int selectedValue = data.getIntExtra(
                GroupManagementRoleSettingActivity.RESULT_SETTING_VALUE,
                GroupManagementRoleHelper.SETTING_ALL);
        if (management == null) {
            management = new GroupManagementBean();
        }
        updateManagementValue(key, selectedValue);
        SettingRowView rowView = findPermissionRowByKey(key);
        if (rowView != null) {
            rowView.setSubtitle(GroupManagementRoleHelper.roleToText(selectedValue));
        }
    }

    private SettingRowView findPermissionRowByKey(String key) {
        switch (key) {
            case KEY_ADD_MEMBER:
                return rowAddMember;
            case KEY_TOP_MSG:
                return rowTop;
            case KEY_MENTION_ALL:
                return rowMention;
            case KEY_EDIT_MSG:
                return rowEdit;
            case KEY_SEND_MSG:
                return rowChat;
            case KEY_SET_MSG_LIFE:
                return rowLife;
            default:
                return null;
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_ROLE_SETTING && resultCode == RESULT_OK) {
            handleRoleSettingResult(data);
            return;
        }
        if (requestCode == REQ_CHANGE_OWNER && resultCode == RESULT_OK && data != null) {
            ArrayList<String> selected = data.getStringArrayListExtra(SELECTED_MEMBERS);
            if (selected == null || selected.isEmpty()) {
                return;
            }
            String ownerId = selected.get(0);
            if (TextUtils.isEmpty(ownerId)) {
                return;
            }
            ServiceManager.getUserService().changeGroupOwner(groupId, ownerId, new ApiCallback<Void>() {
                @Override
                public void onSuccess(Void data) {
                    Toast.makeText(GroupManagementActivity.this, R.string.group_transfer_success, Toast.LENGTH_SHORT).show();
                    finish();
                }

                @Override
                public void onError(int code, String message) {
                    LogUtils.serverError("group", "transferOwner", code, message);
                    Toast.makeText(GroupManagementActivity.this,
                            R.string.group_transfer_failed,
                            Toast.LENGTH_SHORT).show();
                }
            });
        }
    }
}

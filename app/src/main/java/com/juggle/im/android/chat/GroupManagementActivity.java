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
import androidx.appcompat.app.AlertDialog;
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

public class GroupManagementActivity extends AbsAppActivity {
    private static final String EXTRA_GROUP_ID = "extra_group_id";
    private static final int REQ_CHANGE_OWNER = 2001;

    private static final int ROLE_OWNER = 1;
    private static final int ROLE_ADMIN = 2;

    private static final int SETTING_OWNER = 1;
    private static final int SETTING_ADMIN = 2;
    private static final int SETTING_MEMBER = 4;
    private static final int SETTING_ADMIN_OWNER = SETTING_OWNER | SETTING_ADMIN;
    private static final int SETTING_OWNER_MEMBER = SETTING_OWNER | SETTING_MEMBER;
    private static final int SETTING_ADMIN_MEMBER = SETTING_ADMIN | SETTING_MEMBER;
    private static final int SETTING_ALL = SETTING_OWNER | SETTING_ADMIN | SETTING_MEMBER;

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
        rowAddMember.setSubtitle("全部成员");
        rowTop.setSubtitle("全部成员");
        rowMention.setSubtitle("全部成员");
        rowEdit.setSubtitle("全部成员");
        rowChat.setSubtitle("全部成员");
        rowLife.setSubtitle("全部成员");

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
        rowAddMember.setOnRowClickListener(v -> showSettingRoleDialog("谁可以添加成员", KEY_ADD_MEMBER, management == null ? 0 : management.getGroupAddMemberRight(), rowAddMember));
        rowTop.setOnRowClickListener(v -> showSettingRoleDialog("谁可以置顶消息", KEY_TOP_MSG, management == null ? 0 : management.getGroupTopMsgRight(), rowTop));
        rowMention.setOnRowClickListener(v -> showSettingRoleDialog("谁可以 @ 所有人", KEY_MENTION_ALL, management == null ? 0 : management.getGroupMentionAllRight(), rowMention));
        rowEdit.setOnRowClickListener(v -> showSettingRoleDialog("谁可以编辑群消息", KEY_EDIT_MSG, management == null ? 0 : management.getGroupEditMsgRight(), rowEdit));
        rowChat.setOnRowClickListener(v -> showSettingRoleDialog("谁可以在群里发言", KEY_SEND_MSG, management == null ? 0 : management.getGroupSendMsgRight(), rowChat));
        rowLife.setOnRowClickListener(v -> showSettingRoleDialog("谁可以设置消息定时删除", KEY_SET_MSG_LIFE, management == null ? 0 : management.getGroupSetMsgLifeRight(), rowLife));

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
                .setTitle("解散群组")
                .setMessage("确认解散群组？")
                .setNegativeText(getString(R.string.txt_cancel))
                .setPositiveText(getString(R.string.create_group_confirm))
                .setOnPositiveClick(() -> ServiceManager.getUserService().dissolveGroup(groupId, new ApiCallback<Void>() {
                    @Override
                    public void onSuccess(Void data) {
                        Toast.makeText(GroupManagementActivity.this, "群组已解散", Toast.LENGTH_SHORT).show();
                        finish();
                    }

                    @Override
                    public void onError(int code, String message) {
                        Toast.makeText(GroupManagementActivity.this,
                                "解散失败：" + message,
                                Toast.LENGTH_SHORT).show();
                    }
                }))
                .show());
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
                    Toast.makeText(GroupManagementActivity.this,
                            "保存失败：" + message,
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
                Toast.makeText(GroupManagementActivity.this,
                        "加载群管理配置失败：" + message,
                        Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void renderManagement() {
        rowAddMember.setSubtitle(roleToText(management.getGroupAddMemberRight()));
        rowTop.setSubtitle(roleToText(management.getGroupTopMsgRight()));
        rowMention.setSubtitle(roleToText(management.getGroupMentionAllRight()));
        rowEdit.setSubtitle(roleToText(management.getGroupEditMsgRight()));
        rowChat.setSubtitle(roleToText(management.getGroupSendMsgRight()));
        rowLife.setSubtitle(roleToText(management.getGroupSetMsgLifeRight()));

        bindingHistorySwitch = true;
        rowHistory.setSwitchChecked(management.getHistoryMessageVisible() == 1);
        bindingHistorySwitch = false;

        boolean isOwner = groupDetail != null && groupDetail.getMyRole() == ROLE_OWNER;
        cardOwnerActions.setVisibility(isOwner ? View.VISIBLE : View.GONE);
        dissolveButton.setVisibility(isOwner ? View.VISIBLE : View.GONE);
    }

    private void showSettingRoleDialog(String title, String key, int currentValue, SettingRowView rowView) {
        int normalized = normalizeRole(currentValue);
        String[] labels = new String[]{"仅群主", "群主和管理员", "全部成员"};
        int[] values = new int[]{SETTING_OWNER, SETTING_ADMIN_OWNER, SETTING_ALL};

        int checkedIndex = 0;
        for (int i = 0; i < values.length; i++) {
            if (values[i] == normalized) {
                checkedIndex = i;
                break;
            }
        }

        final int[] selectedIndex = {checkedIndex};
        new AlertDialog.Builder(this)
                .setTitle(title)
                .setSingleChoiceItems(labels, checkedIndex, (dialog, which) -> selectedIndex[0] = which)
                .setNegativeButton(R.string.txt_cancel, null)
                .setPositiveButton("确定", (dialog, which) -> {
                    int selectedValue = values[selectedIndex[0]];
                    ServiceManager.getUserService().setGroupManagement(groupId, key, selectedValue, new ApiCallback<Void>() {
                        @Override
                        public void onSuccess(Void data) {
                            rowView.setSubtitle(labels[selectedIndex[0]]);
                            updateManagementValue(key, selectedValue);
                        }

                        @Override
                        public void onError(int code, String message) {
                            Toast.makeText(GroupManagementActivity.this,
                                    "保存失败：" + message,
                                    Toast.LENGTH_SHORT).show();
                        }
                    });
                })
                .show();
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

    private int normalizeRole(int value) {
        if (value == SETTING_OWNER || value == SETTING_ADMIN_OWNER || value == SETTING_ALL) {
            return value;
        }
        if (value == SETTING_OWNER_MEMBER) {
            return SETTING_OWNER;
        }
        if (value == SETTING_ADMIN_MEMBER) {
            return SETTING_ADMIN_OWNER;
        }
        if (value == SETTING_ADMIN) {
            return SETTING_ADMIN_OWNER;
        }
        if (value == SETTING_MEMBER) {
            return SETTING_ALL;
        }
        return SETTING_ALL;
    }

    private String roleToText(int role) {
        int normalized = normalizeRole(role);
        if (normalized == SETTING_OWNER) {
            return "仅群主";
        }
        if (normalized == SETTING_ADMIN_OWNER) {
            return "群主和管理员";
        }
        return "全部成员";
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
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
                    Toast.makeText(GroupManagementActivity.this, "已转让群主", Toast.LENGTH_SHORT).show();
                    finish();
                }

                @Override
                public void onError(int code, String message) {
                    Toast.makeText(GroupManagementActivity.this,
                            "转让失败：" + message,
                            Toast.LENGTH_SHORT).show();
                }
            });
        }
    }
}
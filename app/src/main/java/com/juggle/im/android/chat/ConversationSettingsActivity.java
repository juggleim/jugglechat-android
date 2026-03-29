package com.juggle.im.android.chat;

import static com.juggle.im.android.chat.ConversationActivity.EXTRA_CONVERSATION_ID;
import static com.juggle.im.android.chat.ConversationActivity.EXTRA_IS_GROUP;
import static com.juggle.im.android.chat.ConversationActivity.EXTRA_IS_MUTE;
import static com.juggle.im.android.chat.ConversationActivity.EXTRA_IS_TOP;
import static com.juggle.im.android.chat.ConversationActivity.EXTRA_TITLE;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.view.Window;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import com.juggle.im.android.component.AbsAppActivity;
import com.juggle.im.android.widget.JuggleSwitch;
import androidx.core.view.WindowInsetsControllerCompat;

import com.juggle.im.JIM;
import com.juggle.im.android.R;
import com.juggle.im.android.app.CreateGroupActivity;
import com.juggle.im.android.server.beans.GroupAnnouncementBean;
import com.juggle.im.android.server.beans.GroupDetailBean;
import com.juggle.im.android.server.beans.GroupMemberBean;
import com.juggle.im.android.server.http.ApiCallback;
import com.juggle.im.android.server.http.ServiceManager;
import com.juggle.im.android.utils.AvatarUtils;
import com.juggle.im.android.widget.AppConfirmDialog;
import com.juggle.im.model.Conversation;
import com.juggle.im.model.UserInfo;

import java.util.ArrayList;
import java.util.List;

/**
 * 群组设置主页（对应设计稿：群组详情）。
 */
public class ConversationSettingsActivity extends AbsAppActivity {
    private static final int REQ_ADD_MEMBER = 1000;
    private static final int ROLE_OWNER = 1;
    private static final int ROLE_ADMIN = 2;

    private String conversationId;
    private boolean isGroup;
    private boolean isTop;
    private boolean isMute;

    private GroupDetailBean groupDetail;
    private String announcementPreview = "";

    private ImageView ivAvatar;
    private TextView tvName;
    private TextView tvMeta;
    private LinearLayout previewMembers;
    private View cardGroupActions;
    private View btnQuitGroup;

    private ToolHolder topTool;
    private ToolHolder translateTool;
    private ToolHolder muteTool;
    private ToolHolder clearTool;

    private RowHolder announcementRow;
    private RowHolder addMemberRow;
    private RowHolder membersRow;
    private RowHolder displayNameRow;
    private RowHolder manageRow;
    private RowHolder qrcodeRow;
    private RowHolder groupInfoRow;
    private RowHolder reportRow;

    private final ArrayList<String> groupMemberIds = new ArrayList<>();

    public static Intent intentFor(Context ctx,
                                   String conversationId,
                                   String title,
                                   boolean isGroup,
                                   boolean isTop,
                                   boolean isMute) {
        Intent i = new Intent(ctx, ConversationSettingsActivity.class);
        i.putExtra(EXTRA_CONVERSATION_ID, conversationId);
        i.putExtra(EXTRA_IS_GROUP, isGroup);
        i.putExtra(EXTRA_IS_TOP, isTop);
        i.putExtra(EXTRA_IS_MUTE, isMute);
        i.putExtra(EXTRA_TITLE, title);
        return i;
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_conversation_settings);
        setupWindowStyle();

        conversationId = getIntent().getStringExtra(EXTRA_CONVERSATION_ID);
        isGroup = getIntent().getBooleanExtra(EXTRA_IS_GROUP, false);
        isTop = getIntent().getBooleanExtra(EXTRA_IS_TOP, false);
        isMute = getIntent().getBooleanExtra(EXTRA_IS_MUTE, false);

        initViews();
        bindToolbar();
        bindTools();
        bindRows();

        if (isGroup) {
            loadGroupInfo();
            loadGroupAnnouncement();
        } else {
            renderPrivateConversation();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (isGroup) {
            loadGroupInfo();
            loadGroupAnnouncement();
        }
    }

    private void setupWindowStyle() {
        Window window = getWindow();
        window.setStatusBarColor(getColor(R.color.white));
        window.setNavigationBarColor(getColor(R.color.white));
        WindowInsetsControllerCompat controller = new WindowInsetsControllerCompat(window, window.getDecorView());
        controller.setAppearanceLightStatusBars(true);
    }

    private void initViews() {
        ivAvatar = findViewById(R.id.iv_group_avatar);
        tvName = findViewById(R.id.tv_group_name);
        tvMeta = findViewById(R.id.tv_group_meta);
        previewMembers = findViewById(R.id.layout_member_preview);
        cardGroupActions = findViewById(R.id.card_group_actions);
        btnQuitGroup = findViewById(R.id.btn_quit_group);

        topTool = bindTool(R.id.tool_top, R.drawable.ic_setting_pin, "置顶");
        translateTool = bindTool(R.id.tool_translate, R.drawable.ic_translate, "翻译");
        muteTool = bindTool(R.id.tool_mute, R.drawable.ic_setting_mute, "免打扰");
        clearTool = bindTool(R.id.tool_clear, R.drawable.ic_clear_message, "清空消息");

        announcementRow = bindRow(R.id.row_group_announcement, R.drawable.ic_notification, "群公告");
        addMemberRow = bindRow(R.id.row_add_member, R.drawable.ic_add_member, "添加成员");
        membersRow = bindRow(R.id.row_group_members, R.drawable.ic_group_members, "群组成员");
        displayNameRow = bindRow(R.id.row_group_display_name, R.drawable.ic_display_name, "我在本群昵称");
        manageRow = bindRow(R.id.row_group_management, R.drawable.ic_group_manage, "群组管理");
        qrcodeRow = bindRow(R.id.row_group_qrcode, R.drawable.ic_qrcode, "群组二维码");
        groupInfoRow = bindRow(R.id.row_group_info, R.drawable.ic_word, "群组信息");
        reportRow = bindRow(R.id.row_report, R.drawable.ic_report, "举报");
        announcementRow.divider.setVisibility(View.GONE);
        displayNameRow.divider.setVisibility(View.GONE);
        qrcodeRow.divider.setVisibility(View.GONE);
        reportRow.divider.setVisibility(View.GONE);
        groupInfoRow.root.setVisibility(View.GONE);

        updateToolStates();
    }

    private void bindToolbar() {
        TextView titleView = findViewById(R.id.tv_page_title);
        ImageView editView = findViewById(R.id.iv_edit_group);
        String title = getIntent().getStringExtra(EXTRA_TITLE);
        if (isGroup) {
            titleView.setText("");
        } else {
            titleView.setText(TextUtils.isEmpty(title)
                    ? getString(R.string.conversation_setting)
                    : title);
        }

        findViewById(R.id.iv_back).setOnClickListener(v -> finish());
        editView.setVisibility(isGroup ? View.VISIBLE : View.GONE);
        editView.setOnClickListener(v -> {
            if (!isGroup) {
                return;
            }
            startActivity(GroupInfoActivity.intentFor(this, conversationId));
        });
    }

    @SuppressLint("SetTextI18n")
    private void bindTools() {
        topTool.root.setOnClickListener(v -> {
            Conversation conversation = getConversation();
            JIM.getInstance().getConversationManager().setTop(conversation, !isTop, null);
            isTop = !isTop;
            updateToolStates();
            Toast.makeText(this, isTop ? R.string.settings_top_on : R.string.settings_top_off, Toast.LENGTH_SHORT).show();
        });

        muteTool.root.setOnClickListener(v -> {
            Conversation conversation = getConversation();
            JIM.getInstance().getConversationManager().setMute(conversation, !isMute, null);
            isMute = !isMute;
            updateToolStates();
            Toast.makeText(this, isMute ? R.string.settings_mute_on : R.string.settings_mute_off, Toast.LENGTH_SHORT).show();
        });

        translateTool.root.setOnClickListener(v ->
                Toast.makeText(this, "翻译功能暂未接入", Toast.LENGTH_SHORT).show());

        clearTool.root.setOnClickListener(v -> {
            JIM.getInstance().getMessageManager().clearMessages(getConversation(), 0, null);
            Toast.makeText(this, R.string.chat_cleared, Toast.LENGTH_SHORT).show();
        });
    }

    private void bindRows() {
        announcementRow.root.setOnClickListener(v -> {
            if (!isGroup) {
                return;
            }
            startActivity(GroupAnnouncementActivity.intentFor(
                    this,
                    conversationId,
                    announcementPreview,
                    isGroupAdmin()));
        });

        addMemberRow.root.setOnClickListener(v -> {
            if (!isGroup) {
                return;
            }
            Intent intent = CreateGroupActivity.newIntent(
                    this,
                    CreateGroupActivity.MODE_ADD_MEMBER,
                    conversationId,
                    groupMemberIds);
            startActivityForResult(intent, REQ_ADD_MEMBER);
        });

        membersRow.root.setOnClickListener(v -> {
            if (!isGroup) {
                return;
            }
            String groupName = tvName.getText() == null ? "" : tvName.getText().toString();
            int myRole = groupDetail == null ? 0 : groupDetail.getMyRole();
            startActivity(GroupMembersActivity.intentFor(this, conversationId, groupName, myRole));
        });

        displayNameRow.root.setOnClickListener(v -> {
            if (!isGroup) {
                return;
            }
            showEditDisplayNameDialog();
        });

        manageRow.root.setOnClickListener(v -> {
            if (!isGroup) {
                return;
            }
            startActivity(GroupManagementActivity.intentFor(this, conversationId));
        });

        qrcodeRow.root.setOnClickListener(v ->
                startActivity(GroupQrcodeActivity.intentFor(
                        this,
                        conversationId,
                        tvName.getText() == null ? "" : tvName.getText().toString(),
                        groupDetail == null ? "" : groupDetail.getPortrait())));

        groupInfoRow.root.setOnClickListener(v -> {
            if (!isGroup) {
                return;
            }
            startActivity(GroupInfoActivity.intentFor(this, conversationId));
        });

        reportRow.root.setOnClickListener(v -> showReportDialog());

        btnQuitGroup.setOnClickListener(v -> onQuitOrDissolveGroup());
    }

    private void renderPrivateConversation() {
        cardGroupActions.setVisibility(View.GONE);
        findViewById(R.id.card_announcement).setVisibility(View.GONE);
        findViewById(R.id.card_group_manage).setVisibility(View.GONE);
        btnQuitGroup.setVisibility(View.GONE);

        announcementRow.root.setVisibility(View.GONE);
        displayNameRow.root.setVisibility(View.GONE);
        addMemberRow.root.setVisibility(View.GONE);
        membersRow.root.setVisibility(View.GONE);
        manageRow.root.setVisibility(View.GONE);
        qrcodeRow.root.setVisibility(View.GONE);
        groupInfoRow.root.setVisibility(View.GONE);

        UserInfo userInfo = JIM.getInstance().getUserInfoManager().getUserInfo(conversationId);
        String title = getIntent().getStringExtra(EXTRA_TITLE);
        String displayName = TextUtils.isEmpty(title)
                ? (userInfo == null ? "" : userInfo.getUserName())
                : title;
        String portrait = userInfo == null ? "" : userInfo.getPortrait();

        AvatarUtils.loadAvatar(ivAvatar, portrait, displayName, conversationId);
        tvName.setText(displayName);
        tvMeta.setText("私信");
        previewMembers.setVisibility(View.GONE);
        findViewById(R.id.iv_edit_group).setVisibility(View.GONE);
    }

    private void loadGroupInfo() {
        ServiceManager.getUserService().getGroupInfo(conversationId, new ApiCallback<GroupDetailBean>() {
            @Override
            public void onSuccess(GroupDetailBean data) {
                groupDetail = data;
                if (data == null) {
                    return;
                }

                String groupName = safeText(data.getGroupName(), conversationId);
                AvatarUtils.loadAvatar(ivAvatar, data.getPortrait(), groupName, data.getGroupId());
                tvName.setText(groupName);

                int memberCount = data.getMemberCount();
                if (memberCount <= 0 && data.getMembers() != null) {
                    memberCount = data.getMembers().size();
                }
                tvMeta.setText(memberCount + "个成员");

                groupMemberIds.clear();
                bindMemberPreview(data.getMembers());
                displayNameRow.subtitle.setText(safeText(data.getGroupDisplayName(), "未设置"));

                updateQuitButtonText();
            }

            @Override
            public void onError(int code, String message) {
                Toast.makeText(ConversationSettingsActivity.this,
                        "群信息加载失败：" + message,
                        Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void loadGroupAnnouncement() {
        ServiceManager.getUserService().getGroupAnnouncement(conversationId, new ApiCallback<GroupAnnouncementBean>() {
            @Override
            public void onSuccess(GroupAnnouncementBean data) {
                announcementPreview = data == null ? "" : safeText(data.getContent(), "");
                if (TextUtils.isEmpty(announcementPreview)) {
                    announcementRow.subtitle.setText("未设置");
                } else {
                    announcementRow.subtitle.setText(announcementPreview.replace('\n', ' '));
                }
            }

            @Override
            public void onError(int code, String message) {
                announcementRow.subtitle.setText("未设置");
            }
        });
    }

    private void bindMemberPreview(@Nullable List<GroupMemberBean> members) {
        previewMembers.removeAllViews();
        if (members == null || members.isEmpty()) {
            previewMembers.setVisibility(View.GONE);
            return;
        }
        previewMembers.setVisibility(View.GONE);
        int size = members.size();
        for (int i = 0; i < size; i++) {
            GroupMemberBean member = members.get(i);
            groupMemberIds.add(member.getUserId());
        }
    }

    private void showReportDialog() {
        if (isGroup) {
            showGroupReportDialog();
            return;
        }
        ServiceManager.getUserService().submitFeedback(
                "举报投诉",
                conversationId,
                new ArrayList<>(),
                new ArrayList<>(),
                new ApiCallback<Void>() {
                    @Override
                    public void onSuccess(Void data) {
                        Toast.makeText(ConversationSettingsActivity.this, "已提交举报", Toast.LENGTH_SHORT).show();
                    }

                    @Override
                    public void onError(int code, String message) {
                        Toast.makeText(ConversationSettingsActivity.this,
                                "举报失败：" + message,
                                Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void showGroupReportDialog() {
        EditText input = new EditText(this);
        input.setSingleLine(false);
        input.setMinLines(3);
        input.setHint("请输入举报原因（选填）");
        input.setPadding(dp(16), dp(12), dp(16), dp(12));

        new AlertDialog.Builder(this)
                .setTitle("举报投诉")
                .setView(input)
                .setNegativeButton(R.string.txt_cancel, null)
                .setPositiveButton("提交", (dialog, which) -> {
                    String reason = input.getText() == null ? "" : input.getText().toString().trim();
                    String reportText = TextUtils.isEmpty(reason)
                            ? conversationId
                            : conversationId + "|" + reason;
                    ServiceManager.getUserService().submitFeedback(
                            "举报投诉",
                            reportText,
                            new ArrayList<>(),
                            new ArrayList<>(),
                            new ApiCallback<Void>() {
                                @Override
                                public void onSuccess(Void data) {
                                    Toast.makeText(ConversationSettingsActivity.this,
                                            "已提交举报",
                                            Toast.LENGTH_SHORT).show();
                                }

                                @Override
                                public void onError(int code, String message) {
                                    Toast.makeText(ConversationSettingsActivity.this,
                                            "举报失败：" + message,
                                            Toast.LENGTH_SHORT).show();
                                }
                            });
                })
                .show();
    }

    private void showEditDisplayNameDialog() {
        EditText input = new EditText(this);
        input.setSingleLine();
        input.setPadding(dp(16), dp(12), dp(16), dp(12));
        String current = displayNameRow.subtitle.getText() == null ? "" : displayNameRow.subtitle.getText().toString();
        if (!"未设置".contentEquals(current)) {
            input.setText(current);
            input.setSelection(input.getText().length());
        }

        new AlertDialog.Builder(this)
                .setTitle("我在本群昵称")
                .setView(input)
                .setNegativeButton(R.string.txt_cancel, null)
                .setPositiveButton(R.string.send, (dialog, which) -> {
                    String newName = input.getText() == null ? "" : input.getText().toString().trim();
                    ServiceManager.getUserService().setGroupDisplayName(conversationId, newName, new ApiCallback<Void>() {
                        @Override
                        public void onSuccess(Void data) {
                            String display = TextUtils.isEmpty(newName) ? "未设置" : newName;
                            displayNameRow.subtitle.setText(display);
                            Toast.makeText(ConversationSettingsActivity.this, "保存成功", Toast.LENGTH_SHORT).show();
                        }

                        @Override
                        public void onError(int code, String message) {
                            Toast.makeText(ConversationSettingsActivity.this,
                                    "保存失败：" + message,
                                    Toast.LENGTH_SHORT).show();
                        }
                    });
                })
                .show();
    }

    private void onQuitOrDissolveGroup() {
        if (!isGroup) {
            return;
        }
        boolean isOwner = groupDetail != null && groupDetail.getMyRole() == ROLE_OWNER;
        String actionName = isOwner ? "解散群组" : "退出群组";

        AppConfirmDialog.builder(this)
                .setTitle(actionName)
                .setMessage("确认" + actionName + "？")
                .setNegativeText(getString(R.string.txt_cancel))
                .setPositiveText(getString(R.string.create_group_confirm))
                .setOnPositiveClick(() -> {
                    ApiCallback<Void> callback = new ApiCallback<Void>() {
                        @Override
                        public void onSuccess(Void data) {
                            Toast.makeText(ConversationSettingsActivity.this,
                                    actionName + "成功",
                                    Toast.LENGTH_SHORT).show();
                            finish();
                        }

                        @Override
                        public void onError(int code, String message) {
                            Toast.makeText(ConversationSettingsActivity.this,
                                    actionName + "失败：" + message,
                                    Toast.LENGTH_SHORT).show();
                        }
                    };

                    if (isOwner) {
                        ServiceManager.getUserService().dissolveGroup(conversationId, callback);
                    } else {
                        ServiceManager.getUserService().quitGroup(conversationId, callback);
                    }
                })
                .show();
    }

    private void updateQuitButtonText() {
        TextView quitView = (TextView) btnQuitGroup;
        if (groupDetail != null && groupDetail.getMyRole() == ROLE_OWNER) {
            quitView.setText("解散群组");
        } else {
            quitView.setText("退出群组");
        }
    }

    private void updateToolStates() {
        topTool.title.setText(isTop ? "取消置顶" : "置顶");
        muteTool.title.setText(isMute ? "取消免打扰" : "免打扰");
    }

    private Conversation getConversation() {
        Conversation.ConversationType type = isGroup
                ? Conversation.ConversationType.GROUP
                : Conversation.ConversationType.PRIVATE;
        return new Conversation(type, conversationId);
    }

    private boolean isGroupAdmin() {
        if (groupDetail == null) {
            return false;
        }
        int role = groupDetail.getMyRole();
        return role == ROLE_OWNER || role == ROLE_ADMIN;
    }

    private ToolHolder bindTool(int includeId, int iconRes, String title) {
        View root = findViewById(includeId);
        ImageView icon = root.findViewById(R.id.iv_tool_icon);
        TextView label = root.findViewById(R.id.tv_tool_title);
        icon.setImageResource(iconRes);
        label.setText(title);
        ToolHolder holder = new ToolHolder();
        holder.root = root;
        holder.icon = icon;
        holder.title = label;
        return holder;
    }

    private RowHolder bindRow(int includeId, int iconRes, String title) {
        View root = findViewById(includeId);
        ImageView icon = root.findViewById(R.id.iv_row_icon);
        TextView titleView = root.findViewById(R.id.tv_row_title);
        TextView subtitleView = root.findViewById(R.id.tv_row_subtitle);
        ImageView arrowView = root.findViewById(R.id.iv_row_arrow);
        JuggleSwitch switchView = root.findViewById(R.id.switch_row);
        View dividerView = root.findViewById(R.id.row_divider);

        icon.setImageResource(iconRes);
        titleView.setText(title);
        subtitleView.setText("");
        subtitleView.setVisibility(View.VISIBLE);
        arrowView.setVisibility(View.VISIBLE);
        switchView.setVisibility(View.GONE);

        RowHolder holder = new RowHolder();
        holder.root = root;
        holder.icon = icon;
        holder.title = titleView;
        holder.subtitle = subtitleView;
        holder.arrow = arrowView;
        holder.switchCompat = switchView;
        holder.divider = dividerView;
        return holder;
    }

    private String safeText(String text, String fallback) {
        if (TextUtils.isEmpty(text)) {
            return fallback;
        }
        return text;
    }

    private int dp(int value) {
        return (int) (getResources().getDisplayMetrics().density * value + 0.5f);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_ADD_MEMBER && resultCode == RESULT_OK) {
            // CreateGroupActivity 已在内部处理邀请逻辑，这里只需刷新群组信息
            loadGroupInfo();
        }
    }

    private static class ToolHolder {
        View root;
        ImageView icon;
        TextView title;
    }

    private static class RowHolder {
        View root;
        ImageView icon;
        TextView title;
        TextView subtitle;
        ImageView arrow;
        JuggleSwitch switchCompat;
        View divider;
    }
}

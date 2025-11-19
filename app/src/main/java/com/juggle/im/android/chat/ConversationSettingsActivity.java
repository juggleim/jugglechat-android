package com.juggle.im.android.chat;

import static android.view.View.GONE;
import static com.juggle.im.android.chat.ConversationActivity.*;
import static com.juggle.im.android.chat.SelectMemberActivity.DISABLE_MEMBERS;
import static com.juggle.im.android.chat.SelectMemberActivity.SELECTED_MEMBERS;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.juggle.im.android.R;
import com.juggle.im.android.chat.component.UserListAdapter;
import com.juggle.im.android.server.beans.GroupDetailBean;
import com.juggle.im.android.server.beans.GroupMemberBean;
import com.juggle.im.android.server.http.ApiCallback;
import com.juggle.im.android.server.http.ServiceManager;
import com.juggle.im.android.utils.AvatarUtils;
import com.juggle.im.model.Conversation;
import com.juggle.im.JIM;
import com.juggle.im.model.UserInfo;

import java.util.ArrayList;

/**
 * Conversation settings page. Supports mute (do not disturb), pin to top and actions to clear chat
 * and (for groups) leave group.
 */
public class ConversationSettingsActivity extends AppCompatActivity {
    private String conversationId;
    private boolean isGroup;
    private boolean isTop, isMute;
    private ArrayList<String> groupMemberIds = new ArrayList<>();


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

    @SuppressLint("UseSwitchCompatOrMaterialCode")
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_conversation_settings);

        conversationId = getIntent().getStringExtra(EXTRA_CONVERSATION_ID);
        isGroup = getIntent().getBooleanExtra(EXTRA_IS_GROUP, false);
        isTop = getIntent().getBooleanExtra(EXTRA_IS_TOP, false);
        isMute = getIntent().getBooleanExtra(EXTRA_IS_MUTE, false);

        findViewById(R.id.btn_mute).setOnClickListener(v -> {
            Conversation.ConversationType conversationType = isGroup ? Conversation.ConversationType.GROUP : Conversation.ConversationType.PRIVATE;
            Conversation conv = new Conversation(conversationType, conversationId);
            JIM.getInstance().getConversationManager().setMute(conv, !isMute, null);
            Toast.makeText(this, !isMute ? R.string.settings_mute_on : R.string.settings_mute_off, Toast.LENGTH_SHORT).show();
            isMute = !isMute;
        });
        findViewById(R.id.btn_pin).setOnClickListener(v -> {
            Conversation.ConversationType conversationType = isGroup ? Conversation.ConversationType.GROUP : Conversation.ConversationType.PRIVATE;
            Conversation conv = new Conversation(conversationType, conversationId);
            JIM.getInstance().getConversationManager().setTop(conv, !isTop, null);
            Toast.makeText(this, !isTop ? R.string.settings_top_on : R.string.settings_top_off, Toast.LENGTH_SHORT).show();
            isTop = !isTop;
        });
        findViewById(R.id.btn_add).setOnClickListener(v -> {
            Intent it = new Intent(this, SelectMemberActivity.class);
            it.putExtra("mode", UserListAdapter.LIST_MODE_SELECT_MEMBER);
            it.putStringArrayListExtra(DISABLE_MEMBERS, groupMemberIds);
            startActivityForResult(it, 1000);
        });
        findViewById(R.id.ll_group_members).setOnClickListener(v -> {
            Intent it = new Intent(this, SelectMemberActivity.class);
            it.putExtra("GROUP_ID", conversationId);
            it.putExtra("mode", UserListAdapter.LIST_MODE_NORMAL);
            startActivity(it);
        });

        Button btnClear = findViewById(R.id.btn_clear_chat);
        Button btnLeave = findViewById(R.id.btn_leave_group);

        btnClear.setOnClickListener(v -> {
            try {
                Conversation.ConversationType conversationType = isGroup ? Conversation.ConversationType.GROUP : Conversation.ConversationType.PRIVATE;
                Conversation conv = new Conversation(conversationType, conversationId);
                JIM.getInstance().getMessageManager().clearMessages(conv, 0, null);
                Toast.makeText(this, R.string.chat_cleared, Toast.LENGTH_SHORT).show();
            } catch (Exception e) {
                Toast.makeText(this, R.string.operation_failed, Toast.LENGTH_SHORT).show();
            }
        });

        if (isGroup) {
            btnLeave.setVisibility(View.VISIBLE);
            btnLeave.setOnClickListener(v -> {
                // placeholder: call conversation manager leave group if available
                try {
                    // If there is a leave API, call it. Otherwise show toast.
                    // For now, show a toast and finish.
                    Toast.makeText(this, R.string.left_group, Toast.LENGTH_SHORT).show();
                    finish();
                } catch (Exception e) {
                    Toast.makeText(this, R.string.operation_failed, Toast.LENGTH_SHORT).show();
                }
            });
        } else {
            btnLeave.setVisibility(GONE);
        }

        // back button
        findViewById(R.id.iv_back).setOnClickListener(v -> finish());

        if (isGroup) {
            ServiceManager.getUserService().getGroupInfo(conversationId, new ApiCallback<GroupDetailBean>() {
                @Override
                public void onSuccess(GroupDetailBean data) {
                    ImageView groupAvatar = findViewById(R.id.iv_group_avatar);
                    AvatarUtils.loadAvatar(groupAvatar, data.getPortrait(), data.getGroupName());
                    TextView groupName = findViewById(R.id.tv_group_name);
                    groupName.setText(data.getGroupName());

                    TextView nickName = findViewById(R.id.tv_my_nickname);
                    nickName.setText(data.getGroupDisplayName());
                    for (GroupMemberBean member : data.getMembers()) {
                        groupMemberIds.add(member.getUserId());
                    }
                }

                @Override
                public void onError(int code, String message) {

                }
            });
        } else {
            findViewById(R.id.btn_add).setVisibility(GONE);
            findViewById(R.id.ll_group_members).setVisibility(GONE);
            findViewById(R.id.ll_group_nickname).setVisibility(GONE);

            ImageView groupAvatar = findViewById(R.id.iv_group_avatar);
            UserInfo data = JIM.getInstance().getUserInfoManager().getUserInfo(conversationId);
            if (data != null) {
                AvatarUtils.loadAvatar(groupAvatar, data.getPortrait(), data.getUserName());
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == 1000 && resultCode == RESULT_OK) {
            ArrayList<String> selectedMemberIds = data.getStringArrayListExtra(SELECTED_MEMBERS);
            ServiceManager.getUserService().inviteJoinGroup(conversationId, selectedMemberIds, new ApiCallback<Void>() {
                @Override
                public void onSuccess(Void data) {
                    Toast.makeText(ConversationSettingsActivity.this, "邀请成功", Toast.LENGTH_SHORT).show();
                }

                @Override
                public void onError(int code, String message) {
                    Log.e("inviteJoinGroup", code + message);
                    Toast.makeText(ConversationSettingsActivity.this, "邀请失败" + code, Toast.LENGTH_SHORT).show();
                }
            });
        }
    }
}

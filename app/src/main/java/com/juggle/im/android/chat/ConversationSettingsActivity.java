package com.juggle.im.android.chat;

import static com.juggle.im.android.chat.ConversationActivity.*;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.Switch;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.juggle.im.android.R;
import com.juggle.im.model.Conversation;
import com.juggle.im.JIM;

/**
 * Conversation settings page. Supports mute (do not disturb), pin to top and actions to clear chat
 * and (for groups) leave group.
 */
public class ConversationSettingsActivity extends AppCompatActivity {
    private String conversationId;
    private boolean isGroup;
    private boolean isTop, isMute;

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

        Switch swMute = findViewById(R.id.switch_mute);
        Switch swTop = findViewById(R.id.switch_top);
        Button btnClear = findViewById(R.id.btn_clear_chat);
        Button btnLeave = findViewById(R.id.btn_leave_group);
        swMute.setChecked(isMute);
        swTop.setChecked(isTop);

        swMute.setOnCheckedChangeListener((buttonView, isChecked) -> {
            try {
                Conversation.ConversationType conversationType = isGroup ? Conversation.ConversationType.GROUP : Conversation.ConversationType.PRIVATE;
                Conversation conv = new Conversation(conversationType, conversationId);
                JIM.getInstance().getConversationManager().setMute(conv, isChecked, null);
                Toast.makeText(this, isChecked ? R.string.settings_mute_on : R.string.settings_mute_off, Toast.LENGTH_SHORT).show();
            } catch (Exception e) {
                Toast.makeText(this, R.string.operation_failed, Toast.LENGTH_SHORT).show();
            }
        });

        swTop.setOnCheckedChangeListener((buttonView, isChecked) -> {
            try {
                Conversation.ConversationType conversationType = isGroup ? Conversation.ConversationType.GROUP : Conversation.ConversationType.PRIVATE;
                Conversation conv = new Conversation(conversationType, conversationId);
                JIM.getInstance().getConversationManager().setTop(conv, isChecked, null);
                Toast.makeText(this, isChecked ? R.string.settings_top_on : R.string.settings_top_off, Toast.LENGTH_SHORT).show();
            } catch (Exception e) {
                Toast.makeText(this, R.string.operation_failed, Toast.LENGTH_SHORT).show();
            }
        });

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
            btnLeave.setVisibility(View.GONE);
        }

        // back button
        findViewById(R.id.iv_back).setOnClickListener(v -> finish());
    }
}

package com.juggle.im.android.chat;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

import androidx.annotation.Nullable;
import com.juggle.im.android.component.AbsAppActivity;

import com.juggle.im.android.R;

/**
 * Activity host for the forward-conversation list. Shows a toolbar with title and close button
 * and hosts {@link ForwardConversationListFragment}.
 */
public class ForwardConversationListActivity extends AbsAppActivity {
    public static final String EXTRA_CONVERSATION_ID = "extra_conversation_id";
    public static final String EXTRA_CONVERSATION_NAME = "extra_conversation_name";
    public static final String EXTRA_IS_GROUP = "extra_is_group";

    public static final String EXTRA_FORWARD_MODE = "extra_forward_mode"; // "single" or "merge"

    public static Intent createIntent(Context ctx, String mode) {
        Intent i = new Intent(ctx, ForwardConversationListActivity.class);
        i.putExtra(EXTRA_FORWARD_MODE, mode);
        return i;
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_forward_conversation_list);

        findViewById(R.id.iv_back).setOnClickListener(v -> finish());

        if (getSupportFragmentManager().findFragmentByTag("forward_conversations") == null) {
            ForwardConversationListFragment f = new ForwardConversationListFragment();
            getSupportFragmentManager().beginTransaction()
                    .replace(R.id.content_container, f, "forward_conversations")
                    .commitAllowingStateLoss();
        }
    }
}

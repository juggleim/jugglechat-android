package com.juggle.im.android.chat;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.Nullable;
import com.juggle.im.android.component.AbsAppActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.juggle.im.JIM;
import com.juggle.im.android.R;
import com.juggle.im.android.model.UiMessage;
import com.juggle.im.interfaces.IMessageManager;
import com.juggle.im.model.Message;


import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class MergeMessageActivity extends AbsAppActivity {
    public static final String EXTRA_MESSAGE_ID = "mergeMsg";

    public static void start(Context ctx, String mergeMsgId) {
        Intent i = new Intent(ctx, MergeMessageActivity.class);
        i.putExtra(EXTRA_MESSAGE_ID, mergeMsgId);
        ctx.startActivity(i);
    }

    private RecyclerView recyclerView;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_merge_message);

        // Title bar
        TextView tvTitle = findViewById(R.id.merge_title);
        ImageView btnBack = findViewById(R.id.iv_back);
        btnBack.setOnClickListener(v -> finish());
        recyclerView = findViewById(R.id.merge_recycler);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        final MessageListAdapter adapter = new MessageListAdapter(true);

        String mergeMsgId = getIntent().getStringExtra(EXTRA_MESSAGE_ID);
        JIM.getInstance().getMessageManager().getMergedMessageList(mergeMsgId, new IMessageManager.IGetMessagesCallback() {
            @Override
            public void onSuccess(List<Message> list) {
                List<UiMessage> uiMessages = new ArrayList<>();
                for (Message message : list) {
                    uiMessages.add(UiMessage.fromMessage(message));
                }
                Collections.reverse(uiMessages);
                adapter.submitList(uiMessages);
            }

            @Override
            public void onError(int i) {
                Log.e("MergeMessageActivity", "onError: " + i);
            }
        });
        recyclerView.setAdapter(adapter);

        tvTitle.setText(R.string.history_messages);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        return super.onOptionsItemSelected(item);
    }
}

package com.juggle.im.android.chat;

import android.content.Intent;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;

import com.juggle.im.JIM;
import com.juggle.im.android.R;
import com.juggle.im.android.core.JIMChatCore;
import com.juggle.im.android.model.UiConversation;
import com.juggle.im.model.Conversation;

import java.util.List;

/**
 * Conversation list as a Fragment so it can be hosted inside MainActivity.
 */
public class ConversationListFragment extends Fragment implements ConversationListAdapter.OnConversationClickListener {
    private RecyclerView conversationListView;
    private ConversationListAdapter conversationListAdapter;
    private EditText edtSearch;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_conversation_list, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        conversationListView = view.findViewById(R.id.rv_conversation_list);
        edtSearch = view.findViewById(R.id.edt_search);

        conversationListAdapter = new ConversationListAdapter();
        conversationListAdapter.setOnConversationClickListener(this);
        conversationListView.setLayoutManager(new LinearLayoutManager(requireContext()));
        conversationListView.setAdapter(conversationListAdapter);
    }

    public void upsertConversations(List<UiConversation> dataSet) {
        conversationListAdapter.upsertConversations(dataSet);
    }

    @Override
    public void onConversationClick(UiConversation uiConversation) {
        JIM.getInstance().getConversationManager().clearUnreadCount(uiConversation.getConversationInfo().getConversation(), null);
        Intent intent = ConversationActivity.intentFor(this.getActivity(),
                uiConversation.getConversationInfo().getConversation().getConversationId(),
                uiConversation.getName(),
                uiConversation.getConversationInfo().getConversation().getConversationType().equals(Conversation.ConversationType.GROUP),
                uiConversation.isTop(),
                uiConversation.isMuted());
        startActivity(intent);
    }
}

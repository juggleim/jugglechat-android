package com.juggle.im.android.chat;

import com.juggle.im.android.chat.state.ConversationListReducer;
import com.juggle.im.android.model.UiConversation;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ConversationListReducerTest {

    @Test
    public void shouldKeepIncrementalUpsertOrderStable() {
        ConversationListReducer reducer = new ConversationListReducer();
        ConversationListReducer.ConversationListState state = ConversationListReducer.ConversationListState.initial();

        state = reducer.reduce(state, new ConversationListReducer.ConversationsMerged(Arrays.asList(
                conversation("normal-old", false, 100L),
                conversation("top-1", true, 50L)
        )));

        state = reducer.reduce(state, new ConversationListReducer.ConversationsMerged(Collections.singletonList(
                conversation("normal-new", false, 200L)
        )));

        assertEquals(3, state.getConversations().size());
        assertEquals("top-1", state.getConversations().get(0).getId());
        assertEquals("normal-new", state.getConversations().get(1).getId());
        assertEquals("normal-old", state.getConversations().get(2).getId());
    }

    @Test
    public void shouldUpdatePagingFlagsAfterLoadMore() {
        ConversationListReducer reducer = new ConversationListReducer();
        ConversationListReducer.ConversationListState state = ConversationListReducer.ConversationListState.initial();

        state = reducer.reduce(state, new ConversationListReducer.LoadMoreStarted());
        assertTrue(state.isLoadingMore());

        state = reducer.reduce(state, new ConversationListReducer.LoadMoreSucceeded(
                Collections.singletonList(conversation("c-1", false, 100L)),
                20));

        assertFalse(state.isLoadingMore());
        assertFalse(state.hasMore());
        assertEquals(100L, state.getCursor());
    }

    private UiConversation conversation(String id, boolean isTop, long sortTime) {
        UiConversation conversation = new UiConversation();
        conversation.setId(id);
        conversation.setName(id);
        conversation.setTop(isTop);
        conversation.setSortTime(sortTime);
        return conversation;
    }
}

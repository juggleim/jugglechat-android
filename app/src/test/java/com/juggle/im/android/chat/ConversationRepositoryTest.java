package com.juggle.im.android.chat;

import com.juggle.im.android.chat.domain.ConversationRepository;
import com.juggle.im.android.model.UiConversation;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class ConversationRepositoryTest {

    @Test
    public void shouldKeepTopConversationAheadAndSortBySortTimeDesc() {
        ConversationRepository repository = new ConversationRepository();

        List<UiConversation> current = Arrays.asList(
                conversation("c-old", false, 100L, 1),
                conversation("c-top", true, 10L, 0)
        );
        List<UiConversation> incoming = Collections.singletonList(
                conversation("c-new", false, 200L, 2)
        );

        List<UiConversation> merged = repository.mergeSnapshot(current, incoming);

        assertEquals(3, merged.size());
        assertEquals("c-top", merged.get(0).getId());
        assertEquals("c-new", merged.get(1).getId());
        assertEquals("c-old", merged.get(2).getId());
    }

    @Test
    public void shouldUpsertByConversationIdAndKeepLatestSnapshot() {
        ConversationRepository repository = new ConversationRepository();

        List<UiConversation> current = Collections.singletonList(conversation("c-1", false, 100L, 1));
        List<UiConversation> incoming = Collections.singletonList(conversation("c-1", true, 300L, 8));

        List<UiConversation> merged = repository.mergeSnapshot(current, incoming);

        assertEquals(1, merged.size());
        UiConversation result = merged.get(0);
        assertEquals("c-1", result.getId());
        assertTrue(result.isTop());
        assertEquals(300L, result.getSortTime());
        assertEquals(8, result.getUnreadCount());
    }

    private UiConversation conversation(String id, boolean isTop, long sortTime, int unreadCount) {
        UiConversation conversation = new UiConversation();
        conversation.setId(id);
        conversation.setName(id);
        conversation.setTop(isTop);
        conversation.setSortTime(sortTime);
        conversation.setUnreadCount(unreadCount);
        return conversation;
    }
}

package com.juggle.im.android.chat.call;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class RemoteCallTerminationPolicyTest {
    private static final String CURRENT_USER_ID = "android-user";
    private static final String REMOTE_USER_ID = "ios-user";

    @Test
    public void finishesWhenLastRemoteUserLeaves() {
        assertTrue(RemoteCallTerminationPolicy.shouldFinishAfterRemoteLeave(
                CURRENT_USER_ID,
                Collections.singletonList(REMOTE_USER_ID),
                Collections.emptyList()));
    }

    @Test
    public void finishesWhenOnlyCurrentUserRemainsInSnapshot() {
        assertTrue(RemoteCallTerminationPolicy.shouldFinishAfterRemoteLeave(
                CURRENT_USER_ID,
                Collections.singletonList(REMOTE_USER_ID),
                Collections.singletonList(CURRENT_USER_ID)));
    }

    @Test
    public void keepsCallWhenAnotherRemoteUserRemains() {
        assertFalse(RemoteCallTerminationPolicy.shouldFinishAfterRemoteLeave(
                CURRENT_USER_ID,
                Collections.singletonList(REMOTE_USER_ID),
                Arrays.asList(CURRENT_USER_ID, "another-remote-user")));
    }

    @Test
    public void ignoresCurrentUserOrEmptyLeaveCallbacks() {
        assertFalse(RemoteCallTerminationPolicy.shouldFinishAfterRemoteLeave(
                CURRENT_USER_ID,
                Collections.singletonList(CURRENT_USER_ID),
                Collections.emptyList()));
        assertFalse(RemoteCallTerminationPolicy.shouldFinishAfterRemoteLeave(
                CURRENT_USER_ID,
                Collections.emptyList(),
                Collections.emptyList()));
    }
}

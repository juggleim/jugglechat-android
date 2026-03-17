package com.juggle.im.android.chat;

import android.content.pm.PackageManager;

import com.juggle.im.android.chat.view.ChatInputActionBar;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class InputPluginPermissionDispatchTest {

    @Test
    public void shouldReturnGrantedResultWhenAllPermissionsGranted() {
        ChatInputActionBar.PluginPermissionDispatcher dispatcher =
                new ChatInputActionBar.PluginPermissionDispatcher();
        dispatcher.registerPendingRequest(12001, "photo", new String[]{"p1", "p2"});

        ChatInputActionBar.PluginPermissionDispatcher.PermissionDispatchResult result =
                dispatcher.consumeResult(12001, new int[]{
                        PackageManager.PERMISSION_GRANTED,
                        PackageManager.PERMISSION_GRANTED
                });

        assertNotNull(result);
        assertEquals("photo", result.getPluginId());
        assertTrue(result.isGranted());
        assertEquals(2, result.getPermissions().length);
    }

    @Test
    public void shouldReturnDeniedResultWhenAnyPermissionDenied() {
        ChatInputActionBar.PluginPermissionDispatcher dispatcher =
                new ChatInputActionBar.PluginPermissionDispatcher();
        dispatcher.registerPendingRequest(12007, "call_video", new String[]{"camera", "mic"});

        ChatInputActionBar.PluginPermissionDispatcher.PermissionDispatchResult result =
                dispatcher.consumeResult(12007, new int[]{
                        PackageManager.PERMISSION_GRANTED,
                        PackageManager.PERMISSION_DENIED
                });

        assertNotNull(result);
        assertEquals("call_video", result.getPluginId());
        assertFalse(result.isGranted());
    }

    @Test
    public void shouldIgnoreUnknownRequestCodeAndConsumeOnlyOnce() {
        ChatInputActionBar.PluginPermissionDispatcher dispatcher =
                new ChatInputActionBar.PluginPermissionDispatcher();

        assertNull(dispatcher.consumeResult(99999, new int[]{PackageManager.PERMISSION_GRANTED}));

        dispatcher.registerPendingRequest(12006, "call_voice", new String[]{"mic"});
        assertNotNull(dispatcher.consumeResult(12006, new int[]{PackageManager.PERMISSION_GRANTED}));
        assertNull(dispatcher.consumeResult(12006, new int[]{PackageManager.PERMISSION_GRANTED}));
    }
}

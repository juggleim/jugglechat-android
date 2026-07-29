package com.juggle.im.android.auth;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class OrganizationStoreTest {

    @Test
    public void parseServerInfo_usesRequestedIdWhenAliasMissing() {
        String json = "{"
                + "\"app_key\":\"app-key\","
                + "\"im_servers\":[\"wss://im.example.com\"],"
                + "\"app_servers\":[\"https://api.example.com\"]"
                + "}";

        OrganizationStore.OrganizationConfig config =
                OrganizationStore.parseServerInfo("100000", json);

        assertTrue(config != null);
        assertEquals("100000", config.getOrganizationId());
        assertEquals("app-key", config.getAppKey());
        assertEquals("https://api.example.com", config.getAppServerUrl());
        assertEquals("wss://im.example.com", config.getImServers().get(0));
    }

    @Test
    public void parseServerInfo_rejectsIncompletePayload() {
        OrganizationStore.OrganizationConfig config =
                OrganizationStore.parseServerInfo(
                        "100000",
                        "{\"app_key\":\"app-key\",\"app_servers\":[]}");

        assertNull(config);
    }

    @Test
    public void accountRecord_sessionValidityRequiresFutureExpiry() {
        AccountStore.AccountRecord record = new AccountStore.AccountRecord(
                "100000",
                "user-1",
                "User",
                "",
                "app-token",
                "im-token",
                200L);

        assertTrue(record.isSessionValid(199L));
        assertFalse(record.isSessionValid(200L));
    }
}

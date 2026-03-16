package com.juggle.im.android.auth;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class SessionRepositoryTest {

    @Test
    public void shouldReadValidSessionFromSecureStorage() {
        FakeSessionStorage storage = new FakeSessionStorage();
        storage.secure = new SessionRepository.SessionState("app-secure", "im-secure", 5000L);

        SessionRepository repository = new SessionRepository(storage, () -> 1000L);
        SessionRepository.SessionState session = repository.getValidSession();

        assertNotNull(session);
        assertEquals("app-secure", session.getAppToken());
        assertEquals("im-secure", session.getImToken());
        assertEquals(5000L, session.getExpireAtMillis());
        assertNull(storage.writtenSecure);
        assertTrue(!storage.clearedLegacy);
    }

    @Test
    public void shouldMigrateLegacySessionIntoSecureStorage() {
        FakeSessionStorage storage = new FakeSessionStorage();
        storage.legacy = new SessionRepository.SessionState("app-legacy", "im-legacy", 5000L);

        SessionRepository repository = new SessionRepository(storage, () -> 1000L);
        SessionRepository.SessionState session = repository.getValidSession();

        assertNotNull(session);
        assertEquals("app-legacy", session.getAppToken());
        assertEquals("im-legacy", session.getImToken());
        assertNotNull(storage.writtenSecure);
        assertEquals("app-legacy", storage.writtenSecure.getAppToken());
        assertTrue(storage.clearedLegacy);
    }

    @Test
    public void shouldClearExpiredSession() {
        FakeSessionStorage storage = new FakeSessionStorage();
        storage.secure = new SessionRepository.SessionState("app-expired", "im-expired", 999L);

        SessionRepository repository = new SessionRepository(storage, () -> 1000L);
        SessionRepository.SessionState session = repository.getValidSession();

        assertNull(session);
        assertTrue(storage.clearedSecure);
    }

    @Test
    public void shouldSaveSessionToSecureStorageAndClearLegacy() {
        FakeSessionStorage storage = new FakeSessionStorage();

        SessionRepository repository = new SessionRepository(storage, () -> 1000L);
        repository.saveSession("app-new", "im-new", 5000L);

        assertNotNull(storage.writtenSecure);
        assertEquals("app-new", storage.writtenSecure.getAppToken());
        assertEquals("im-new", storage.writtenSecure.getImToken());
        assertEquals(5000L, storage.writtenSecure.getExpireAtMillis());
        assertTrue(storage.clearedLegacy);
    }

    @Test
    public void shouldClearBothStoragesOnLogout() {
        FakeSessionStorage storage = new FakeSessionStorage();
        SessionRepository repository = new SessionRepository(storage, () -> 1000L);

        repository.clearSession();

        assertTrue(storage.clearedSecure);
        assertTrue(storage.clearedLegacy);
    }

    private static final class FakeSessionStorage implements SessionRepository.Storage {
        private SessionRepository.SessionState secure;
        private SessionRepository.SessionState legacy;
        private SessionRepository.SessionState writtenSecure;
        private boolean clearedSecure;
        private boolean clearedLegacy;

        @Override
        public SessionRepository.SessionState readSecureSession() {
            return secure;
        }

        @Override
        public SessionRepository.SessionState readLegacySession() {
            return legacy;
        }

        @Override
        public void writeSecureSession(SessionRepository.SessionState sessionState) {
            this.writtenSecure = sessionState;
            this.secure = sessionState;
        }

        @Override
        public void clearSecureSession() {
            this.secure = null;
            this.clearedSecure = true;
        }

        @Override
        public void clearLegacySession() {
            this.legacy = null;
            this.clearedLegacy = true;
        }
    }
}

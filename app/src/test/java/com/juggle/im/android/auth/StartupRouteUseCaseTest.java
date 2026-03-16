package com.juggle.im.android.auth;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

public class StartupRouteUseCaseTest {

    @Test
    public void shouldRouteToMainWhenSessionIsValid() {
        SessionRepository.SessionState sessionState =
                new SessionRepository.SessionState("app-token", "im-token", 5000L);
        StartupRouteUseCase useCase = new StartupRouteUseCase(() -> sessionState);

        StartupRouteUseCase.RouteDecision decision = useCase.decide();

        assertEquals(StartupRouteUseCase.TargetRoute.MAIN, decision.getTargetRoute());
        assertEquals("session_valid", decision.getReason());
        assertNotNull(decision.getSessionState());
        assertEquals("app-token", decision.getSessionState().getAppToken());
    }

    @Test
    public void shouldRouteToLoginWhenSessionIsMissing() {
        StartupRouteUseCase useCase = new StartupRouteUseCase(() -> null);

        StartupRouteUseCase.RouteDecision decision = useCase.decide();

        assertEquals(StartupRouteUseCase.TargetRoute.LOGIN, decision.getTargetRoute());
        assertEquals("session_absent_or_expired", decision.getReason());
        assertNull(decision.getSessionState());
    }

    @Test
    public void shouldFallbackToLoginWhenSessionReadThrows() {
        StartupRouteUseCase useCase = new StartupRouteUseCase(() -> {
            throw new IllegalStateException("boom");
        });

        StartupRouteUseCase.RouteDecision decision = useCase.decide();

        assertEquals(StartupRouteUseCase.TargetRoute.LOGIN, decision.getTargetRoute());
        assertEquals("session_restore_error", decision.getReason());
        assertNull(decision.getSessionState());
    }
}

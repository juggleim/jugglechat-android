package com.juggle.im.android.auth;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.juggle.im.android.model.TraceContext;
import com.juggle.im.android.utils.LogUtils;

/**
 * 启动路由决策用例。
 * <p>
 * 统一封装“恢复会话 + 路由判断”，并输出标准化可观测日志：
 * - session.restore.hit / session.restore.miss
 * - route.decide.main / route.decide.login
 */
public final class StartupRouteUseCase {
    private static final String TAG = "StartupRouteUseCase";
    private static final String FEATURE = "auth-shell";

    interface SessionSource {
        @Nullable
        SessionRepository.SessionState readValidSession();
    }

    public enum TargetRoute {
        MAIN,
        LOGIN
    }

    interface RouteObserver {
        void onEvent(@NonNull String traceId,
                     @NonNull String event,
                     @NonNull String result,
                     @NonNull String detail);

        static RouteObserver noop() {
            return (traceId, event, result, detail) -> {
            };
        }
    }

    public static final class RouteDecision {
        private final TargetRoute targetRoute;
        private final String reason;
        private final SessionRepository.SessionState sessionState;

        private RouteDecision(@NonNull TargetRoute targetRoute,
                              @NonNull String reason,
                              @Nullable SessionRepository.SessionState sessionState) {
            this.targetRoute = targetRoute;
            this.reason = reason;
            this.sessionState = sessionState;
        }

        @NonNull
        public TargetRoute getTargetRoute() {
            return targetRoute;
        }

        @NonNull
        public String getReason() {
            return reason;
        }

        @Nullable
        public SessionRepository.SessionState getSessionState() {
            return sessionState;
        }
    }

    private final SessionSource sessionSource;
    private final RouteObserver routeObserver;

    public StartupRouteUseCase(@NonNull SessionSource sessionSource) {
        this(sessionSource, RouteObserver.noop());
    }

    StartupRouteUseCase(@NonNull SessionSource sessionSource, @NonNull RouteObserver routeObserver) {
        this.sessionSource = sessionSource;
        this.routeObserver = routeObserver;
    }

    public static StartupRouteUseCase create(@NonNull Context context) {
        SessionRepository repository = SessionRepository.create(context);
        return new StartupRouteUseCase(repository::getValidSession,
                (traceId, event, result, detail) -> LogUtils.i(TAG, traceId, FEATURE, event, result, detail));
    }

    @NonNull
    public RouteDecision decide() {
        String traceId = TraceContext.currentOrNew();
        long start = System.currentTimeMillis();
        try {
            SessionRepository.SessionState sessionState = sessionSource.readValidSession();
            long latencyMs = Math.max(0L, System.currentTimeMillis() - start);
            if (sessionState != null) {
                routeObserver.onEvent(traceId, "session.restore.hit", "success", "latencyMs=" + latencyMs);
                routeObserver.onEvent(traceId, "route.decide.main", "success", "reason=session_valid");
                return new RouteDecision(TargetRoute.MAIN, "session_valid", sessionState);
            }
            routeObserver.onEvent(traceId, "session.restore.miss", "success", "latencyMs=" + latencyMs);
            routeObserver.onEvent(traceId, "route.decide.login", "success", "reason=session_absent_or_expired");
            return new RouteDecision(TargetRoute.LOGIN, "session_absent_or_expired", null);
        } catch (RuntimeException e) {
            routeObserver.onEvent(traceId, "session.restore.fail", "fail",
                    "reason=" + e.getClass().getSimpleName());
            routeObserver.onEvent(traceId, "route.decide.login", "success",
                    "reason=session_restore_error");
            return new RouteDecision(TargetRoute.LOGIN, "session_restore_error", null);
        } finally {
            TraceContext.clear();
        }
    }
}

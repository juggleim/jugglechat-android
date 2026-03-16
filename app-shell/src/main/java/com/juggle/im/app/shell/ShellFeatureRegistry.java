package com.juggle.im.app.shell;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 壳层 Feature 注册中心。
 * <p>
 * 复杂逻辑说明：
 * - 使用 route 作为唯一键，避免重复注册导致的路由歧义。
 * - 使用并发容器保障多线程初始化时的稳定性。
 */
public final class ShellFeatureRegistry {

    private final Map<String, FeatureEntry> entries = new ConcurrentHashMap<>();

    public void register(FeatureEntry entry) {
        FeatureEntry previous = entries.putIfAbsent(entry.route(), entry);
        if (previous != null) {
            throw new IllegalStateException("重复的 Feature 路由: " + entry.route());
        }
    }

    public FeatureEntry find(String route) {
        return entries.get(route);
    }
}

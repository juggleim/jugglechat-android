package com.juggle.im.app.shell;

/**
 * 壳层挂载 Feature 的对外契约。
 * <p>
 * 每个 feature 只需要提供稳定的路由标识与归属信息，避免壳层依赖具体实现类。
 */
public interface FeatureEntry {

    /**
     * @return 供路由层识别的稳定路由 key，例如 "chat/conversation"。
     */
    String route();

    /**
     * @return 模块归属标识，用于日志与故障定位。
     */
    String owner();
}

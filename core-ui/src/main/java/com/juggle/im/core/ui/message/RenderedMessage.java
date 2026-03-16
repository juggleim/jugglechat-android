package com.juggle.im.core.ui.message;

/**
 * 渲染结果占位模型。
 * <p>
 * 当前阶段只返回字符串描述，后续可以替换为 ViewModel/Compose model 而不影响上层接口。
 */
public final class RenderedMessage {

    private final String description;

    public RenderedMessage(String description) {
        this.description = description;
    }

    public String description() {
        return description;
    }
}

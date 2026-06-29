package com.juggle.im.android.chat.utils;

import android.content.Context;
import android.widget.TextView;

import io.noties.markwon.Markwon;
import io.noties.markwon.ext.strikethrough.StrikethroughPlugin;
import io.noties.markwon.ext.tables.TablePlugin;
import io.noties.markwon.linkify.LinkifyPlugin;

/**
 * Markdown 渲染工具。
 *
 * <p>用于 AI 流式文本消息：将 Markdown 文本渲染为带样式的富文本。
 * Markwon 实例的构建较重（需解析插件、注册渲染器），这里按 ApplicationContext 缓存单例，
 * 避免每次绑定 ViewHolder 都重建。</p>
 */
public final class MarkdownRenderer {

    private static volatile Markwon sMarkwon;

    private MarkdownRenderer() {
    }

    private static Markwon get(Context context) {
        if (sMarkwon == null) {
            synchronized (MarkdownRenderer.class) {
                if (sMarkwon == null) {
                    sMarkwon = Markwon.builder(context.getApplicationContext())
                            .usePlugin(StrikethroughPlugin.create())
                            .usePlugin(TablePlugin.create(context.getApplicationContext()))
                            .usePlugin(LinkifyPlugin.create())
                            .build();
                }
            }
        }
        return sMarkwon;
    }

    /**
     * 将 Markdown 文本渲染到 TextView。
     *
     * @param textView 目标视图
     * @param markdown 原始 Markdown 文本（流式过程中可能是不完整片段，Markwon 会尽力渲染）
     */
    public static void render(TextView textView, String markdown) {
        if (textView == null) {
            return;
        }
        if (markdown == null) {
            markdown = "";
        }
        get(textView.getContext()).setMarkdown(textView, markdown);
    }
}

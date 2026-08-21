package com.juggle.im.android.chat.utils;

import android.annotation.SuppressLint;
import android.text.Layout;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextPaint;
import android.text.style.BackgroundColorSpan;
import android.text.style.ClickableSpan;
import android.text.style.URLSpan;
import android.text.util.Linkify;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewParent;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.text.util.LinkifyCompat;

import com.juggle.im.android.R;
import com.juggle.im.android.app.WebViewPageActivity;

/**
 * 消息文本中的网址识别与点击处理。
 *
 * <p>负责把消息里的 http/https（含省略协议的 www.xxx.com）网址高亮成可点击文本，
 * 点击后用内置 WebView 打开。</p>
 */
public final class MessageLinkUtils {

    /** 链接按下时的浅色底纹，用于给出点击反馈 */
    private static final int PRESSED_BACKGROUND = 0x33808080;

    private MessageLinkUtils() {
    }

    /**
     * 识别文本中的网址并设置到 TextView，同时挂上点击打开 WebView 的能力。
     *
     * @param textView 承载消息文本的视图
     * @param text     已完成 @提及 等处理的文本（原有 Span 会被保留）
     * @param isSend   是否为自己发出的消息，决定链接配色（发出方气泡为深色底）
     */
    public static void applyLinks(@NonNull TextView textView, @Nullable CharSequence text, boolean isSend) {
        SpannableStringBuilder builder = new SpannableStringBuilder(text == null ? "" : text);
        LinkifyCompat.addLinks(builder, Linkify.WEB_URLS);

        int linkColor = ContextCompat.getColor(textView.getContext(),
                isSend ? R.color.message_link_sent : R.color.message_link_received);
        // TIPS：Linkify 生成的是系统 URLSpan（点击走外部浏览器），这里统一替换成内置 WebView 的 Span
        URLSpan[] urlSpans = builder.getSpans(0, builder.length(), URLSpan.class);
        for (URLSpan urlSpan : urlSpans) {
            int start = builder.getSpanStart(urlSpan);
            int end = builder.getSpanEnd(urlSpan);
            String url = urlSpan.getURL();
            builder.removeSpan(urlSpan);
            builder.setSpan(new WebLinkSpan(url, linkColor), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }

        textView.setText(builder, TextView.BufferType.SPANNABLE);
        attachLinkTouchHandler(textView, urlSpans.length > 0);
    }

    /**
     * 给已经带有 ClickableSpan 的 TextView 挂上链接触摸处理（如 Markdown 渲染后的文本）。
     *
     * @param textView 承载消息文本的视图
     */
    public static void attachLinkTouchHandler(@NonNull TextView textView) {
        attachLinkTouchHandler(textView, true);
    }

    /**
     * TIPS：不能用 LinkMovementMethod，它会把 TextView 置为 clickable/longClickable，
     * 消息气泡的长按菜单会被吞掉；这里自己处理触摸——命中链接才消费事件，
     * 未命中则返回 false 交还父容器，链接上的长按也会转发给气泡。
     */
    @SuppressLint("ClickableViewAccessibility")
    private static void attachLinkTouchHandler(@NonNull TextView textView, boolean hasLink) {
        textView.setMovementMethod(null);
        textView.setClickable(false);
        textView.setLongClickable(false);
        textView.setHighlightColor(0x00000000);
        // 复用 ViewHolder 时必须清掉旧的监听，避免无链接的消息仍然拦截触摸
        textView.setOnTouchListener(hasLink ? new LinkTouchListener() : null);
    }

    /**
     * 网址点击 Span：高亮显示并在内置 WebView 中打开。
     */
    private static final class WebLinkSpan extends ClickableSpan {
        private final String url;
        private final int color;

        WebLinkSpan(String url, int color) {
            this.url = url;
            this.color = color;
        }

        @Override
        public void onClick(@NonNull View widget) {
            WebViewPageActivity.navToUrl(widget.getContext(), url);
        }

        @Override
        public void updateDrawState(@NonNull TextPaint ds) {
            ds.setColor(color);
            ds.setUnderlineText(true);
        }
    }

    /**
     * 链接触摸处理：命中链接时消费触摸事件，长按仍转发给消息气泡。
     */
    private static final class LinkTouchListener implements View.OnTouchListener {
        private ClickableSpan pressedSpan;
        private BackgroundColorSpan pressedBackground;
        private Runnable longPressTask;
        private boolean longPressFired;
        private float downX;
        private float downY;

        @Override
        public boolean onTouch(View v, MotionEvent event) {
            if (!(v instanceof TextView)) {
                return false;
            }
            TextView textView = (TextView) v;
            CharSequence text = textView.getText();
            if (!(text instanceof Spannable)) {
                return false;
            }
            Spannable spannable = (Spannable) text;
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN: {
                    ClickableSpan span = findSpan(textView, spannable, event);
                    if (span == null) {
                        return false;
                    }
                    pressedSpan = span;
                    longPressFired = false;
                    downX = event.getX();
                    downY = event.getY();
                    markPressed(spannable, span);
                    scheduleLongPress(textView);
                    return true;
                }
                case MotionEvent.ACTION_MOVE: {
                    if (pressedSpan == null) {
                        return false;
                    }
                    int slop = ViewConfiguration.get(textView.getContext()).getScaledTouchSlop();
                    if (Math.abs(event.getX() - downX) > slop || Math.abs(event.getY() - downY) > slop) {
                        release(textView, spannable);
                    }
                    return true;
                }
                case MotionEvent.ACTION_UP: {
                    if (pressedSpan == null) {
                        return false;
                    }
                    ClickableSpan span = pressedSpan;
                    boolean consumedByLongPress = longPressFired;
                    release(textView, spannable);
                    if (!consumedByLongPress) {
                        span.onClick(textView);
                    }
                    return true;
                }
                case MotionEvent.ACTION_CANCEL: {
                    if (pressedSpan == null) {
                        return false;
                    }
                    release(textView, spannable);
                    return true;
                }
                default:
                    return false;
            }
        }

        private void scheduleLongPress(TextView textView) {
            cancelLongPress(textView);
            longPressTask = () -> {
                longPressFired = true;
                CharSequence text = textView.getText();
                if (text instanceof Spannable) {
                    clearPressedBackground((Spannable) text);
                }
                dispatchLongClickToParent(textView);
            };
            textView.postDelayed(longPressTask, ViewConfiguration.getLongPressTimeout());
        }

        private void cancelLongPress(TextView textView) {
            if (longPressTask != null) {
                textView.removeCallbacks(longPressTask);
                longPressTask = null;
            }
        }

        private void release(TextView textView, Spannable spannable) {
            cancelLongPress(textView);
            clearPressedBackground(spannable);
            pressedSpan = null;
        }

        private void markPressed(Spannable spannable, ClickableSpan span) {
            clearPressedBackground(spannable);
            int start = spannable.getSpanStart(span);
            int end = spannable.getSpanEnd(span);
            if (start < 0 || end < 0) {
                return;
            }
            pressedBackground = new BackgroundColorSpan(PRESSED_BACKGROUND);
            spannable.setSpan(pressedBackground, start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }

        private void clearPressedBackground(Spannable spannable) {
            if (pressedBackground != null) {
                spannable.removeSpan(pressedBackground);
                pressedBackground = null;
            }
        }

        /**
         * 把链接上的长按转交给消息气泡，保证长按菜单（复制/转发/撤回等）不丢失。
         */
        private void dispatchLongClickToParent(View view) {
            ViewParent parent = view.getParent();
            while (parent instanceof View) {
                View parentView = (View) parent;
                if (parentView.performLongClick()) {
                    return;
                }
                parent = parentView.getParent();
            }
        }

        @Nullable
        private ClickableSpan findSpan(TextView textView, Spannable spannable, MotionEvent event) {
            Layout layout = textView.getLayout();
            if (layout == null) {
                return null;
            }
            int x = (int) (event.getX() - textView.getTotalPaddingLeft() + textView.getScrollX());
            int y = (int) (event.getY() - textView.getTotalPaddingTop() + textView.getScrollY());
            int line = layout.getLineForVertical(y);
            // TIPS：行尾空白处 getOffsetForHorizontal 会吸附到最后一个字符，先做行内水平范围校验避免误命中
            if (x < layout.getLineLeft(line) || x > layout.getLineRight(line)) {
                return null;
            }
            int offset = layout.getOffsetForHorizontal(line, x);
            ClickableSpan[] spans = spannable.getSpans(offset, offset, ClickableSpan.class);
            return spans.length > 0 ? spans[0] : null;
        }
    }
}

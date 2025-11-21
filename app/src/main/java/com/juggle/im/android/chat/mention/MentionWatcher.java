package com.juggle.im.android.chat.mention;

import android.text.Editable;
import android.text.SpannableStringBuilder;
import android.text.TextWatcher;
import android.widget.EditText;

public class MentionWatcher implements TextWatcher {

    private final EditText editText;
    private final MentionConfig config;
    private final MentionCallback callback;

    private int deleteStart = -1;
    private int deleteCount = 0;

    public MentionWatcher(EditText editText, MentionConfig config, MentionCallback callback) {
        this.editText = editText;
        this.config = config;
        this.callback = callback;
    }

    @Override
    public void beforeTextChanged(CharSequence s, int start, int count, int after) {
        deleteStart = start;
        deleteCount = count;
    }

    @Override
    public void onTextChanged(CharSequence s, int start, int before, int count) {
        if (count == 1) {
            char c = s.charAt(start);  // 当前新增的字符
            if (c == config.triggerChar) {
                callback.onMentionTrigger(editText);
            }
        }
    }


    @Override
    public void afterTextChanged(Editable s) {
        if (s == null) return;

        // 1. 如果用户执行了删除
        if (deleteCount > 0) {
            handleDeleteSpan(s);
        }

        // 3. 检查内文破坏 span
        validateSpans(s);
    }

    private void handleDeleteSpan(Editable s) {
        MentionSpan[] spans = s.getSpans(0, s.length(), MentionSpan.class);

        for (MentionSpan span : spans) {
            int start = s.getSpanStart(span);
            int end = s.getSpanEnd(span);

            // 情况：光标在 span 尾部删除
            if (deleteStart == end) {
                // 整体删除
                s.delete(start, end);
                s.removeSpan(span);

                callback.onMentionInvalidated(span.userId);
                return;
            }
        }
    }

    private void validateSpans(Editable s) {
        MentionSpan[] spans = s.getSpans(0, s.length(), MentionSpan.class);

        for (MentionSpan span : spans) {
            int start = s.getSpanStart(span);
            int end = s.getSpanEnd(span);

            String text = s.subSequence(start, end).toString();
            if (!text.contains(span.displayName)) {
                s.removeSpan(span);
                callback.onMentionInvalidated(span.userId);
            }
        }
    }
}

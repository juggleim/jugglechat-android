package com.juggle.im.android.chat.mention;

import android.text.Editable;
import android.text.SpannableStringBuilder;
import android.text.TextWatcher;
import android.util.Log;
import android.widget.EditText;

public class MentionWatcher implements TextWatcher {

    private final EditText editText;
    private final MentionConfig config;
    private final MentionCallback callback;
    private boolean internalChange = false;

    private int deleteStart = -1;
    private int deleteCount = 0;

    public MentionWatcher(EditText editText, MentionConfig config, MentionCallback callback) {
        this.editText = editText;
        this.config = config;
        this.callback = callback;
    }

    @Override
    public void beforeTextChanged(CharSequence s, int start, int count, int after) {
        if (internalChange) return;
        deleteStart = start;
        deleteCount = count;
        Log.d("Mention beforeTextChanged", deleteStart + ", " + deleteCount + ",str=" + s.toString());
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
        int cursor = editText.getSelectionStart();

        // 1. 如果用户执行了删除
        if (deleteCount > 0) {
            // 使用 beforeTextChanged 中记录的删除起始位置和数量
            handleDeleteSpan(s, deleteStart, deleteCount);
        }

        // 3. 检查内文破坏 span
        validateSpans(s);
    }

    private void handleDeleteSpan(Editable s, int deletePosition, int deleteCount) {
        if (internalChange) return;

        MentionSpan[] spans = s.getSpans(0, s.length(), MentionSpan.class);

        // 查找是否有span在删除范围内
        for (MentionSpan span : spans) {
            int start = s.getSpanStart(span);
            int end = s.getSpanEnd(span);
            
            // 如果删除操作涉及到span（包括部分覆盖或完全包含）
            if (deletePosition < end && (deletePosition + deleteCount) > start) {
                internalChange = true;

                // 删除整个 span 块
                s.delete(start, end);
                s.removeSpan(span);

                internalChange = false;

                callback.onMentionInvalidated(span.userId);
                // 一次删除操作只处理一个span
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
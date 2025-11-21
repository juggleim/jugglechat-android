package com.juggle.im.android.chat.mention;

import android.text.Editable;
import android.text.Spanned;
import android.util.Log;
import android.widget.EditText;

import java.util.ArrayList;
import java.util.List;

public class MentionManager {

    private EditText editText;
    private MentionConfig config;

    private MentionWatcher watcher;

    public MentionManager() {
    }

    public void init(EditText editText,
                     MentionCallback callback,
                     MentionConfig config) {

        this.editText = editText;
        this.config = config;

        watcher = new MentionWatcher(editText, config, callback);
        editText.addTextChangedListener(watcher);
    }

    /**
     * 插入 @all
     */
    public void insertMentionAll() {
        insertMentionAtCursor("all", "all");
    }

    /**
     * 在选完用户后插入 @ 块
     */
    public void insertMention(ArrayList<String> userIds, ArrayList<String> userNames) {
        for (int i = 0; i < userIds.size(); i++) {
            insertMentionAtCursor(userIds.get(i), userNames.get(i));
        }
    }
    private void insertMentionAtCursor(String userId, String displayName) {
        Editable editable = editText.getText();
        int cursor = editText.getSelectionStart();

        // 判断光标前是否是 @
        boolean needAt = true;
        if (cursor > 0) {
            char preChar = editable.charAt(cursor - 1);
            if (preChar == '@') {
                needAt = false;
            }
        }

        String label = (needAt ? "@" : "") + displayName + config.mentionSuffix;

        // 插入文本
        editable.insert(cursor, label);

        // 设置 span
        int start = cursor - (needAt ? 0 : 1);
        int end = start + label.length();
        Log.d("MentionManager", "span start: " + start + ", end: " + end + ", insert " + label + ", editable=" + editable.toString());

        editable.setSpan(
                new MentionSpan(userId, displayName),
                start,
                end,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        );

        // 光标移动到空格后面
        editText.setSelection(cursor + label.length());
    }


    /**
     * 在发送消息时提取所有 at 信息
     */
    public List<MentionModel> collectMentions() {
        Editable s = editText.getText();

        MentionSpan[] spans = s.getSpans(0, s.length(), MentionSpan.class);
        List<MentionModel> list = new ArrayList<>();

        for (MentionSpan span : spans) {
            list.add(new MentionModel(
                    span.userId,
                    span.displayName,
                    s.getSpanStart(span),
                    s.getSpanEnd(span)
            ));
        }
        return list;
    }
}



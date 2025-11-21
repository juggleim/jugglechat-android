package com.juggle.im.android.chat.mention;

import android.widget.EditText;

public interface MentionCallback {

    /** 当用户输入 @ 时触发 */
    void onMentionTrigger(EditText editText);

    /** 当某个 @ 块被破坏或整体删除时触发 */
    default void onMentionInvalidated(String mentionId) {}
}


package com.juggle.im.android.chat.view;

import android.content.Context;
import android.graphics.Rect;
import android.text.InputType;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.KeyEvent;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;

import androidx.appcompat.widget.AppCompatEditText;

/**
 * EditText that requests IME to show the SEND action even when the field is visually multiline.
 * It adjusts the EditorInfo seen by the IME to clear the MULTI_LINE flag and set IME_ACTION_SEND.
 * It also supports Shift+Enter insertion of newlines from physical keyboards.
 */
public class SendImeEditText extends AppCompatEditText {

    public SendImeEditText(Context context) {
        super(context);
        initView();
    }

    public SendImeEditText(Context context, AttributeSet attrs) {
        super(context, attrs);
        initView();
    }

    public SendImeEditText(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        initView();
    }

    /**
     * 初始化输入框显示属性。
     *
     * <p>tips：仅限制 hint 为单行省略显示，不影响用户实际输入多行内容。</p>
     */
    private void initView() {
        setHorizontallyScrolling(true);
        setMaxLines(1);
        setEllipsize(TextUtils.TruncateAt.END);
    }

    @Override
    public InputConnection onCreateInputConnection(EditorInfo outAttrs) {
        InputConnection ic = super.onCreateInputConnection(outAttrs);
        try {
            // Ensure IME_ACTION_SEND is advertised
            outAttrs.imeOptions &= ~EditorInfo.IME_FLAG_NO_ENTER_ACTION;
            outAttrs.imeOptions |= EditorInfo.IME_ACTION_SEND;

            // Some IMEs decide whether to show the action button based on the inputType flags.
            // Clear the MULTI_LINE flag from the reported inputType so IME will show the action.
            outAttrs.inputType &= ~InputType.TYPE_TEXT_FLAG_MULTI_LINE;
        } catch (Throwable ignored) {
        }
        return ic;
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_ENTER && event.getAction() == KeyEvent.ACTION_DOWN) {
            if (event.isShiftPressed()) {
                // Insert newline when Shift+Enter pressed (physical keyboard)
                int pos = Math.max(getSelectionStart(), 0);
                getText().insert(pos, "\n");
                return true;
            }
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    protected void onFocusChanged(boolean focused, int direction, Rect previouslyFocusedRect) {
        super.onFocusChanged(focused, direction, previouslyFocusedRect);
    }
}

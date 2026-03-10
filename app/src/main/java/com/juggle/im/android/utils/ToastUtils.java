package com.juggle.im.android.utils;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.StringRes;

import com.juggle.im.android.R;

public final class ToastUtils {

    private ToastUtils() {
    }

    public static void show(Context context, String message) {
        if (context == null || message == null || message.trim().isEmpty()) {
            return;
        }
        View view = LayoutInflater.from(context).inflate(R.layout.layout_toast_message, null, false);
        TextView toastText = view.findViewById(R.id.toastText);
        toastText.setText(message.trim());

        Toast toast = new Toast(context.getApplicationContext());
        toast.setDuration(Toast.LENGTH_SHORT);
        toast.setView(view);
        toast.show();
    }

    public static void show(Context context, @StringRes int messageResId) {
        if (context == null) {
            return;
        }
        show(context, context.getString(messageResId));
    }
}

package com.juggle.im.android.utils;

import android.content.Context;

public class ResourceUtils {
    public static int dp2px(Context context, float dp) {
        return (int) (dp * context.getResources().getDisplayMetrics().density + 0.5f);
    }
}

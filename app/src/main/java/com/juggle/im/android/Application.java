package com.juggle.im.android;
import androidx.multidex.MultiDexApplication;

import com.juggle.im.android.core.JIMChatCore;
import com.juggle.im.android.model.ConfigUtils;

import java.util.Collections;

public class Application extends MultiDexApplication {

    @Override
    public void onCreate() {
        super.onCreate();
        JIMChatCore.getInstance().init(this, Collections.singletonList(ConfigUtils.imServer), ConfigUtils.appKey);

    }
}

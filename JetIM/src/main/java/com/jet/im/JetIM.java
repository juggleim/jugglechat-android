package com.jet.im;

import android.content.Context;
import android.text.TextUtils;

import com.jet.im.interfaces.IConnectionManager;
import com.jet.im.interfaces.IConversationManager;
import com.jet.im.interfaces.IMessageManager;
import com.jet.im.interfaces.IUserInfoManager;
import com.jet.im.internal.ConnectionManager;
import com.jet.im.internal.ConversationManager;
import com.jet.im.internal.MessageManager;
import com.jet.im.internal.UserInfoManager;
import com.jet.im.internal.core.JetIMCore;
import com.jet.im.push.PushConfig;
import com.jet.im.push.PushManager;
import com.jet.im.internal.util.LoggerUtils;

public class JetIM {

    public static JetIM getInstance() {
        return SingletonHolder.sInstance;
    }

    public void init(Context context, String appKey) {
        init(context, appKey, new InitConfig());
    }

    public void init(Context context, String appKey, InitConfig initConfig) {
        if (context == null) {
            throw new IllegalArgumentException("context is null");
        }
        if (TextUtils.isEmpty(appKey)) {
            throw new IllegalArgumentException("app key is empty");
        }
        LoggerUtils.i("init, appKey is " + appKey);
        mCore.setContext(context);
        PushManager.getInstance().init(context, initConfig.getPushConfig());
        if (appKey.equals(mCore.getAppKey())) {
            return;
        }
        mCore.setAppKey(appKey);
        mCore.setUserId("");
        mCore.setToken("");
    }

    public void setServer(String serverUrl) {
        mCore.setNaviUrl(serverUrl);
    }

    public IConnectionManager getConnectionManager() {
        return mConnectionManager;
    }

    public IMessageManager getMessageManager() {
        return mMessageManager;
    }

    public IConversationManager getConversationManager() {
        return mConversationManager;
    }

    public IUserInfoManager getUserInfoManager() {
        return mUserInfoManager;
    }

    private static class SingletonHolder {
        static final JetIM sInstance = new JetIM();
    }

    private JetIM() {
        JetIMCore core = new JetIMCore();
        mCore = core;
        mMessageManager = new MessageManager(core);
        mConversationManager = new ConversationManager(core);
        mMessageManager.setSendReceiveListener(mConversationManager);
        mConnectionManager = new ConnectionManager(core, mConversationManager, mMessageManager);
        mUserInfoManager = new UserInfoManager(core);
    }

    private final ConnectionManager mConnectionManager;
    private final MessageManager mMessageManager;
    private final ConversationManager mConversationManager;
    private final UserInfoManager mUserInfoManager;
    private final JetIMCore mCore;

    public static class InitConfig {
        private PushConfig pushConfig=new PushConfig();

        public PushConfig getPushConfig() {
            return pushConfig;
        }

        public void setPushConfig(PushConfig pushConfig) {
            this.pushConfig = pushConfig;
        }
    }
}

package com.jet.im.push;

import android.content.Context;
import android.util.Log;

import com.jet.im.JetIM;
import com.jet.im.internal.util.JUtility;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class PushManager implements IPush.Callback {
    private static final String TAG = "PushManager";
    private ThreadPoolExecutor pushExecutor;
    Map<PushChannel, IPush> iPushMap = new HashMap<>();

    public static PushManager getInstance() {
        return PushManager.SingletonHolder.sInstance;
    }

    private PushManager() {
        pushExecutor = new ThreadPoolExecutor(1, 1, 60, TimeUnit.SECONDS, new LinkedBlockingQueue<>());
        pushExecutor.allowCoreThreadTimeOut(true);
    }

    public void init(Context context, PushConfig config) {
        pushExecutor.execute(new Runnable() {
            @Override
            public void run() {
                init("com.jet.im.push.hw.HWPush");
                init("com.jet.im.push.xm.XMPush");
                init("com.jet.im.push.google.GooglePush");
                List<IPush> pushList = getRegisterPush();
                for (IPush item : pushList) {
                    item.getToken(context, config, PushManager.this);
                }
            }
        });
    }

    /**
     * 获取适合的推送类型 根据手机机型和用户 enable 适配的推送类型
     */
    public List<IPush> getRegisterPush() {
        List<IPush> result = new ArrayList<>();
        String os = JUtility.getDeviceManufacturer().toLowerCase();
        for (Map.Entry<PushChannel, IPush> item : iPushMap.entrySet()) {
            if (item.getKey() == PushChannel.GOOGLE) {
                result.add(item.getValue());
                continue;
            }
            String pushTypeOs = item.getKey().getOs();
            if (pushTypeOs.contains(os)) {
                result.add(item.getValue());
            }
        }
        return result;
    }

    private void init(String className) {
        try {
            Class<?> aClass = Class.forName(className);
            IPush push = (IPush) aClass.newInstance();
            iPushMap.put(push.getType(), push);
        } catch (ClassNotFoundException | IllegalAccessException | InstantiationException e) {
            Log.d(TAG, "not register " + className);
        }
    }

    @Override
    public void onReceivedToken(PushChannel type, String token) {
        //todo 1.本地持久化，2.调用上报 token 接口
        pushExecutor.execute(new Runnable() {
            @Override
            public void run() {
                JetIM.getInstance().getConnectionManager().registerPushToken(type, token);
            }
        });
    }

    @Override
    public void onError(PushChannel type, int code, String msg) {
        //todo 处理 onError
    }

    private static class SingletonHolder {
        static final PushManager sInstance = new PushManager();
    }
}
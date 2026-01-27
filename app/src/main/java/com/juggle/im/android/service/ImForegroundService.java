package com.juggle.im.android.service;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;

import androidx.core.app.NotificationCompat;

import com.juggle.im.android.R;
import com.juggle.im.android.app.MainActivity;
import com.juggle.im.android.core.JIMChatCore;
import com.juggle.im.android.model.ConfigUtils;

import java.util.Random;

/**
 * IM前台服务
 */
public class ImForegroundService extends Service {

    public static final String CHANNEL_ID = "im_foreground_channel";
    public static final int NOTIFICATION_ID = 1001;

    public static final String ACTION_START = "com.juggle.im.android.service.ACTION_START";
    public static final String ACTION_STOP = "com.juggle.im.android.service.ACTION_STOP";
    public static final String ACTION_KEEP_ALIVE = "com.juggle.im.android.service.ACTION_KEEP_ALIVE";

    private NotificationManager notificationManager;
    private NotificationCompat.Builder notificationBuilder;

    // ====== 动态通知相关 ======
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Random random = new Random();
    private boolean notificationLoopRunning = false;

    private final String[] dynamicTexts = new String[] {
            "正在保持聊天连接",
            "聊天服务运行中",
            "正在同步消息",
            "连接服务器正常",
            "IM服务活跃中"
    };

    private final Runnable notificationUpdater = new Runnable() {
        @Override
        public void run() {
            if (!notificationLoopRunning) return;

            updateNotification(getRandomText());

            // 下次执行时间：3–5 秒随机
            long delay = 3000 + random.nextInt(2000);
            handler.postDelayed(this, delay);
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();

        notificationManager =
                (NotificationManager) getSystemService(NOTIFICATION_SERVICE);

        notificationBuilder = createNotificationBuilder();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) {
            return START_STICKY;
        }

        String action = intent.getAction();

        if (ACTION_START.equals(action) || ACTION_KEEP_ALIVE.equals(action)) {

            startForeground(NOTIFICATION_ID, notificationBuilder.build());
            startNotificationLoop();

            String token = ConfigUtils.imToken;
            if (token != null && !token.isEmpty()) {
                JIMChatCore.getInstance().connect(token);
            }

            return START_STICKY;

        } else if (ACTION_STOP.equals(action)) {

            stopNotificationLoop();
            stopForeground(STOP_FOREGROUND_REMOVE);
            stopSelf();

            return START_NOT_STICKY;
        }

        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        stopNotificationLoop();
        super.onDestroy();
    }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        super.onTaskRemoved(rootIntent);
        // 不主动停止
    }

    // ================== 通知相关 ==================

    private NotificationCompat.Builder createNotificationBuilder() {
        Intent notificationIntent = new Intent(this, MainActivity.class);
        notificationIntent.setFlags(
                Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP
        );

        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            flags |= PendingIntent.FLAG_IMMUTABLE;
        }

        PendingIntent pendingIntent = PendingIntent.getActivity(
                this, 0, notificationIntent, flags
        );

        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle(getString(R.string.im_service_title))
                .setContentText(getString(R.string.im_service_content))
                .setSmallIcon(R.drawable.ic_notification)
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setPriority(NotificationCompat.PRIORITY_LOW);
    }

    private void updateNotification(String text) {
        notificationBuilder.setContentText(text);
        notificationManager.notify(
                NOTIFICATION_ID,
                notificationBuilder.build()
        );
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    getString(R.string.im_service_channel_name),
                    NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription(
                    getString(R.string.im_service_channel_description)
            );
            channel.setShowBadge(false);

            NotificationManager manager =
                    getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }

    // ================== 定时更新控制 ==================

    private void startNotificationLoop() {
        if (notificationLoopRunning) return;

        notificationLoopRunning = true;
        handler.post(notificationUpdater);
    }

    private void stopNotificationLoop() {
        notificationLoopRunning = false;
        handler.removeCallbacks(notificationUpdater);
    }

    private String getRandomText() {
        int index = random.nextInt(dynamicTexts.length);
        return dynamicTexts[index];
    }
}

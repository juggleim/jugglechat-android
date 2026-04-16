package com.juggle.im.android.chat.view;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.media.MediaRecorder;
import android.os.Handler;
import android.os.Looper;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.juggle.im.android.R;
import com.juggle.im.android.utils.PermissionComponent;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * 语音输入组件
 *
 * <p>按住说话录音，上滑取消。UI 分两种状态：</p>
 * <ul>
 *   <li>正常录音：半透明遮罩 + 主题色语音条（圆角矩形 + 底部半圆弧） + 麦克风图标 + "松开发送，上滑取消"</li>
 *   <li>上滑取消：弧形变黑，语音条变红，文字变为"松开取消"</li>
 * </ul>
 */
public class VoiceInputAction extends FrameLayout {
    private static final String TAG = "VoiceInputAction";

    public interface Callback {
        void onStart();

        void onFinish(String filePath, long durationMs);

        void onCancel();

        void onTooShort();
    }

    private Callback callback;
    private MediaRecorder recorder;
    private File outFile;
    private long startTimeMs;
    private boolean recording = false;
    private boolean slideToCancel = false;

    private View overlayView;
    private View voiceBar;
    private View voiceArc;
    private ImageView micIcon;
    private TextView voiceHint;

    private Handler uiHandler = new Handler(Looper.getMainLooper());

    public VoiceInputAction(@NonNull Context context, AttributeSet attrs) {
        super(context, attrs);
        LayoutInflater.from(context).inflate(R.layout.voice_input_action, this, true);
    }

    private void initOverlay(ViewGroup root) {
        try {
            if (getContext() instanceof Activity) {
                Activity act = (Activity) getContext();
                overlayView = act.findViewById(R.id.voice_record_overlay);
            }
        } catch (Throwable t) {
            overlayView = null;
        }

        if (overlayView != null) {
            voiceBar = overlayView.findViewById(R.id.voice_bar);
            voiceArc = overlayView.findViewById(R.id.voice_arc);
            micIcon = overlayView.findViewById(R.id.iv_mic_icon);
            voiceHint = overlayView.findViewById(R.id.voice_hint);
        } else {
            LayoutInflater li = LayoutInflater.from(getContext());
            overlayView = li.inflate(R.layout.voice_record_overlay, root, false);
            overlayView.setLayoutParams(new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            root.addView(overlayView);
            voiceBar = overlayView.findViewById(R.id.voice_bar);
            voiceArc = overlayView.findViewById(R.id.voice_arc);
            micIcon = overlayView.findViewById(R.id.iv_mic_icon);
            voiceHint = overlayView.findViewById(R.id.voice_hint);
        }
        overlayView.setVisibility(GONE);
        setClickable(true);
        setFocusable(true);
    }

    private void initHolder(ViewGroup inputArea) {
        setVisibility(VISIBLE);
        TextView hintText = inputArea.findViewById(R.id.hold_to_talk);

        hintText.setOnTouchListener((v, event) -> {
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    if (checkAudioPermission()) {
                        showOverlay(true);
                        startRecording();
                    } else {
                        if (getContext() instanceof Activity) {
                            ActivityCompatWrapper.requestAudioPermission((Activity) getContext());
                        }
                        showOverlay(true);
                    }
                    return true;
                case MotionEvent.ACTION_MOVE:
                    float y = event.getY();
                    float h = getHeight();
                    // tips: 上滑超过 35% 区域视为取消
                    boolean wasCancel = slideToCancel;
                    slideToCancel = y < h * 0.35f;
                    if (wasCancel != slideToCancel) {
                        updateCancelState(slideToCancel);
                    }
                    return true;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    finishOrCancel();
                    showOverlay(false);
                    return true;
            }
            return false;
        });
    }

    public void attachToContainer(ViewGroup inputArea, ViewGroup window) {
        initHolder(inputArea);
        initOverlay(window);
    }

    public void hide() {
        setVisibility(GONE);
        showOverlay(false);
    }

    public void show() {
        setVisibility(VISIBLE);
    }

    private boolean checkAudioPermission() {
        return PermissionComponent.hasAllPermissions(getContext(), Manifest.permission.RECORD_AUDIO);
    }

    private void startRecording() {
        try {
            File dir = getContext().getCacheDir();
            String name = "voice_" + new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date()) + ".m4a";
            outFile = new File(dir, name);

            recorder = new MediaRecorder();
            recorder.setAudioSource(MediaRecorder.AudioSource.MIC);
            recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
            recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
            recorder.setAudioSamplingRate(16000);
            recorder.setAudioEncodingBitRate(64000);
            recorder.setOutputFile(outFile.getAbsolutePath());
            recorder.prepare();
            recorder.start();
            recording = true;
            startTimeMs = System.currentTimeMillis();
            slideToCancel = false;
            updateCancelState(false);
            if (callback != null) callback.onStart();
            uiHandler.postDelayed(amplitudePollRunnable, 120);
        } catch (IOException | RuntimeException e) {
            recording = false;
        }
    }

    private void showOverlay(boolean show) {
        if (overlayView == null) return;
        try {
            overlayView.setVisibility(show ? View.VISIBLE : View.GONE);
        } catch (Throwable t) {
        }
    }

    /**
     * 切换正常录音 / 上滑取消的 UI 状态
     *
     * @param cancel true = 上滑取消状态，false = 正常录音状态
     */
    private void updateCancelState(boolean cancel) {
        if (voiceBar != null) {
            voiceBar.setBackgroundResource(cancel
                    ? R.drawable.bg_voice_bar_cancel
                    : R.drawable.bg_voice_bar_recording);
        }
        if (voiceArc != null) {
            voiceArc.setBackgroundResource(cancel
                    ? R.drawable.bg_voice_arc_cancel
                    : R.drawable.bg_voice_arc_recording);
        }
        if (voiceHint != null) {
            voiceHint.setText(cancel ? "松开取消" : "松开发送，上滑取消");
        }
    }

    private final Runnable amplitudePollRunnable = new Runnable() {
        @Override
        public void run() {
            if (!recording || recorder == null) return;
            try {
                int amp = recorder.getMaxAmplitude();
                float level = Math.min(1f, amp / 32767f);
                applyMicScale(level);
            } catch (Exception ignored) {
            }
            uiHandler.postDelayed(this, 120);
        }
    };

    /**
     * 根据音量大小缩放麦克风图标，提供录音反馈
     *
     * @param level 0..1
     */
    private void applyMicScale(float level) {
        if (micIcon == null) return;
        float s = 1f + level * 0.3f;
        micIcon.setScaleX(s);
        micIcon.setScaleY(s);
    }

    private void finishOrCancel() {
        if (!recording) return;
        long duration = System.currentTimeMillis() - startTimeMs;
        stopRecording();
        if (slideToCancel) {
            if (outFile != null && outFile.exists()) outFile.delete();
            if (callback != null) callback.onCancel();
        } else {
            if (duration < 800) {
                if (outFile != null && outFile.exists()) outFile.delete();
                if (callback != null) callback.onTooShort();
            } else {
                if (callback != null)
                    callback.onFinish(outFile != null ? outFile.toString() : null, duration);
            }
        }
    }

    private void stopRecording() {
        try {
            uiHandler.removeCallbacks(amplitudePollRunnable);
            if (recorder != null) {
                try {
                    recorder.stop();
                } catch (RuntimeException ignored) {
                }
                recorder.reset();
                recorder.release();
                recorder = null;
            }
        } catch (Exception ignored) {
        }
        recording = false;
    }

    public void setCallback(Callback cb) {
        this.callback = cb;
    }

    private static class ActivityCompatWrapper {
        static void requestAudioPermission(Activity activity) {
            try {
                PermissionComponent.requestPermissions(activity, 1002, Manifest.permission.RECORD_AUDIO);
            } catch (Throwable t) {
            }
        }
    }
}

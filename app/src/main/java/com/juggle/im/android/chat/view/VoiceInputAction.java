package com.juggle.im.android.chat.view;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.drawable.ColorDrawable;
import android.media.MediaRecorder;
import android.os.Handler;
import android.os.Looper;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.PopupWindow;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.juggle.im.android.R;
import com.juggle.im.android.utils.PermissionComponent;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Random;

/**
 * 语音输入组件
 *
 * <p>按住说话录音，上滑取消。UI 分两种状态：</p>
 * <ul>
 *   <li>正常录音：半透明遮罩 + 中部蓝色语音条 + 底部浅色大弧形面板 + "松开发送 上滑取消"</li>
 *   <li>上滑取消：底部弧形变灰，语音条变红，提示文案变为红色的"松开取消"</li>
 * </ul>
 */
public class VoiceInputAction extends FrameLayout {
    private static final String TAG = "VoiceInputAction";
    private static final int[] WAVE_BAR_IDS = new int[]{
            R.id.wave_bar_1,
            R.id.wave_bar_2,
            R.id.wave_bar_3,
            R.id.wave_bar_4,
            R.id.wave_bar_5,
            R.id.wave_bar_6,
            R.id.wave_bar_7,
            R.id.wave_bar_8,
            R.id.wave_bar_9,
            R.id.wave_bar_10,
            R.id.wave_bar_11
    };

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
    private PopupWindow overlayPopupWindow;
    private View overlayAnchorView;
    private View voiceBar;
    private View voiceArc;
    private ImageView micIcon;
    private TextView voiceHint;
    private View[] waveBars = new View[WAVE_BAR_IDS.length];
    private float downRawY;
    private final Random waveRandom = new Random();

    private Handler uiHandler = new Handler(Looper.getMainLooper());

    public VoiceInputAction(@NonNull Context context, AttributeSet attrs) {
        super(context, attrs);
        LayoutInflater.from(context).inflate(R.layout.voice_input_action, this, true);
    }

    private void initOverlay(ViewGroup root) {
        if (overlayView == null) {
            LayoutInflater li = LayoutInflater.from(getContext());
            overlayView = li.inflate(R.layout.voice_record_overlay, root, false);
            voiceBar = overlayView.findViewById(R.id.voice_bar);
            voiceArc = overlayView.findViewById(R.id.voice_arc);
            micIcon = overlayView.findViewById(R.id.iv_mic_icon);
            voiceHint = overlayView.findViewById(R.id.voice_hint);
            bindWaveBars();
            overlayPopupWindow = new PopupWindow(
                    overlayView,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    false);
            overlayPopupWindow.setClippingEnabled(false);
            overlayPopupWindow.setTouchable(false);
            overlayPopupWindow.setOutsideTouchable(false);
            overlayPopupWindow.setBackgroundDrawable(new ColorDrawable(0x00000000));
        }
        overlayAnchorView = root;
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
                    downRawY = event.getRawY();
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
                    // tips: 上滑超过固定距离后进入取消态，避免受控件自身高度影响
                    float slideDistance = downRawY - event.getRawY();
                    boolean wasCancel = slideToCancel;
                    slideToCancel = slideDistance > dp(88);
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

    /**
     * 每次切回语音输入态时，确保遮罩和音波条状态恢复可用。
     */
    private void ensureOverlayReady() {
        if (overlayView == null) return;
        bindWaveBars();
        updateCancelState(false);
        if (voiceBar != null) {
            voiceBar.setScaleX(1f);
            voiceBar.setScaleY(1f);
        }
    }

    public void hide() {
        setVisibility(GONE);
        showOverlay(false);
    }

    public void show() {
        setVisibility(VISIBLE);
        ensureOverlayReady();
    }

    private boolean checkAudioPermission() {
        return PermissionComponent.hasAllPermissions(getContext(), Manifest.permission.RECORD_AUDIO);
    }

    private void startRecording() {
        try {
            uiHandler.removeCallbacks(amplitudePollRunnable);
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
        if (overlayView == null || overlayPopupWindow == null) return;
        try {
            if (show) {
                bindWaveBars();
                updateCancelState(false);
                if (!overlayPopupWindow.isShowing() && overlayAnchorView != null) {
                    overlayPopupWindow.showAtLocation(overlayAnchorView, Gravity.NO_GRAVITY, 0, 0);
                }
                overlayView.setVisibility(View.VISIBLE);
            } else {
                overlayView.setVisibility(View.GONE);
                resetWaveAnimation();
                if (voiceBar != null) {
                    voiceBar.setScaleX(1f);
                    voiceBar.setScaleY(1f);
                }
                if (overlayPopupWindow.isShowing()) {
                    overlayPopupWindow.dismiss();
                }
            }
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
            voiceHint.setText(cancel ? "松开取消" : "松开发送  上滑取消");
            voiceHint.setTextColor(cancel ? 0xFFFF4D4F : 0xFFFFFFFF);
        }
        updateWaveBarColor(cancel);
    }

    private final Runnable amplitudePollRunnable = new Runnable() {
        @Override
        public void run() {
            if (!recording || recorder == null) return;
            try {
                int amp = recorder.getMaxAmplitude();
                float level = Math.min(1f, amp / 32767f);
                applyMicScale(level);
                applyWaveAnimation(level);
            } catch (Exception ignored) {
            }
            uiHandler.postDelayed(this, 120);
        }
    };

    /**
     * 绑定音波条视图
     */
    private void bindWaveBars() {
        if (overlayView == null) return;
        for (int i = 0; i < WAVE_BAR_IDS.length; i++) {
            waveBars[i] = overlayView.findViewById(WAVE_BAR_IDS[i]);
        }
        updateWaveBarColor(false);
        resetWaveAnimation();
    }

    /**
     * 更新音波条颜色
     *
     * @param cancel true 为取消态
     */
    private void updateWaveBarColor(boolean cancel) {
        int color = cancel ? 0xFFFFFFFF : 0xFFFFFFFF;
        for (View waveBar : waveBars) {
            if (waveBar != null) {
                waveBar.setBackgroundTintList(ColorStateList.valueOf(color));
            }
        }
    }

    /**
     * 根据当前音量刷新音波条高度，形成动态音波效果
     *
     * @param level 0..1
     */
    private void applyWaveAnimation(float level) {
        for (int i = 0; i < waveBars.length; i++) {
            View waveBar = waveBars[i];
            if (waveBar == null) continue;
            int minHeight = dpInt(6);
            int maxHeight = dpInt(22);
            float randomFactor = 0.45f + waveRandom.nextFloat() * 0.55f;
            int targetHeight = minHeight + Math.round((maxHeight - minHeight) * Math.min(1f, level + randomFactor * 0.5f));
            ViewGroup.LayoutParams params = waveBar.getLayoutParams();
            if (params.height != targetHeight) {
                params.height = targetHeight;
                waveBar.setLayoutParams(params);
            }
            waveBar.setAlpha(0.55f + Math.min(0.45f, level * 0.5f + randomFactor * 0.3f));
        }
    }

    /**
     * 重置音波条为静态初始高度
     */
    private void resetWaveAnimation() {
        int[] defaultHeightsDp = new int[]{10, 14, 18, 12, 20, 16, 20, 12, 18, 14, 10};
        for (int i = 0; i < waveBars.length; i++) {
            View waveBar = waveBars[i];
            if (waveBar == null) continue;
            ViewGroup.LayoutParams params = waveBar.getLayoutParams();
            params.height = dpInt(defaultHeightsDp[i]);
            waveBar.setLayoutParams(params);
            waveBar.setAlpha(1f);
        }
    }

    /**
     * 根据音量大小轻微缩放语音条，提供录音反馈
     *
     * @param level 0..1
     */
    private void applyMicScale(float level) {
        if (voiceBar == null) return;
        float s = 1f + level * 0.08f;
        voiceBar.setScaleX(s);
        voiceBar.setScaleY(s);
    }

    /**
     * dp 转 px
     *
     * @param value dp 值
     * @return px 值
     */
    private float dp(float value) {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value,
                getResources().getDisplayMetrics());
    }

    /**
     * dp 转 int px
     *
     * @param value dp 值
     * @return int px 值
     */
    private int dpInt(float value) {
        return Math.round(dp(value));
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

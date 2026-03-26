package com.juggle.im.android.chat.view;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.media.MediaRecorder;
import android.os.Handler;
import android.os.Looper;
import android.util.AttributeSet;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
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
 * VoiceInputAction - a self-contained press-to-record UI.
 * Shows a semi-transparent overlay with center ripple animation driven by audio amplitude.
 * Records to a temporary file and reports result via Callback.
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

    // UI
    private View overlayView; // activity provided overlay (preferred)
    private View rippleCenter; // the view in the overlay that shows ripple
    private TextView hintText;

    private Handler uiHandler = new Handler(Looper.getMainLooper());
    // simple animator state
    private boolean rippleActive = false;

    public VoiceInputAction(@NonNull Context context, AttributeSet attrs) {
        super(context, attrs);
        LayoutInflater.from(context).inflate(R.layout.voice_input_action, this, true);
    }

    private void initOverlay(ViewGroup root) {
        // Prefer using an overlay provided by the hosting Activity so the ripple UI
        // lives in the Activity layout (as requested). If not present, inflate
        // a fallback overlay layout from resources and add it to the window.
        try {
            if (getContext() instanceof Activity) {
                Activity act = (Activity) getContext();
                overlayView = act.findViewById(R.id.voice_record_overlay);
            }
        } catch (Throwable t) {
            overlayView = null;
        }

        if (overlayView != null) {
            // find children inside provided overlay
            rippleCenter = overlayView.findViewById(R.id.voice_ripple_center);
        } else {
            // inflate fallback overlay from resources and add to this view so
            // VoiceInputAction still works even if activity hasn't provided one.
            LayoutInflater li = LayoutInflater.from(getContext());
            overlayView = li.inflate(R.layout.voice_record_overlay, root, false);
            // ensure overlay covers parent
            overlayView.setLayoutParams(new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            root.addView(overlayView);
            rippleCenter = overlayView.findViewById(R.id.voice_ripple_center);
        }
        overlayView.setVisibility(GONE);
        setClickable(true);
        setFocusable(true);
    }

    private void initHolder(ViewGroup inputArea) {
        setVisibility(VISIBLE);
        hintText = inputArea.findViewById(R.id.hold_to_talk);

        // touch handling: emulate press to record on ACTION_DOWN inside the pressed area
        hintText.setOnTouchListener((v, event) -> {
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    if (checkAudioPermission()) {
                        // show overlay (if provided by activity it's likely already in the view hierarchy
                        showOverlay(true);
                        startRecording();
                    } else {
                        // permission missing, attempt to request if host is Activity
                        if (getContext() instanceof Activity) {
                            ActivityCompatWrapper.requestAudioPermission((Activity) getContext());
                        }
                        // still show overlay so user sees UI feedback
                        showOverlay(true);
                    }
                    return true;
                case MotionEvent.ACTION_MOVE:
                    // if user moves finger up beyond some threshold, mark slideToCancel
                    float y = event.getY();
                    float h = getHeight();
                    // if pointer moves to upper 30% area, treat as cancel
                    slideToCancel = y < h * 0.35f;
                    // provide subtle feedback by scaling ripple slightly while sliding
                    applyRippleLevel(slideToCancel ? 0f : 0.2f);
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
            // prepare file
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
            startRipple();
            if (callback != null) callback.onStart();
            // poll amplitude
            uiHandler.postDelayed(amplitudePollRunnable, 120);
        } catch (IOException | RuntimeException e) {
            Log.w(TAG, "startRecording failed", e);
            recording = false;
            stopRipple();
        }
    }

    private void showOverlay(boolean show) {
        if (overlayView == null) return;
        try {
            overlayView.setVisibility(show ? View.VISIBLE : View.GONE);
        } catch (Throwable t) {
            // ignore
        }
    }

    private final Runnable amplitudePollRunnable = new Runnable() {
        @Override
        public void run() {
            if (!recording || recorder == null) return;
            try {
                int amp = recorder.getMaxAmplitude();
                // convert to a float 0..1
                float level = Math.min(1f, amp / 32767f);
                applyRippleLevel(level);
            } catch (Exception ignored) {
            }
            uiHandler.postDelayed(this, 120);
        }
    };

    private void finishOrCancel() {
        if (!recording) return;
        long duration = System.currentTimeMillis() - startTimeMs;
        stopRecording();
        if (slideToCancel) {
            // delete file
            if (outFile != null && outFile.exists()) outFile.delete();
            if (callback != null) callback.onCancel();
        } else {
            if (duration < 800) {
                // too short
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
                    // stop can throw if start failed
                    Log.e("voice", "stop failed", ignored);
                }
                recorder.reset();
                recorder.release();
                recorder = null;
            }
        } catch (Exception ignored) {
        }
        recording = false;
        stopRipple();
    }

    public void setCallback(Callback cb) {
        this.callback = cb;
    }

    private void startRipple() {
        rippleActive = true;
        if (rippleCenter != null) {
            rippleCenter.setScaleX(1f);
            rippleCenter.setScaleY(1f);
            rippleCenter.setAlpha(1f);
            // kick off a subtle pulse to make it feel alive
            animateRipplePulse();
        }
    }

    private void stopRipple() {
        rippleActive = false;
        if (rippleCenter != null) {
            rippleCenter.animate().scaleX(1f).scaleY(1f).alpha(0.6f).setDuration(150).start();
        }
    }

    private void applyRippleLevel(float level) {
        // level is 0..1; map to scale 1.0..1.6 and alpha 0.6..1.0
        if (rippleCenter == null) return;
        float s = 1f + level * 0.6f;
        float a = 0.6f + Math.min(1f, level) * 0.4f;
        rippleCenter.setScaleX(s);
        rippleCenter.setScaleY(s);
        rippleCenter.setAlpha(a);
    }

    private void animateRipplePulse() {
        if (!rippleActive || rippleCenter == null) return;
        rippleCenter.animate().scaleX(1.05f).scaleY(1.05f).alpha(1f).setDuration(400).withEndAction(() -> {
            if (!rippleActive) return;
            rippleCenter.animate().scaleX(1f).scaleY(1f).alpha(0.9f).setDuration(400).withEndAction(this::animateRipplePulse).start();
        }).start();
    }

    // Small wrapper to request permission without pulling androidx code directly here
    private static class ActivityCompatWrapper {
        static void requestAudioPermission(Activity activity) {
            try {
                PermissionComponent.requestPermissions(activity, 1002, Manifest.permission.RECORD_AUDIO);
            } catch (Throwable t) {
                // ignore
            }
        }
    }

}

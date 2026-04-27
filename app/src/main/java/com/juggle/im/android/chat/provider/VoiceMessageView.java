package com.juggle.im.android.chat.provider;

import android.annotation.SuppressLint;
import android.content.res.ColorStateList;
import android.media.MediaPlayer;
import android.text.TextUtils;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;

import com.juggle.im.android.R;
import com.juggle.im.android.model.UiMessage;
import com.juggle.im.model.Message;
import com.juggle.im.model.messages.VoiceMessage;

import java.io.IOException;
import java.util.Random;

/**
 * 语音消息视图。
 */
public class VoiceMessageView extends MessageView<UiMessage, VoiceMessage> {
    private static final int VOICE_WIDTH_SHORT_DP = 160;
    private static final int VOICE_WIDTH_MEDIUM_DP = 185;
    private static final int VOICE_WIDTH_LONG_DP = 245;
    private static final int VOICE_WIDTH_MAX_DP = 300;

    private static final int WAVE_COUNT_SHORT = 16;
    private static final int WAVE_COUNT_MEDIUM = 20;
    private static final int WAVE_COUNT_LONG = 30;
    private static final int WAVE_COUNT_MAX = 39;

    private static final int WAVE_BAR_WIDTH_DP = 2;
    private static final int WAVE_BAR_MARGIN_END_DP = 2;
    private static final int WAVE_BAR_MIN_HEIGHT_DP = 8;
    private static final int WAVE_BAR_MAX_HEIGHT_DP = 20;
    private static final int WAVE_ANIMATE_INTERVAL_MS = 120;

    private static MediaPlayer currentPlayer;
    private static Runnable currentStopAnimation;
    private static boolean currentPreparing;
    private final Random waveRandom = new Random();

    private static final class VoiceVisualSpec {
        final int bubbleWidthDp;
        final int waveCount;

        VoiceVisualSpec(int bubbleWidthDp, int waveCount) {
            this.bubbleWidthDp = bubbleWidthDp;
            this.waveCount = waveCount;
        }
    }

    /**
     * 语音消息内容视图。
     *
     * @param root 消息内容容器
     */
    public VoiceMessageView(@NonNull ViewGroup root) {
        super(root, R.layout.content_voice);
    }

    /**
     * 绑定语音消息内容。
     *
     * @param m       消息对象
     * @param voice   语音消息内容
     * @param isGroup 是否群聊
     */
    @SuppressLint("DefaultLocale")
    @Override
    public void bindItem(UiMessage m, VoiceMessage voice, boolean isGroup) {
        View voiceContainer = this.itemView.findViewById(R.id.layout_voice_container);
        LinearLayout btnPlay = this.itemView.findViewById(R.id.button_play_voice);
        LinearLayout waveBarsContainer = this.itemView.findViewById(R.id.layout_voice_wave_bars);
        ProgressBar loadingView = this.itemView.findViewById(R.id.progress_voice_loading);
        TextView tvDuration = this.itemView.findViewById(R.id.text_voice_duration);
        if (voiceContainer == null || btnPlay == null || waveBarsContainer == null
                || loadingView == null || tvDuration == null) {
            return;
        }

        final boolean isSend = m.getDirection() == Message.MessageDirection.SEND;
        final int durationMs = Math.max(1000, voice.getDuration());
        final int durationSeconds = Math.max(1, durationMs / 1000);
        final VoiceVisualSpec visualSpec = resolveVoiceVisualSpec(durationMs / 1000f);
        final String url = !TextUtils.isEmpty(voice.getUrl()) ? voice.getUrl() : voice.getLocalPath();
        final View[] waveBars = buildWaveBars(waveBarsContainer, visualSpec.waveCount);

        // 简要描述：对齐 snailchat 的时长分档，直接按档位控制语音气泡宽度和波形数量。
        ViewGroup.LayoutParams containerParams = voiceContainer.getLayoutParams();
        if (containerParams != null) {
            containerParams.width = dp(voiceContainer, visualSpec.bubbleWidthDp);
            voiceContainer.setLayoutParams(containerParams);
        }

        int waveColor = isSend ? 0xFFFFFFFF : ContextCompat.getColor(itemView.getContext(), R.color.app_primary);
        updateWaveBarColor(waveBars, waveColor);
        updateLoadingColor(loadingView, waveColor);
        setLoadingState(loadingView, waveBarsContainer, false);
        resetWaveAnimation(waveBars);
        if (isSend) {
            tvDuration.setTextColor(0xFFFFFFFF);
        } else {
            tvDuration.setTextColor(ContextCompat.getColor(itemView.getContext(), R.color.app_primary));
        }
        tvDuration.setText(durationSeconds + "'");

        if (TextUtils.isEmpty(url)) {
            voiceContainer.setEnabled(false);
            voiceContainer.setOnClickListener(null);
            voiceContainer.setOnLongClickListener(null);
            btnPlay.setAlpha(0.35f);
            return;
        }
        voiceContainer.setEnabled(true);
        btnPlay.setAlpha(1f);
        voiceContainer.setOnClickListener(v -> togglePlay(
                url,
                waveBars,
                waveColor,
                waveBarsContainer,
                loadingView
        ));
        voiceContainer.setOnLongClickListener(v -> {
            View parent = (View) this.itemView.getParent();
            if (parent != null) {
                parent.performLongClick();
            }
            return true;
        });
    }

    /**
     * 切换语音播放状态。
     *
     * @param url 语音地址
     * @param waveBars 音波条
     * @param waveColor 音波颜色
     * @param waveBarsContainer 音波容器
     * @param loadingView 加载态控件
     */
    private void togglePlay(@NonNull String url,
                            @NonNull View[] waveBars,
                            int waveColor,
                            @NonNull View waveBarsContainer,
                            @NonNull ProgressBar loadingView) {
        if (currentPlayer != null && currentPreparing) {
            stopCurrentPlay();
            return;
        }
        if (currentPlayer != null && currentPlayer.isPlaying()) {
            stopCurrentPlay();
            return;
        }
        stopCurrentPlay();

        final Runnable[] animationHolder = new Runnable[1];
        currentPreparing = true;
        setLoadingState(loadingView, waveBarsContainer, true);
        currentStopAnimation = () -> {
            if (animationHolder[0] != null) {
                itemView.removeCallbacks(animationHolder[0]);
            }
            currentPreparing = false;
            setLoadingState(loadingView, waveBarsContainer, false);
            resetWaveAnimation(waveBars);
            updateWaveBarColor(waveBars, waveColor);
        };

        currentPlayer = new MediaPlayer();
        try {
            currentPlayer.setDataSource(url);
            currentPlayer.prepareAsync();
            currentPlayer.setOnPreparedListener(mp -> {
                if (currentPlayer != mp) {
                    return;
                }
                currentPreparing = false;
                setLoadingState(loadingView, waveBarsContainer, false);
                mp.start();
                animationHolder[0] = createWaveAnimationRunnable(waveBars);
                itemView.post(animationHolder[0]);
            });
            currentPlayer.setOnCompletionListener(mp -> {
                if (currentPlayer == mp) {
                    stopCurrentPlay();
                }
            });
            currentPlayer.setOnErrorListener((mp, what, extra) -> {
                if (currentPlayer == mp) {
                    stopCurrentPlay();
                }
                return true;
            });
        } catch (IOException e) {
            stopCurrentPlay();
        }
    }

    private Runnable createWaveAnimationRunnable(@NonNull View[] waveBars) {
        return new Runnable() {
            @Override
            public void run() {
                if (currentPlayer == null || !currentPlayer.isPlaying()) {
                    return;
                }
                for (View waveBar : waveBars) {
                    if (waveBar == null) continue;
                    int targetHeight = dp(waveBar, WAVE_BAR_MIN_HEIGHT_DP
                            + waveRandom.nextInt(WAVE_BAR_MAX_HEIGHT_DP - WAVE_BAR_MIN_HEIGHT_DP + 1));
                    ViewGroup.LayoutParams params = waveBar.getLayoutParams();
                    if (params.height != targetHeight) {
                        params.height = targetHeight;
                        waveBar.setLayoutParams(params);
                    }
                    waveBar.setAlpha(0.65f + waveRandom.nextFloat() * 0.35f);
                }
                itemView.postDelayed(this, WAVE_ANIMATE_INTERVAL_MS);
            }
        };
    }

    private void stopCurrentPlay() {
        if (currentStopAnimation != null) {
            currentStopAnimation.run();
            currentStopAnimation = null;
        }
        if (currentPlayer == null) {
            return;
        }
        try {
            if (currentPlayer.isPlaying()) {
                currentPlayer.stop();
            }
        } catch (Exception ignored) {
        } finally {
            try {
                currentPlayer.release();
            } catch (Exception ignored) {
            }
            currentPlayer = null;
        }
    }

    /**
     * 简要描述：根据波形数量动态创建波形条，避免固定 9 根导致长语音视觉过短。
     *
     * @param container 波形容器
     * @param waveCount 波形数量
     * @return 波形数组
     */
    private View[] buildWaveBars(@NonNull LinearLayout container, int waveCount) {
        container.removeAllViews();
        View[] waveBars = new View[waveCount];
        for (int i = 0; i < waveCount; i++) {
            View waveBar = new View(container.getContext());
            int defaultHeightDp = WAVE_BAR_MIN_HEIGHT_DP
                    + waveRandom.nextInt(WAVE_BAR_MAX_HEIGHT_DP - WAVE_BAR_MIN_HEIGHT_DP + 1);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    dp(container, WAVE_BAR_WIDTH_DP),
                    dp(container, defaultHeightDp)
            );
            if (i < waveCount - 1) {
                params.setMarginEnd(dp(container, WAVE_BAR_MARGIN_END_DP));
            }
            waveBar.setLayoutParams(params);
            waveBar.setBackgroundResource(R.drawable.bg_voice_wave_bar);
            waveBar.setTag(defaultHeightDp);
            container.addView(waveBar);
            waveBars[i] = waveBar;
        }
        return waveBars;
    }

    private void updateWaveBarColor(@NonNull View[] waveBars, int color) {
        for (View waveBar : waveBars) {
            if (waveBar != null) {
                waveBar.setBackgroundTintList(ColorStateList.valueOf(color));
            }
        }
    }

    private void resetWaveAnimation(@NonNull View[] waveBars) {
        for (View waveBar : waveBars) {
            if (waveBar == null) continue;
            ViewGroup.LayoutParams params = waveBar.getLayoutParams();
            int defaultHeightDp = WAVE_BAR_MIN_HEIGHT_DP;
            Object heightTag = waveBar.getTag();
            if (heightTag instanceof Integer) {
                defaultHeightDp = (Integer) heightTag;
            }
            params.height = dp(waveBar, defaultHeightDp);
            waveBar.setLayoutParams(params);
            waveBar.setAlpha(1f);
        }
    }

    /**
     * 简要描述：播放前显示加载动画，onPrepared 后恢复波形并开始播放，避免弱网点击无反馈。
     *
     * @param loadingView 加载控件
     * @param waveBarsContainer 波形容器
     * @param loading 是否加载中
     */
    private void setLoadingState(@NonNull ProgressBar loadingView, @NonNull View waveBarsContainer, boolean loading) {
        loadingView.setVisibility(loading ? View.VISIBLE : View.GONE);
        waveBarsContainer.setVisibility(loading ? View.INVISIBLE : View.VISIBLE);
    }

    /**
     * 设置加载控件颜色，使发送端和接收端的视觉保持一致。
     *
     * @param loadingView 加载控件
     * @param color 颜色值
     */
    private void updateLoadingColor(@NonNull ProgressBar loadingView, int color) {
        loadingView.setIndeterminateTintList(ColorStateList.valueOf(color));
    }

    /**
     * 按时长返回语音气泡宽度与波形数量配置。
     *
     * @param seconds 语音秒数
     * @return 视觉规格
     */
    @NonNull
    private VoiceVisualSpec resolveVoiceVisualSpec(float seconds) {
        if (seconds < 10f) {
            return new VoiceVisualSpec(VOICE_WIDTH_SHORT_DP, WAVE_COUNT_SHORT);
        }
        if (seconds < 20f) {
            return new VoiceVisualSpec(VOICE_WIDTH_MEDIUM_DP, WAVE_COUNT_MEDIUM);
        }
        if (seconds < 40f) {
            return new VoiceVisualSpec(VOICE_WIDTH_LONG_DP, WAVE_COUNT_LONG);
        }
        return new VoiceVisualSpec(VOICE_WIDTH_MAX_DP, WAVE_COUNT_MAX);
    }

    private int dp(@NonNull View view, int value) {
        return Math.round(value * view.getResources().getDisplayMetrics().density);
    }
}

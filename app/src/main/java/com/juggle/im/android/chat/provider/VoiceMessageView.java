package com.juggle.im.android.chat.provider;

import android.annotation.SuppressLint;
import android.content.res.ColorStateList;
import android.media.MediaPlayer;
import android.text.TextUtils;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.juggle.im.android.R;
import com.juggle.im.android.chat.utils.VoiceMessageDownloader;
import com.juggle.im.android.model.UiMessage;
import com.juggle.im.model.Message;
import com.juggle.im.model.MediaMessageContent;
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

    private static final int WAVE_COUNT_SHORT = 28;
    private static final int WAVE_COUNT_MEDIUM = 34;
    private static final int WAVE_COUNT_LONG = 42;
    private static final int WAVE_COUNT_MAX = 50;

    private static final int WAVE_BAR_WIDTH_DP = 2;
    private static final int WAVE_BAR_MARGIN_END_DP = 2;
    private static final int WAVE_BAR_MIN_HEIGHT_DP = 8;
    private static final int WAVE_BAR_MAX_HEIGHT_DP = 20;
    private static final int WAVE_ANIMATE_INTERVAL_MS = 120;
    private static final int WAVE_PREPARING_INTERVAL_MS = 90;

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
        FrameLayout waveArea = this.itemView.findViewById(R.id.layout_voice_wave_area);
        LinearLayout waveBarsContainer = this.itemView.findViewById(R.id.layout_voice_wave_bars);
        TextView tvDuration = this.itemView.findViewById(R.id.text_voice_duration);
        if (voiceContainer == null || btnPlay == null || waveArea == null || waveBarsContainer == null
                || tvDuration == null) {
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
        // tips：语音条展示区要扣掉时长文本和左右留白，宽度跟随气泡主体铺满，避免短条悬空在左侧。
        ViewGroup.LayoutParams waveAreaParams = waveArea.getLayoutParams();
        if (waveAreaParams instanceof LinearLayout.LayoutParams) {
            ((LinearLayout.LayoutParams) waveAreaParams).width = 0;
            ((LinearLayout.LayoutParams) waveAreaParams).weight = 1f;
            waveArea.setLayoutParams(waveAreaParams);
        }
        adjustWaveBarWidths(waveArea, waveBars);

        int waveColor = isSend ? 0xFFFFFFFF : ContextCompat.getColor(itemView.getContext(), R.color.app_primary);
        updateWaveBarColor(waveBars, waveColor);
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
                m.getMessage(),
                url,
                waveBars,
                waveColor,
                btnPlay
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
     * TIPS: 播放一律走本地文件（对齐 iOS）——本地已有就直接播，缺失时先用 SDK 下载再播；
     * 直接把远端 URL 丢给 MediaPlayer 会让每次点击都重新走网络缓冲，出声延迟明显。
     *
     * @param message 语音消息，用于按需下载媒体文件
     * @param fallbackUrl 兜底地址：拿不到本地文件且下载失败时使用
     * @param waveBars 音波条
     * @param waveColor 音波颜色
     * @param playButton 播放点击区域
     */
    private void togglePlay(@Nullable Message message,
                            @NonNull String fallbackUrl,
                            @NonNull View[] waveBars,
                            int waveColor,
                            @NonNull View playButton) {
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
        playButton.setAlpha(0.82f);
        animationHolder[0] = createPreparingAnimationRunnable(waveBars, waveColor);
        itemView.post(animationHolder[0]);
        currentStopAnimation = () -> {
            if (animationHolder[0] != null) {
                itemView.removeCallbacks(animationHolder[0]);
            }
            currentPreparing = false;
            playButton.setAlpha(1f);
            resetWaveAnimation(waveBars);
            updateWaveBarColor(waveBars, waveColor);
        };

        String localPath = message == null
                ? null
                : VoiceMessageDownloader.playableLocalPath(
                        message.getContent() instanceof MediaMessageContent
                                ? (MediaMessageContent) message.getContent()
                                : null);
        if (localPath != null) {
            startPlayback(localPath, animationHolder, waveBars, playButton);
            return;
        }

        final Runnable[] pendingAnimationHolder = animationHolder;
        VoiceMessageDownloader.ensureLocal(message, new VoiceMessageDownloader.Callback() {
            @Override
            public void onReady(@NonNull String readyPath) {
                if (!currentPreparing) {
                    // 下载期间用户已取消播放
                    return;
                }
                startPlayback(readyPath, pendingAnimationHolder, waveBars, playButton);
            }

            @Override
            public void onFailed() {
                if (!currentPreparing) {
                    return;
                }
                // 下载失败时退回边下边播，至少不至于点了没反应
                startPlayback(fallbackUrl, pendingAnimationHolder, waveBars, playButton);
            }
        });
    }

    /**
     * 以指定数据源开始播放。
     *
     * @param dataSource 本地文件路径或远端地址
     * @param animationHolder 动画持有器，准备完成后替换为播放动画
     * @param waveBars 音波条
     * @param playButton 播放点击区域
     */
    private void startPlayback(@NonNull String dataSource,
                               @NonNull Runnable[] animationHolder,
                               @NonNull View[] waveBars,
                               @NonNull View playButton) {
        currentPlayer = new MediaPlayer();
        try {
            currentPlayer.setDataSource(dataSource);
            currentPlayer.prepareAsync();
            currentPlayer.setOnPreparedListener(mp -> {
                if (currentPlayer != mp) {
                    return;
                }
                currentPreparing = false;
                playButton.setAlpha(1f);
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

    private Runnable createPreparingAnimationRunnable(@NonNull View[] waveBars, int waveColor) {
        return new Runnable() {
            private int tick;

            @Override
            public void run() {
                if (!currentPreparing) {
                    return;
                }
                for (int i = 0; i < waveBars.length; i++) {
                    View waveBar = waveBars[i];
                    if (waveBar == null) {
                        continue;
                    }
                    int phase = (tick + i) % 6;
                    int targetHeight = dp(waveBar, WAVE_BAR_MIN_HEIGHT_DP + Math.min(phase, 5 - phase) * 2);
                    ViewGroup.LayoutParams params = waveBar.getLayoutParams();
                    if (params.height != targetHeight) {
                        params.height = targetHeight;
                        waveBar.setLayoutParams(params);
                    }
                    waveBar.setAlpha(0.45f + Math.min(phase, 5 - phase) * 0.08f);
                    waveBar.setBackgroundTintList(ColorStateList.valueOf(waveColor));
                }
                tick = (tick + 1) % 6;
                itemView.postDelayed(this, WAVE_PREPARING_INTERVAL_MS);
            }
        };
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

    /**
     * tips：保持波形条本身纤细，通过分配间距来铺满可用宽度，避免每根条被拉得过宽影响观感。
     *
     * @param waveArea 波形展示区域
     * @param waveBars 波形条数组
     */
    private void adjustWaveBarWidths(@NonNull View waveArea, @NonNull View[] waveBars) {
        waveArea.post(() -> {
            int availableWidth = waveArea.getWidth();
            if (availableWidth <= 0 || waveBars.length == 0) {
                return;
            }
            int barWidth = dp(waveArea, WAVE_BAR_WIDTH_DP);
            int totalBarWidth = barWidth * waveBars.length;
            int gapCount = Math.max(0, waveBars.length - 1);
            int margin = gapCount == 0 ? 0 : Math.max(dp(waveArea, WAVE_BAR_MARGIN_END_DP),
                    (availableWidth - totalBarWidth) / gapCount);
            for (int i = 0; i < waveBars.length; i++) {
                View waveBar = waveBars[i];
                if (waveBar == null) {
                    continue;
                }
                LinearLayout.LayoutParams params = (LinearLayout.LayoutParams) waveBar.getLayoutParams();
                params.width = barWidth;
                params.setMarginEnd(i < waveBars.length - 1 ? margin : 0);
                waveBar.setLayoutParams(params);
            }
        });
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

package com.juggle.im.android.chat.provider;

import android.annotation.SuppressLint;
import android.content.res.ColorStateList;
import android.media.MediaPlayer;
import android.text.TextUtils;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
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
 * Voice message view.
 */
public class VoiceMessageView extends MessageView<UiMessage, VoiceMessage> {
    private static final int MIN_PLAY_BAR_WIDTH_DP = 72;
    private static final int MAX_PLAY_BAR_WIDTH_DP = 176;
    private static final int BASE_PLAY_BAR_WIDTH_DP = 64;
    private static final int WIDTH_STEP_PER_SECOND_DP = 4;
    private static final int[] MESSAGE_WAVE_BAR_IDS = new int[]{
            R.id.msg_wave_bar_1,
            R.id.msg_wave_bar_2,
            R.id.msg_wave_bar_3,
            R.id.msg_wave_bar_4,
            R.id.msg_wave_bar_5,
            R.id.msg_wave_bar_6,
            R.id.msg_wave_bar_7,
            R.id.msg_wave_bar_8,
            R.id.msg_wave_bar_9
    };
    private static MediaPlayer currentPlayer;
    private static Runnable currentStopAnimation;
    private final Random waveRandom = new Random();

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
        TextView tvDuration = this.itemView.findViewById(R.id.text_voice_duration);
        if (voiceContainer == null || btnPlay == null || tvDuration == null) {
            return;
        }

        final boolean isSend = m.getDirection() == Message.MessageDirection.SEND;
        final int durationSeconds = Math.max(1, voice.getDuration() / 1000);
        final String url = !TextUtils.isEmpty(voice.getUrl()) ? voice.getUrl() : voice.getLocalPath();
        final View[] waveBars = collectWaveBars(btnPlay);

        // tips: 不再反推整个气泡总宽度，而是直接按时长控制播放条本身宽度，让气泡跟随内容自然包裹。
        int playBarWidthDp = BASE_PLAY_BAR_WIDTH_DP + durationSeconds * WIDTH_STEP_PER_SECOND_DP;
        playBarWidthDp = Math.max(MIN_PLAY_BAR_WIDTH_DP, Math.min(MAX_PLAY_BAR_WIDTH_DP, playBarWidthDp));
        ViewGroup.LayoutParams playParams = btnPlay.getLayoutParams();
        if (playParams != null) {
            playParams.width = dp(btnPlay, playBarWidthDp);
            btnPlay.setLayoutParams(playParams);
        }
        ViewGroup.LayoutParams containerParams = voiceContainer.getLayoutParams();
        if (containerParams != null) {
            containerParams.width = ViewGroup.LayoutParams.WRAP_CONTENT;
            voiceContainer.setLayoutParams(containerParams);
        }

        int waveColor = isSend ? 0xFFFFFFFF : ContextCompat.getColor(itemView.getContext(), R.color.app_primary);
        updateWaveBarColor(waveBars, waveColor);
        resetWaveAnimation(waveBars);
        if (isSend) {
            tvDuration.setTextColor(0xFFFFFF);
        } else {
            tvDuration.setTextColor(ContextCompat.getColor(itemView.getContext(), R.color.app_primary));
        }
        tvDuration.setText(durationSeconds + "'");

        if (TextUtils.isEmpty(url)) {
            voiceContainer.setEnabled(false);
            btnPlay.setAlpha(0.35f);
            return;
        }
        voiceContainer.setEnabled(true);
        voiceContainer.setOnClickListener(v -> togglePlay(url, waveBars, waveColor));
        voiceContainer.setOnLongClickListener(v -> {
            View parent = (View) this.itemView.getParent();
            if (parent != null) {
                parent.performLongClick();
            }
            return false;
        });
    }

    /**
     * 切换语音播放状态。
     *
     * @param url 语音地址
     * @param waveBars 音波条
     * @param waveColor 音波颜色
     */
    private void togglePlay(@NonNull String url, @NonNull View[] waveBars, int waveColor) {
        if (currentPlayer != null && currentPlayer.isPlaying()) {
            stopCurrentPlay();
            return;
        }
        stopCurrentPlay();
        final Runnable[] animationHolder = new Runnable[1];
        currentPlayer = new MediaPlayer();
        try {
            currentPlayer.setDataSource(url);
            currentPlayer.prepareAsync();
            currentPlayer.setOnPreparedListener(mp -> {
                mp.start();
                animationHolder[0] = createWaveAnimationRunnable(waveBars);
                currentStopAnimation = () -> {
                    if (animationHolder[0] != null) {
                        itemView.removeCallbacks(animationHolder[0]);
                    }
                    resetWaveAnimation(waveBars);
                    updateWaveBarColor(waveBars, waveColor);
                };
                itemView.post(animationHolder[0]);
            });
            currentPlayer.setOnCompletionListener(mp -> stopCurrentPlay());
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
                    int targetHeight = dp(waveBar, 8 + waveRandom.nextInt(14));
                    ViewGroup.LayoutParams params = waveBar.getLayoutParams();
                    if (params.height != targetHeight) {
                        params.height = targetHeight;
                        waveBar.setLayoutParams(params);
                    }
                    waveBar.setAlpha(0.65f + waveRandom.nextFloat() * 0.35f);
                }
                itemView.postDelayed(this, 120);
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

    private View[] collectWaveBars(@NonNull ViewGroup container) {
        View[] waveBars = new View[MESSAGE_WAVE_BAR_IDS.length];
        for (int i = 0; i < MESSAGE_WAVE_BAR_IDS.length; i++) {
            waveBars[i] = container.findViewById(MESSAGE_WAVE_BAR_IDS[i]);
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
        int[] defaultHeightsDp = new int[]{10, 14, 18, 12, 20, 12, 18, 14, 10};
        for (int i = 0; i < waveBars.length; i++) {
            View waveBar = waveBars[i];
            if (waveBar == null) continue;
            ViewGroup.LayoutParams params = waveBar.getLayoutParams();
            params.height = dp(waveBar, defaultHeightsDp[i]);
            waveBar.setLayoutParams(params);
            waveBar.setAlpha(1f);
        }
    }

    private int dp(@NonNull View view, int value) {
        return Math.round(value * view.getResources().getDisplayMetrics().density);
    }
}

package com.juggle.im.android.chat.provider;

import android.annotation.SuppressLint;
import android.media.MediaPlayer;
import android.text.TextUtils;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;

import com.juggle.im.android.R;
import com.juggle.im.android.model.UiMessage;
import com.juggle.im.model.Message;
import com.juggle.im.model.messages.VoiceMessage;

import java.io.IOException;

/**
 * Voice message view.
 */
public class VoiceMessageView extends MessageView<UiMessage, VoiceMessage> {
    private static final int MIN_BUBBLE_WIDTH_DP = 116;
    private static final int MAX_BUBBLE_WIDTH_DP = 220;
    private static final int BASE_BUBBLE_WIDTH_DP = 96;
    private static final int WIDTH_STEP_PER_SECOND_DP = 3;

    private MediaPlayer player;

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
        ImageView btnPlay = this.itemView.findViewById(R.id.button_play_voice);
        TextView tvDuration = this.itemView.findViewById(R.id.text_voice_duration);
        if (voiceContainer == null || btnPlay == null || tvDuration == null) {
            return;
        }

        final boolean isSend = m.getDirection() == Message.MessageDirection.SEND;
        final int durationSeconds = Math.max(1, voice.getDuration() / 1000);
        final String url = !TextUtils.isEmpty(voice.getUrl()) ? voice.getUrl() : voice.getLocalPath();

        // 简要描述：按时长动态拉伸气泡宽度，保证短语音不显拥挤、长语音有明显长度区分。
        int widthDp = BASE_BUBBLE_WIDTH_DP + durationSeconds * WIDTH_STEP_PER_SECOND_DP;
        widthDp = Math.max(MIN_BUBBLE_WIDTH_DP, Math.min(MAX_BUBBLE_WIDTH_DP, widthDp));
        ViewGroup.LayoutParams params = voiceContainer.getLayoutParams();
        if (params != null) {
            params.width = dp(voiceContainer, widthDp);
            voiceContainer.setLayoutParams(params);
        }

        btnPlay.setImageResource(R.drawable.ic_msg_type_voice);
        if (isSend) {
            btnPlay.clearColorFilter();
            tvDuration.setTextColor(0xFFFFFF);
        } else {
            btnPlay.setColorFilter(ContextCompat.getColor(itemView.getContext(), R.color.app_primary));
            tvDuration.setTextColor(ContextCompat.getColor(itemView.getContext(), R.color.app_primary));
        }
        tvDuration.setText(durationSeconds + "'");

        if (TextUtils.isEmpty(url)) {
            voiceContainer.setEnabled(false);
            btnPlay.setAlpha(0.35f);
            return;
        }
        voiceContainer.setEnabled(true);
        voiceContainer.setOnClickListener(v -> togglePlay(url, btnPlay));
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
     * @param url     语音地址
     * @param waveView 波形图标
     */
    private void togglePlay(@NonNull String url, @NonNull ImageView waveView) {
        if (player != null && player.isPlaying()) {
            stopCurrentPlay(waveView);
            return;
        }
        stopCurrentPlay(waveView);
        player = new MediaPlayer();
        try {
            player.setDataSource(url);
            player.prepareAsync();
            player.setOnPreparedListener(mp -> {
                mp.start();
                waveView.setAlpha(0.55f);
            });
            player.setOnCompletionListener(mp -> {
                mp.release();
                player = null;
                waveView.setAlpha(1f);
            });
        } catch (IOException e) {
            stopCurrentPlay(waveView);
        }
    }

    private void stopCurrentPlay(@NonNull ImageView waveView) {
        if (player == null) {
            waveView.setAlpha(1f);
            return;
        }
        try {
            if (player.isPlaying()) {
                player.stop();
            }
        } catch (Exception ignored) {
        } finally {
            try {
                player.release();
            } catch (Exception ignored) {
            }
            player = null;
            waveView.setAlpha(1f);
        }
    }

    private int dp(@NonNull View view, int value) {
        return Math.round(value * view.getResources().getDisplayMetrics().density);
    }
}

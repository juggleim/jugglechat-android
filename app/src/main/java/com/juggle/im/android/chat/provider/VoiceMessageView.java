package com.juggle.im.android.chat.provider;

import android.annotation.SuppressLint;
import android.media.MediaPlayer;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.juggle.im.android.R;
import com.juggle.im.android.model.UiMessage;
import com.juggle.im.model.messages.VoiceMessage;

import java.io.IOException;

/** Voice message view. */
public class VoiceMessageView extends MessageView<UiMessage, VoiceMessage> {
    private MediaPlayer player;

    public VoiceMessageView(@NonNull ViewGroup root) {
        super(root, R.layout.content_voice);
    }

    @SuppressLint("DefaultLocale")
    @Override
    public void bind(UiMessage m, VoiceMessage voice, boolean isGroup) {
        ImageView btnPlay = itemView.findViewById(R.id.button_play_voice);
        TextView tvDuration = itemView.findViewById(R.id.text_voice_duration);
        int duration = voice.getDuration()/1000;
        final String url = voice.getUrl() == null ? voice.getLocalPath() : voice.getUrl();
        tvDuration.setText(duration == 0 ? "" : String.format("%02d:%02d", duration / 60, duration % 60));
        btnPlay.setVisibility(View.VISIBLE);
        btnPlay.setOnClickListener(v -> {
            if (player != null && player.isPlaying()) {
                player.stop();
                player.release();
                player = null;
                btnPlay.setImageResource(R.drawable.ic_play);
                return;
            }
            player = new MediaPlayer();
            try {
                player.setDataSource(url);
                player.prepareAsync();
                player.setOnPreparedListener(mp -> {
                    mp.start();
                    btnPlay.setImageResource(R.drawable.ic_stop);
                });
                player.setOnCompletionListener(mp -> {
                    mp.release();
                    player = null;
                    btnPlay.setImageResource(R.drawable.ic_play);
                });
            } catch (IOException e) {
                try { player.release(); } catch (Exception ignored) {}
                player = null;
            }
        });
    }
}

package com.juggle.im.android.app;

import static android.view.View.GONE;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.appcompat.widget.SwitchCompat;

import com.juggle.im.android.R;
import com.juggle.im.android.component.AbsAppActivity;

/**
 * 通用设置页面
 */
public class GeneralSettingsActivity extends AbsAppActivity {
    private static final int REQ_CHAT_BACKGROUND = 2001;

    private View rowChatBackground;
    private View rowAppNotify;
    private boolean suppressNotifyCallback;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_general_settings);

        ImageView backView = findViewById(R.id.iv_back);
        TextView titleView = findViewById(R.id.tv_title);
        rowChatBackground = findViewById(R.id.row_chat_background);
        rowAppNotify = findViewById(R.id.row_app_notify);

        titleView.setText("通用设置");
        backView.setOnClickListener(v -> finish());

        setupRow(rowChatBackground, -1, "聊天背景", "", true);
        setupRow(rowAppNotify,-1, "应用内通知", "", false);

        SwitchCompat notifySwitch = rowAppNotify.findViewById(R.id.switch_row);
        ImageView notifyArrow = rowAppNotify.findViewById(R.id.iv_row_arrow);
        TextView notifySubtitle = rowAppNotify.findViewById(R.id.tv_row_subtitle);
        notifyArrow.setVisibility(GONE);
        notifySubtitle.setVisibility(GONE);
        notifySwitch.setVisibility(View.VISIBLE);

        suppressNotifyCallback = true;
        notifySwitch.setChecked(AppSettingsStore.isAppNotifyEnabled(this));
        suppressNotifyCallback = false;

        notifySwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (suppressNotifyCallback) {
                return;
            }
            AppSettingsStore.setAppNotifyEnabled(this, isChecked);
        });

        rowAppNotify.setOnClickListener(v -> {
            suppressNotifyCallback = true;
            notifySwitch.setChecked(!notifySwitch.isChecked());
            suppressNotifyCallback = false;
            AppSettingsStore.setAppNotifyEnabled(this, notifySwitch.isChecked());
        });

        rowChatBackground.setOnClickListener(v -> {
            Intent intent = new Intent(this, ChatBackgroundActivity.class);
            startActivityForResult(intent, REQ_CHAT_BACKGROUND);
        });

        updateBackgroundSubtitle();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_CHAT_BACKGROUND && resultCode == RESULT_OK) {
            updateBackgroundSubtitle();
        }
    }

    private void updateBackgroundSubtitle() {
        TextView subtitle = rowChatBackground.findViewById(R.id.tv_row_subtitle);
        ImageView arrow = rowChatBackground.findViewById(R.id.iv_row_arrow);
        subtitle.setVisibility(View.VISIBLE);
        subtitle.setText("已设置");
        arrow.setVisibility(View.VISIBLE);
    }

    private void setupRow(View row, int iconRes, String title, String subtitle, boolean showArrow) {
        ImageView icon = row.findViewById(R.id.iv_row_icon);
        TextView titleView = row.findViewById(R.id.tv_row_title);
        TextView subtitleView = row.findViewById(R.id.tv_row_subtitle);
        ImageView arrowView = row.findViewById(R.id.iv_row_arrow);

        if (iconRes > 0) {
            icon.setImageResource(iconRes);
        } else {
            icon.setVisibility(GONE);
        }
        titleView.setText(title);

        if (subtitle == null || subtitle.isEmpty()) {
            subtitleView.setVisibility(GONE);
        } else {
            subtitleView.setVisibility(View.VISIBLE);
            subtitleView.setText(subtitle);
        }
        arrowView.setVisibility(showArrow ? View.VISIBLE : GONE);
    }
}

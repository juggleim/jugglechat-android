package com.juggle.im.android.app;

import android.content.Context;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.Nullable;

import com.juggle.im.android.R;

public class BottomNavView extends LinearLayout {

    public interface OnTabClickListener {
        void onTabClicked(int index);
    }

    private OnTabClickListener listener;
    private int selectedIndex = 0;

    private View tabChat;
    private View tabContact;
    private View tabFriend;
    private View tabMe;

    private android.widget.ImageView tabChatIcon;
    private android.widget.TextView tabChatLabel;
    private android.widget.ImageView tabContactIcon;
    private android.widget.TextView tabContactLabel;
    private android.widget.ImageView tabFriendIcon;
    private android.widget.TextView tabFriendLabel;
    private android.widget.ImageView tabMeIcon;
    private android.widget.TextView tabMeLabel;
    private TextView unReadView;

    public BottomNavView(Context context) {
        super(context);
        init(context);
    }

    public BottomNavView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    private void init(Context context) {
        LayoutInflater.from(context).inflate(R.layout.include_bottom_nav, this, true);

        tabChat = findViewById(R.id.tab_chats);
        tabContact = findViewById(R.id.tab_contacts);
        tabFriend = findViewById(R.id.tab_discover);
        tabMe = findViewById(R.id.tab_me);

        tabChatIcon = tabChat.findViewById(R.id.tab_chats_icon);
        tabChatLabel = tabChat.findViewById(R.id.tab_chats_label);
        tabContactIcon = tabContact.findViewById(R.id.tab_contacts_icon);
        tabContactLabel = tabContact.findViewById(R.id.tab_contacts_label);
        tabFriendIcon = tabFriend.findViewById(R.id.tab_discover_icon);
        tabFriendLabel = tabFriend.findViewById(R.id.tab_discover_label);
        tabMeIcon = tabMe.findViewById(R.id.tab_me_icon);
        tabMeLabel = tabMe.findViewById(R.id.tab_me_label);
        unReadView = tabChat.findViewById(R.id.unread_dot);

        tabChat.setOnClickListener(v -> selectTab(0, true));
        tabContact.setOnClickListener(v -> selectTab(1, true));
        tabFriend.setOnClickListener(v -> selectTab(2, true));
        tabMe.setOnClickListener(v -> selectTab(3, true));

        // initialize selection
        post(() -> setSelectedTab(selectedIndex));
    }

    private void notifyListener(int index) {
        if (listener != null) {
            listener.onTabClicked(index);
        }
    }

    private void selectTab(int index, boolean notify) {
        setSelectedTab(index);
        if (notify && listener != null) {
            listener.onTabClicked(index);
        }
    }

    public void setSelectedTab(int index) {
        selectedIndex = index;
        int activeColor = getResources().getColor(R.color.app_primary);
        int inactiveColor = getResources().getColor(android.R.color.darker_gray);

        // tabChat
        if (tabChatIcon != null) tabChatIcon.setColorFilter(index == 0 ? activeColor : inactiveColor);
        if (tabChatLabel != null) tabChatLabel.setTextColor(index == 0 ? activeColor : inactiveColor);
        // tabContact
        if (tabContactIcon != null) tabContactIcon.setColorFilter(index == 1 ? activeColor : inactiveColor);
        if (tabContactLabel != null) tabContactLabel.setTextColor(index == 1 ? activeColor : inactiveColor);
        // tabFriend
        if (tabFriendIcon != null) tabFriendIcon.setColorFilter(index == 2 ? activeColor : inactiveColor);
        if (tabFriendLabel != null) tabFriendLabel.setTextColor(index == 2 ? activeColor : inactiveColor);
        // tabMe
        if (tabMeIcon != null) tabMeIcon.setColorFilter(index == 3 ? activeColor : inactiveColor);
        if (tabMeLabel != null) tabMeLabel.setTextColor(index == 3 ? activeColor : inactiveColor);
    }

    public void setOnTabClickListener(OnTabClickListener listener) {
        this.listener = listener;
    }

    public void updateUnreadCount(int c) {
        if (c > 0) unReadView.setVisibility(VISIBLE);
        else unReadView.setVisibility(GONE);
        // Show 99+ when count exceeds 99
        unReadView.setText(c > 99 ? "99+" : String.valueOf(c));
    }
}

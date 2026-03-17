package com.juggle.im.android.app;

import android.content.Context;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
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

    private ImageView tabChatIcon;
    private TextView tabChatLabel;
    private ImageView tabContactIcon;
    private TextView tabContactLabel;
    private ImageView tabFriendIcon;
    private TextView tabFriendLabel;
    private ImageView tabMeIcon;
    private TextView tabMeLabel;
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
        int inactiveColor = getResources().getColor(R.color.conversation_secondary_text);

        if (tabChatIcon != null) tabChatIcon.setImageResource(index == 0
                ? R.drawable.nav_chat_selected
                : R.drawable.nav_chat_unselected);
        if (tabChatLabel != null) tabChatLabel.setTextColor(index == 0 ? activeColor : inactiveColor);

        if (tabContactIcon != null) tabContactIcon.setImageResource(index == 1
                ? R.drawable.nav_contact_selected
                : R.drawable.nav_contact_unselected);
        if (tabContactLabel != null) tabContactLabel.setTextColor(index == 1 ? activeColor : inactiveColor);

        if (tabFriendIcon != null) tabFriendIcon.setImageResource(index == 2
                ? R.drawable.nav_discover_selected
                : R.drawable.nav_discover_unselected);
        if (tabFriendLabel != null) tabFriendLabel.setTextColor(index == 2 ? activeColor : inactiveColor);

        if (tabMeIcon != null) tabMeIcon.setImageResource(index == 3
                ? R.drawable.nav_me_selected
                : R.drawable.nav_me_unselected);
        if (tabMeLabel != null) tabMeLabel.setTextColor(index == 3 ? activeColor : inactiveColor);
    }

    public void setOnTabClickListener(OnTabClickListener listener) {
        this.listener = listener;
    }

    public void updateUnreadCount(int c) {
        if (unReadView == null) return;
        if (c > 0) {
            unReadView.setVisibility(VISIBLE);
            unReadView.setText(c > 99 ? "99+" : String.valueOf(c));
        } else {
            unReadView.setVisibility(GONE);
        }
    }

}

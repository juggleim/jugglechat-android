package com.juggle.im.android.app;

import static android.view.View.GONE;
import static android.view.View.VISIBLE;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.Window;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;

import com.juggle.im.JIM;
import com.juggle.im.JIMConst;
import com.juggle.im.android.R;
import com.juggle.im.android.auth.AuthGuard;
import com.juggle.im.android.chat.ConversationListFragment;
import com.juggle.im.android.chat.FriendsFragment;
import com.juggle.im.android.chat.DiscoverFragment;
import com.juggle.im.android.chat.MyProfileFragment;
import com.juggle.im.android.chat.SearchActivity;
import com.juggle.im.android.chat.call.MultiCallActivity;
import com.juggle.im.android.chat.call.SingleCallActivity;
import com.juggle.im.android.core.JIMChatCore;
import com.juggle.im.android.event.ConnectStatusEvent;
import com.juggle.im.android.event.ConversationUpdatedEvent;
import com.juggle.im.android.event.MessageReadUpdatedEvent;
import com.juggle.im.android.event.UnreadMessageCountEvent;
import com.juggle.im.android.model.ConfigUtils;
import com.juggle.im.android.model.UiConversation;
import com.juggle.im.call.CallConst;
import com.juggle.im.model.Conversation;
import com.juggle.im.model.ConversationInfo;
import com.juggle.im.model.GroupInfo;
import com.juggle.im.model.UserInfo;
import com.qiniu.android.utils.StringUtils;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class MainActivity extends AppCompatActivity {
    private ConversationListFragment conversationListFragment;
    private FriendsFragment friendsFragment; // kept for places that still use it
    private DiscoverFragment discoverFragment;
    private MyProfileFragment myProfileFragment;
    private BottomNavView bottomNav;
    private TextView tvTitle;
    private ImageView btnMore, btnSearch;
    private AuthGuard authGuard;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        authGuard = AuthGuard.create(this);
        if (!authGuard.requireValidSessionForWrite(this, "main.enter")) {
            return;
        }
        JIMChatCore.getInstance().connect(ConfigUtils.imToken);

        setContentView(R.layout.activity_main);

        Window window = getWindow();
        window.setStatusBarColor(getColor(R.color.primary_bg_light));
        window.setNavigationBarColor(getColor(R.color.white));
        WindowInsetsControllerCompat controller =
                new WindowInsetsControllerCompat(window, window.getDecorView());
        controller.setAppearanceLightStatusBars(false);

        // add conversation fragment as default
        FragmentManager fm = getSupportFragmentManager();
        FragmentTransaction tx = fm.beginTransaction();
        conversationListFragment = new ConversationListFragment();
        tx.add(R.id.content_container, conversationListFragment, "conversations");
        tx.commitAllowingStateLoss();

        bottomNav = findViewById(R.id.footer_nav);
        tvTitle = findViewById(R.id.tv_title);
        if (bottomNav != null) {
            bottomNav.setOnTabClickListener(index -> onTabSelected(index));
            bottomNav.setSelectedTab(0);
        }

        // add button: show popup menu (Add friend, Create group)
        btnMore = findViewById(R.id.btn_more);
        if (btnMore != null) {
            btnMore.setOnClickListener(v -> {
                android.widget.PopupMenu popup = new android.widget.PopupMenu(MainActivity.this, v);
                popup.getMenuInflater().inflate(R.menu.menu_add, popup.getMenu());
                popup.setOnMenuItemClickListener(item -> {
                    int id = item.getItemId();
                    if (id == R.id.menu_add_friend) {
                        if (!authGuard.requireValidSessionForWrite(MainActivity.this, "main.menu.add_friend")) {
                            return true;
                        }
                        startActivity(new android.content.Intent(MainActivity.this, AddFriendActivity.class));
                        return true;
                    } else if (id == R.id.menu_create_group) {
                        if (!authGuard.requireValidSessionForWrite(MainActivity.this, "main.menu.create_group")) {
                            return true;
                        }
                        startActivity(new android.content.Intent(MainActivity.this, CreateGroupActivity.class));
                        return true;
                    }
                    return false;
                });
                popup.show();
            });
        }
        btnSearch = findViewById(R.id.btn_search);
        btnSearch.setOnClickListener( v -> {
            startActivity(new Intent(MainActivity.this, SearchActivity.class));
        });

        EventBus.getDefault().register(this);

        JIM.getInstance().getCallManager().addReceiveListener("CallReceive", iCallSession -> {
            Log.d("MainActivity", "receive call: " + iCallSession.getCallId());
            int members = iCallSession.getMembers().size();
            Intent it = members == 2
                    ? new Intent(this, SingleCallActivity.class)
                    : new Intent(this, MultiCallActivity.class);
            it.putExtra("inviter", iCallSession.getInviter());
            it.putExtra("is_video_call", iCallSession.getMediaType() == CallConst.CallMediaType.VIDEO);
            List<String> ids = iCallSession.getMembers().stream()
                    .map(member -> member.getUserInfo().getUserId())
                    .collect(Collectors.toList());
            it.putStringArrayListExtra("targetUserIds", (ArrayList<String>)ids);
            it.putExtra("direction", "incoming");
            it.putExtra("callId", iCallSession.getCallId());
            String extra = iCallSession.getExtra();
            if (!StringUtils.isBlank(extra)) {
                try {
                    JSONObject jsonObject = new JSONObject(extra);
                    String conversationId = jsonObject.getString("conversationId");
                    it.putExtra("conversationId", conversationId);
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }

            startActivity(it);
        });
    }


    private void onTabSelected(int index) {
        FragmentManager fm = getSupportFragmentManager();
        FragmentTransaction tx = fm.beginTransaction();
        switch (index) {
            case 0:
                if (conversationListFragment == null) {
                    conversationListFragment = new ConversationListFragment();
                    tx.add(R.id.content_container, conversationListFragment, "conversations");
                }
                if (friendsFragment != null) tx.hide(friendsFragment);
                if (discoverFragment != null) tx.hide(discoverFragment);
                if (myProfileFragment != null) tx.hide(myProfileFragment);
                tx.show(conversationListFragment);
                tvTitle.setText("聊天");
                btnMore.setVisibility(VISIBLE);
                btnSearch.setVisibility(VISIBLE);

                break;
            case 2:
                // friend tab now shows DiscoverFragment (发现)
                if (discoverFragment == null) {
                    discoverFragment = new DiscoverFragment();
                    tx.add(R.id.content_container, discoverFragment, "discover");
                }
                if (conversationListFragment != null) tx.hide(conversationListFragment);
                // if friendsFragment existed, hide it as well
                if (friendsFragment != null) tx.hide(friendsFragment);
                if (myProfileFragment != null) tx.hide(myProfileFragment);
                tx.show(discoverFragment);
                tvTitle.setText("发现");
                btnMore.setVisibility(GONE);
                btnSearch.setVisibility(GONE);

                break;
            case 1:
                if (friendsFragment == null) {
                    friendsFragment = new FriendsFragment();
                    tx.add(R.id.content_container, friendsFragment, "friends");
                }
                if (conversationListFragment != null) tx.hide(conversationListFragment);
                if (discoverFragment != null) tx.hide(discoverFragment);
                if (myProfileFragment != null) tx.hide(myProfileFragment);
                tx.show(friendsFragment);
                tvTitle.setText("联系人");
                btnMore.setVisibility(GONE);
                btnSearch.setVisibility(GONE);
                break;
            case 3:
                if (myProfileFragment == null) {
                    myProfileFragment = new MyProfileFragment();
                    tx.add(R.id.content_container, myProfileFragment, "profile");
                }
                if (conversationListFragment != null) tx.hide(conversationListFragment);
                if (friendsFragment != null) tx.hide(friendsFragment);
                if (discoverFragment != null) tx.hide(discoverFragment);
                tx.show(myProfileFragment);
                tvTitle.setText("我");
                btnMore.setVisibility(GONE);
                btnSearch.setVisibility(GONE);

                break;
            default:
                // other tabs not implemented yet
                break;
        }
        tx.commitAllowingStateLoss();
        BottomNavView bottomNav = findViewById(R.id.footer_nav);
        if (bottomNav != null) bottomNav.setSelectedTab(index);
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onConnectStatusChanged(ConnectStatusEvent event) {
        Log.i("MainActivity", event.getConnectionStatus().toString() + "," + event.getCode());
        View v = findViewById(R.id.connect_status);
        if (event.getConnectionStatus() == JIMConst.ConnectionStatus.FAILURE
                || event.getConnectionStatus() == JIMConst.ConnectionStatus.DISCONNECTED) {
            v.setVisibility(VISIBLE);
            TextView vStatus = findViewById(R.id.connect_text_view);
            if (event.getCode() == 11011) {
                authGuard.handleSessionInvalid(this, "remote_login_11011");
                return;
            } else {
                vStatus.setText("连接失败，请检查网络");
            }
        } else {
            v.setVisibility(GONE);
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void MessageReadUpdatedEvent(MessageReadUpdatedEvent event) {

    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onConversationUpdated(ConversationUpdatedEvent event) {
        Log.i("MainActivity", "onConversationUpdated");
        List<ConversationInfo> infoList = event.getConversationInfoList();
        if (infoList == null || infoList.isEmpty()) return;

        List<UiConversation> uiList = new ArrayList<>();
        for (ConversationInfo info : infoList) {
            UiConversation ui = UiConversation.fromConversationInfo(info);
            //不显示系统消息
            if (info.getConversation().getConversationType().equals(Conversation.ConversationType.SYSTEM)) {
                continue;
            }
            if (info.getConversation().getConversationType().equals(Conversation.ConversationType.GROUP)) {
                GroupInfo groupInfo = JIM.getInstance().getUserInfoManager().getGroupInfo(ui.getConversationInfo().getConversation().getConversationId());
                if (groupInfo != null) {
                    ui.setName(groupInfo.getGroupName());
                    ui.setAvatar(groupInfo.getPortrait());
                }
                UserInfo userInfo = ui.getLastMessage() != null
                        ? JIM.getInstance().getUserInfoManager().getUserInfo(ui.getLastMessage().getSenderUserId())
                        : null;
                if (userInfo != null) {
                    ui.setLastMessageUserName(userInfo.getUserName());
                }
            } else if (info.getConversation().getConversationType().equals(Conversation.ConversationType.PRIVATE)) {
                UserInfo userInfo = JIM.getInstance().getUserInfoManager().getUserInfo(ui.getConversationInfo().getConversation().getConversationId());
                if (userInfo != null) {
                    ui.setName(userInfo.getUserName());
                    ui.setAvatar(userInfo.getPortrait());
                    ui.setLastMessageUserName(userInfo.getUserName());
                }
            }
            uiList.add(ui);
        }
        ConversationListFragment frag = (ConversationListFragment) getSupportFragmentManager().findFragmentByTag("conversations");
        if (frag != null) {
            runOnUiThread(() -> frag.upsertConversations(uiList));
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onUnreadMessageCountEvent(UnreadMessageCountEvent event) {
        bottomNav.updateUnreadCount(event.getTotalCount());
    }


    @Override
    public void onDestroy() {
        super.onDestroy();
        if (EventBus.getDefault().isRegistered(this)) {
            EventBus.getDefault().unregister(this);
        }
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            // 当用户按下返回键时，将应用移至后台而不是关闭
            moveTaskToBack(true);
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }
}

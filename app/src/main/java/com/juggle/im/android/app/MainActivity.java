package com.juggle.im.android.app;

import static android.view.View.GONE;
import static android.view.View.VISIBLE;

import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.Window;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ImageView;
import android.widget.PopupWindow;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;

import com.juggle.im.JIM;
import com.juggle.im.JIMConst;
import com.juggle.im.android.R;
import com.juggle.im.android.auth.AuthGuard;
import com.juggle.im.android.auth.MultiDevicePolicy;
import com.juggle.im.android.auth.UserProfileStore;
import com.juggle.im.android.chat.ConversationListFragment;
import com.juggle.im.android.chat.FriendsFragment;
import com.juggle.im.android.chat.DiscoverFragment;
import com.juggle.im.android.chat.MyProfileFragment;
import com.juggle.im.android.chat.SearchActivity;
import com.juggle.im.android.chat.call.BaseCallActivity;
import com.juggle.im.android.chat.call.CallUiStateStore;
import com.juggle.im.android.core.JIMChatCore;
import com.juggle.im.android.event.ConnectStatusEvent;
import com.juggle.im.android.event.ConversationUpdatedEvent;
import com.juggle.im.android.event.MessageReadUpdatedEvent;
import com.juggle.im.android.event.UnreadMessageCountEvent;
import com.juggle.im.android.model.ConfigUtils;
import com.juggle.im.android.model.UiConversation;
import com.juggle.im.android.utils.AvatarUtils;
import com.juggle.im.call.CallConst;
import com.juggle.im.call.ICallSession;
import com.juggle.im.model.Conversation;
import com.juggle.im.model.ConversationInfo;
import com.juggle.im.model.GroupInfo;
import com.juggle.im.model.UserInfo;
import com.qiniu.android.utils.StringUtils;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {
    private ConversationListFragment conversationListFragment;
    private FriendsFragment friendsFragment; // kept for places that still use it
    private DiscoverFragment discoverFragment;
    private MyProfileFragment myProfileFragment;
    private BottomNavView bottomNav;
    private TextView tvTitle;
    private TextView tvHeaderName;
    private TextView tvHeaderUserId;
    private TextView tvHeaderStatus;
    private View headerProfileArea;
    private ImageView ivHeaderAvatar;
    private ImageView btnMore, btnSearch;
    private AuthGuard authGuard;
    private PopupWindow mainAddActionPopup;
    private FrameLayout callFloatContainer;
    private View ongoingCallFloatView;
    private TextView tvOngoingCallFloatTimer;
    private final Handler callFloatHandler = new Handler(Looper.getMainLooper());
    private Runnable ongoingTimerRunnable;
    private ICallSession floatingCallSession;
    private static final String MAIN_FLOATING_CALL_LISTENER = "MainOngoingCallFloat";

    private final ICallSession.ICallSessionListener floatingCallListener = new ICallSession.ICallSessionListener() {
        @Override
        public void onCallConnect() {
            runOnUiThread(() -> {
                CallUiStateStore.FloatingCallInfo info = CallUiStateStore.getFloatingCallInfo();
                if (info != null) {
                    info.connected = true;
                    if (info.connectedStartAt <= 0L) {
                        info.connectedStartAt = System.currentTimeMillis();
                    }
                    CallUiStateStore.saveFloatingCallInfo(info);
                    renderOngoingCallFloating();
                }
            });
        }

        @Override
        public void onCallFinish(CallConst.CallFinishReason callFinishReason) {
            runOnUiThread(() -> {
                CallUiStateStore.clearFloatingCallInfo();
                dismissOngoingCallFloating();
            });
        }

        @Override
        public void onErrorOccur(CallConst.CallErrorCode callErrorCode) {
        }

        @Override
        public void onUsersInvite(String s, List<String> list) {
        }

        @Override
        public void onUsersConnect(List<String> list) {
        }

        @Override
        public void onUsersLeave(List<String> list) {
        }

        @Override
        public void onUserCameraEnable(String s, boolean b) {
        }

        @Override
        public void onUserMicrophoneEnable(String s, boolean b) {
        }

        @Override
        public void onSoundLevelUpdate(HashMap<String, Float> hashMap) {
        }

        @Override
        public void onVideoFirstFrameRender(String s) {
        }
    };

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
        window.setStatusBarColor(getColor(R.color.conversation_page_bg));
        window.setNavigationBarColor(getColor(R.color.conversation_page_bg));
        WindowInsetsControllerCompat controller =
                new WindowInsetsControllerCompat(window, window.getDecorView());
        controller.setAppearanceLightStatusBars(true);

        // add conversation fragment as default
        FragmentManager fm = getSupportFragmentManager();
        FragmentTransaction tx = fm.beginTransaction();
        conversationListFragment = new ConversationListFragment();
        tx.add(R.id.content_container, conversationListFragment, "conversations");
        tx.commitAllowingStateLoss();

        bottomNav = findViewById(R.id.footer_nav);
        tvTitle = findViewById(R.id.tv_title);
        tvHeaderName = findViewById(R.id.tv_header_name);
        tvHeaderUserId = findViewById(R.id.tv_header_user_id);
        tvHeaderStatus = findViewById(R.id.tv_header_status);
        ivHeaderAvatar = findViewById(R.id.iv_header_avatar);
        headerProfileArea = findViewById(R.id.header_profile_area);
        callFloatContainer = findViewById(R.id.call_float_container);
        if (bottomNav != null) {
            bottomNav.setOnTabClickListener(index -> onTabSelected(index));
            bottomNav.setSelectedTab(0);
        }
        updateHeaderProfile();
        updateHeaderStatus(getString(R.string.main_status_connecting));

        // add button: show custom quick actions popup (Create group, Add friend, Scan QR)
        btnMore = findViewById(R.id.btn_more);
        if (btnMore != null) {
            btnMore.setOnClickListener(this::showMainAddActionsPopup);
        }
        btnSearch = findViewById(R.id.btn_search);
        if (btnSearch != null) {
            btnSearch.setOnClickListener(v -> startActivity(new Intent(MainActivity.this, SearchActivity.class)));
        }

        EventBus.getDefault().register(this);
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateHeaderProfile();
        renderOngoingCallFloating();
    }


    private void onTabSelected(int index) {
        dismissMainAddActionsPopupIfNeeded();
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
                tvTitle.setText(R.string.main_title);
                btnMore.setVisibility(VISIBLE);
                btnSearch.setVisibility(VISIBLE);
                btnMore.setOnClickListener(this::showMainAddActionsPopup);
                if (headerProfileArea != null) headerProfileArea.setVisibility(VISIBLE);
                if (tvHeaderStatus != null && tvHeaderStatus.getText().length() > 0) {
                    tvHeaderStatus.setVisibility(VISIBLE);
                }

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
                if (headerProfileArea != null) headerProfileArea.setVisibility(GONE);
                if (tvHeaderStatus != null) tvHeaderStatus.setVisibility(GONE);

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
                tvTitle.setText(R.string.tab_contact);
                btnMore.setVisibility(VISIBLE);
                btnSearch.setVisibility(GONE);
                btnMore.setOnClickListener(v -> {
                    if (!authGuard.requireValidSessionForWrite(this, "main.contact.add_friend")) {
                        return;
                    }
                    startActivity(new Intent(this, AddFriendActivity.class));
                });
                if (headerProfileArea != null) headerProfileArea.setVisibility(GONE);
                if (tvHeaderStatus != null) tvHeaderStatus.setVisibility(GONE);
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
                if (headerProfileArea != null) headerProfileArea.setVisibility(GONE);
                if (tvHeaderStatus != null) tvHeaderStatus.setVisibility(GONE);

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
            if (MultiDevicePolicy.shouldForceLogout(event.getCode())) {
                authGuard.handleSessionInvalid(this, "remote_login_11011");
                return;
            } else {
                vStatus.setText("连接失败，请检查网络");
                updateHeaderStatus(getString(R.string.main_status_connecting));
            }
        } else {
            v.setVisibility(GONE);
            updateHeaderStatus("");
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
        dismissMainAddActionsPopupIfNeeded();
        dismissOngoingCallFloating();
        if (EventBus.getDefault().isRegistered(this)) {
            EventBus.getDefault().unregister(this);
        }
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            dismissMainAddActionsPopupIfNeeded();
            // 当用户按下返回键时，将应用移至后台而不是关闭
            moveTaskToBack(true);
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    private void showMainAddActionsPopup(View anchor) {
        if (mainAddActionPopup != null && mainAddActionPopup.isShowing()) {
            mainAddActionPopup.dismiss();
            return;
        }
        View content = LayoutInflater.from(this).inflate(R.layout.layout_main_add_action_popup, null);
        mainAddActionPopup = new PopupWindow(
                content,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                true);
        mainAddActionPopup.setOutsideTouchable(true);
        mainAddActionPopup.setFocusable(true);
        mainAddActionPopup.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        mainAddActionPopup.setElevation(dp(8));
        mainAddActionPopup.setOnDismissListener(() -> mainAddActionPopup = null);

        content.findViewById(R.id.action_create_group).setOnClickListener(v -> {
            if (!authGuard.requireValidSessionForWrite(MainActivity.this, "main.menu.create_group")) {
                return;
            }
            startActivity(new Intent(MainActivity.this, CreateGroupActivity.class));
            dismissMainAddActionsPopupIfNeeded();
        });
        content.findViewById(R.id.action_add_friend).setOnClickListener(v -> {
            if (!authGuard.requireValidSessionForWrite(MainActivity.this, "main.menu.add_friend")) {
                return;
            }
            startActivity(new Intent(MainActivity.this, AddFriendActivity.class));
            dismissMainAddActionsPopupIfNeeded();
        });
        content.findViewById(R.id.action_scan_qr).setOnClickListener(v -> {
            Toast.makeText(MainActivity.this, R.string.scan_qr_todo, Toast.LENGTH_SHORT).show();
            dismissMainAddActionsPopupIfNeeded();
        });

        content.measure(
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        int popupWidth = content.getMeasuredWidth();
        int xoff = anchor.getWidth() - popupWidth;
        mainAddActionPopup.showAsDropDown(anchor, xoff, dp(8));
    }

    private void dismissMainAddActionsPopupIfNeeded() {
        if (mainAddActionPopup != null && mainAddActionPopup.isShowing()) {
            mainAddActionPopup.dismiss();
        }
    }

    private int dp(int value) {
        return Math.round(getResources().getDisplayMetrics().density * value);
    }

    /**
     * 展示最小化通话浮窗（含计时）。
     */
    private void renderOngoingCallFloating() {
        CallUiStateStore.FloatingCallInfo info = CallUiStateStore.getFloatingCallInfo();
        if (info == null || StringUtils.isNullOrEmpty(info.callId)) {
            dismissOngoingCallFloating();
            return;
        }
        if (callFloatContainer == null) {
            return;
        }

        if (ongoingCallFloatView == null) {
            ongoingCallFloatView = LayoutInflater.from(this).inflate(R.layout.layout_call_ongoing_floating, callFloatContainer, false);
            tvOngoingCallFloatTimer = ongoingCallFloatView.findViewById(R.id.tv_float_time);
            ongoingCallFloatView.setOnClickListener(v -> {
                CallUiStateStore.FloatingCallInfo current = CallUiStateStore.getFloatingCallInfo();
                if (current == null) {
                    return;
                }
                startActivity(BaseCallActivity.buildRestoreIntent(this, current));
                CallUiStateStore.clearFloatingCallInfo();
                dismissOngoingCallFloating();
            });
        }

        if (ongoingCallFloatView.getParent() == null) {
            FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT);
            params.gravity = Gravity.TOP | Gravity.END;
            params.setMargins(dp(12), dp(90), dp(12), 0);
            callFloatContainer.addView(ongoingCallFloatView, params);
        }

        ImageView ivAvatar = ongoingCallFloatView.findViewById(R.id.iv_float_avatar);
        TextView tvName = ongoingCallFloatView.findViewById(R.id.tv_float_name);
        String displayUserId = resolveFloatingDisplayUserId(info);
        UserInfo userInfo = JIM.getInstance().getUserInfoManager().getUserInfo(displayUserId);
        String displayName = userInfo == null || StringUtils.isNullOrEmpty(userInfo.getUserName())
                ? getString(R.string.call_status_float_ongoing)
                : userInfo.getUserName();
        String displayAvatar = userInfo == null ? "" : userInfo.getPortrait();
        tvName.setText(displayName);
        AvatarUtils.loadAvatar(ivAvatar, displayAvatar, displayName, displayUserId);

        long startAt = info.connectedStartAt > 0L ? info.connectedStartAt : System.currentTimeMillis();
        startOngoingCallTimer(startAt);
        bindFloatingCallSession(info.callId);
    }

    /**
     * 关闭最小化通话浮窗并解绑监听。
     */
    private void dismissOngoingCallFloating() {
        stopOngoingCallTimer();
        if (floatingCallSession != null) {
            floatingCallSession.removeListener(MAIN_FLOATING_CALL_LISTENER);
            floatingCallSession = null;
        }
        if (ongoingCallFloatView != null && callFloatContainer != null) {
            callFloatContainer.removeView(ongoingCallFloatView);
            ongoingCallFloatView = null;
            tvOngoingCallFloatTimer = null;
        }
    }

    /**
     * 绑定最小化通话会话监听，用于自动清理浮窗。
     */
    private void bindFloatingCallSession(String callId) {
        if (floatingCallSession != null) {
            floatingCallSession.removeListener(MAIN_FLOATING_CALL_LISTENER);
            floatingCallSession = null;
        }
        if (StringUtils.isNullOrEmpty(callId)) {
            return;
        }
        floatingCallSession = JIM.getInstance().getCallManager().getCallSession(callId);
        if (floatingCallSession != null) {
            floatingCallSession.addListener(MAIN_FLOATING_CALL_LISTENER, floatingCallListener);
        }
    }

    /**
     * 启动最小化浮窗计时器。
     */
    private void startOngoingCallTimer(long startAt) {
        if (tvOngoingCallFloatTimer == null) {
            return;
        }
        stopOngoingCallTimer();
        tvOngoingCallFloatTimer.setText(formatDuration(startAt));
        ongoingTimerRunnable = new Runnable() {
            @Override
            public void run() {
                if (tvOngoingCallFloatTimer == null) {
                    return;
                }
                tvOngoingCallFloatTimer.setText(formatDuration(startAt));
                callFloatHandler.postDelayed(this, 1000L);
            }
        };
        callFloatHandler.post(ongoingTimerRunnable);
    }

    /**
     * 停止最小化浮窗计时器。
     */
    private void stopOngoingCallTimer() {
        if (ongoingTimerRunnable != null) {
            callFloatHandler.removeCallbacks(ongoingTimerRunnable);
            ongoingTimerRunnable = null;
        }
    }

    private String formatDuration(long startAt) {
        long elapsedMillis = Math.max(0L, System.currentTimeMillis() - startAt);
        int seconds = (int) (elapsedMillis / 1000L);
        int minutes = seconds / 60;
        seconds = seconds % 60;
        return String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds);
    }

    private String resolveFloatingDisplayUserId(CallUiStateStore.FloatingCallInfo info) {
        String currentUserId = JIM.getInstance().getCurrentUserId();
        if (info.targetUserIds != null) {
            for (String userId : info.targetUserIds) {
                if (!StringUtils.isNullOrEmpty(userId) && !userId.equals(currentUserId)) {
                    return userId;
                }
            }
        }
        return info.inviterUserId;
    }

    /**
     * 顶部左侧个人信息：显示当前登录用户头像、昵称和用户 ID。
     * 仅用于 UI 头部展示，不参与业务写入。
     */
    private void updateHeaderProfile() {
        if (tvHeaderName == null || tvHeaderUserId == null || ivHeaderAvatar == null) {
            return;
        }
        UserProfileStore.UserProfile cachedProfile = UserProfileStore.read(this);

        String displayName = trimToEmpty(ConfigUtils.myName);
        if (displayName.isEmpty()) {
            displayName = trimToEmpty(cachedProfile.getNickname());
        }
        if (displayName.isEmpty()) {
            displayName = getString(R.string.main_default_user_name);
        }
        tvHeaderName.setText(displayName);

        String userId = trimToEmpty(JIM.getInstance().getCurrentUserId());
        if (userId.isEmpty()) {
            userId = trimToEmpty(cachedProfile.getUserId());
        }
        tvHeaderUserId.setText(userId.isEmpty() ? "" : "@" + userId);
        String avatarUrl = trimToEmpty(ConfigUtils.myAvatarUrl);
        if (avatarUrl.isEmpty()) {
            avatarUrl = trimToEmpty(cachedProfile.getAvatar());
        }
        if (ConfigUtils.myName == null || ConfigUtils.myName.trim().isEmpty()) {
            ConfigUtils.myName = cachedProfile.getNickname();
        }
        if (ConfigUtils.myAvatarUrl == null || ConfigUtils.myAvatarUrl.trim().isEmpty()) {
            ConfigUtils.myAvatarUrl = cachedProfile.getAvatar();
        }
        String nameForCache = trimToEmpty(ConfigUtils.myName);
        if (nameForCache.isEmpty()) {
            nameForCache = trimToEmpty(cachedProfile.getNickname());
        }
        if (!userId.isEmpty()) {
            UserProfileStore.save(this, userId, nameForCache, avatarUrl);
        }
        AvatarUtils.loadAvatar(ivHeaderAvatar, avatarUrl, displayName, userId);
    }

    /**
     * 顶部中间副标题（例如“正在连接…”）。
     */
    private void updateHeaderStatus(String status) {
        if (tvHeaderStatus == null) {
            return;
        }
        String safeStatus = trimToEmpty(status);
        tvHeaderStatus.setText(safeStatus);
        tvHeaderStatus.setVisibility(safeStatus.isEmpty() ? GONE : VISIBLE);
    }

    private String trimToEmpty(String value) {
        return value == null ? "" : value.trim();
    }
}

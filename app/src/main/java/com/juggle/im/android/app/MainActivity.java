package com.juggle.im.android.app;

import static android.view.View.GONE;
import static android.view.View.VISIBLE;

import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.Window;
import android.widget.LinearLayout;
import android.widget.ImageView;
import android.widget.PopupWindow;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;

import com.juggle.im.JIM;
import com.juggle.im.JIMConst;
import com.juggle.im.android.Application;
import com.juggle.im.android.R;
import com.juggle.im.android.auth.AuthGuard;
import com.juggle.im.android.auth.MultiDevicePolicy;
import com.juggle.im.android.auth.UserProfileStore;
import com.juggle.im.android.chat.ConversationActivity;
import com.juggle.im.android.chat.ConversationListFragment;
import com.juggle.im.android.chat.FriendsFragment;
import com.juggle.im.android.chat.DiscoverFragment;
import com.juggle.im.android.chat.MyProfileFragment;
import com.juggle.im.android.chat.SearchActivity;
import com.juggle.im.android.core.JIMChatCore;
import com.juggle.im.android.event.ConnectStatusEvent;
import com.juggle.im.android.event.ConversationUpdatedEvent;
import com.juggle.im.android.event.MessageReadUpdatedEvent;
import com.juggle.im.android.event.UnreadMessageCountEvent;
import com.juggle.im.android.model.ConfigUtils;
import com.juggle.im.android.model.UiConversation;
import com.juggle.im.android.utils.AvatarUtils;
import com.juggle.im.model.Conversation;
import com.juggle.im.model.ConversationInfo;
import com.juggle.im.model.GroupInfo;
import com.juggle.im.model.UserInfo;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {
    /** AI 助手机器人会话 id，点击底部 AI 入口直接进入与该机器人的私聊会话 */
    private static final String AI_BOT_CONVERSATION_ID = "py-sdk-bot-1782458410";

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

    /**
     * 扫码结果回调 Launcher。
     * 接收 ScanQRActivity 返回的扫描结果，解析 JSON 中的 action 字段进行路由。
     */
    private final ActivityResultLauncher<Intent> scanQrLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() != RESULT_OK || result.getData() == null) return;
                String scanResult = result.getData().getStringExtra("scan_result");
                if (scanResult == null || scanResult.isEmpty()) return;
                handleScanResult(scanResult);
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // tips: 通过 AuthGuard 实时校验 session 有效性，无效时跳转登录页
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
        if (bottomNav != null) {
            bottomNav.setOnTabClickListener(index -> onTabSelected(index));
            bottomNav.setOnAiClickListener(() ->
                    startActivity(ConversationActivity.intentFor(
                            MainActivity.this, AI_BOT_CONVERSATION_ID, false,
                            getString(R.string.ai_chat_title))));
            bottomNav.setSelectedTab(0);
        }
        updateHeaderProfile();
        syncStateFromSdk();

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
                tvTitle.setText(R.string.tab_friend);
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
                tvTitle.setText(R.string.tab_me);
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

    /**
     * 用 SDK 当前状态对齐界面。
     * <p>
     * TIPS：连接状态与会话首屏都是靠 SDK 回调推上来的（onStatusChange / onDbOpen）。Activity 重建时
     * （语言切换、深色模式、字体缩放等）SDK 早已连上，不会再回调，界面就会卡在"正在连接…"且会话列表为空。
     * 所以每次创建都主动读一次连接态，已连接就补拉一遍会话列表。
     */
    private void syncStateFromSdk() {
        JIMConst.ConnectionStatus status = JIM.getInstance().getConnectionManager().getConnectionStatus();
        if (status == JIMConst.ConnectionStatus.CONNECTED) {
            updateHeaderStatus("");
            findViewById(R.id.connect_status).setVisibility(GONE);
            JIMChatCore.getInstance().syncConversationListAsync();
        } else {
            updateHeaderStatus(getString(R.string.main_status_connecting));
        }
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
                vStatus.setText(R.string.main_connect_failed);
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
            if (!authGuard.requireValidSessionForWrite(MainActivity.this, "main.menu.scan_qr")) {
                return;
            }
            scanQrLauncher.launch(new Intent(MainActivity.this, ScanQRActivity.class));
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

    /**
     * 处理扫码结果。
     * 解析二维码 JSON 内容，根据 action 字段路由到不同功能页面。
     * 支持的 action：add_friend（加好友）、join_group（加群）、login（扫码登录）。
     *
     * @param data 二维码原始内容字符串
     */
    private void handleScanResult(String data) {
        try {
            JSONObject json = new JSONObject(data);
            String action = json.optString("action", "");

            // tips: 根据 action 类型分发到不同业务流程，保持与 SnailChat 一致
            switch (action) {
                case "add_friend": {
                    String userId = json.optString("user_id", "");
                    if (!userId.isEmpty()) {
                        Intent intent = new Intent(this, ContactDetailActivity.class);
                        intent.putExtra(ContactDetailActivity.EXTRA_USER_ID, userId);
                        startActivity(intent);
                    }
                    break;
                }
                default:
                    Toast.makeText(this, R.string.scan_qr_not_valid, Toast.LENGTH_SHORT).show();
                    break;
            }
        } catch (JSONException e) {
            Toast.makeText(this, R.string.scan_qr_not_valid, Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * 跳转登录页，清空当前任务栈
     */
    private void goToLogin() {
        Intent intent = new Intent(this, LoginActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
        finish();
    }
}

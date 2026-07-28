package com.juggle.im.android.chat;

import static android.view.View.GONE;
import com.juggle.im.android.utils.LogUtils;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.juggle.im.android.R;
import com.juggle.im.android.chat.component.UserListAdapter;
import com.juggle.im.android.component.AbsAppActivity;
import com.juggle.im.android.server.beans.FriendBean;
import com.juggle.im.android.server.beans.FriendsListData;
import com.juggle.im.android.server.beans.GroupDetailBean;
import com.juggle.im.android.server.beans.GroupMemberBean;
import com.juggle.im.android.server.http.ApiCallback;
import com.juggle.im.android.server.http.ServiceManager;
import com.juggle.im.android.utils.AvatarUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * 成员选择页面。
 * 支持群管理、转让群主、多人通话拉人等场景复用。
 */
public class SelectMemberActivity extends AbsAppActivity {
    public static final String GROUP_ID = "GROUP_ID";
    public static final String SELECTED_MEMBERS = "SELECTED_MEMBERS";
    public static final String SELECTED_MEMBERS_NAME = "SELECTED_MEMBERS_NAME";
    public static final String DISABLE_MEMBERS = "DISABLE_MEMBERS";

    private final List<UserListAdapter.UserInfoObj> allMembers = new ArrayList<>();
    private final List<UserListAdapter.UserInfoObj> selectedMemberList = new ArrayList<>();

    private RecyclerView rvMembers;
    private UserListAdapter selectCallMemberAdapter;
    private TextView btnConfirm;
    private EditText searchInput;
    private ImageView searchIcon;
    private HorizontalScrollView searchContentScroll;
    private LinearLayout selectedChipsContainer;

    private List<String> disabledMembers = new ArrayList<>();
    private String mode;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_select_member);

        String groupId = getIntent().getStringExtra(GROUP_ID);
        mode = getIntent().getStringExtra("mode");
        if (mode == null) {
            mode = UserListAdapter.LIST_MODE_SELECT_MEMBER;
        }

        ArrayList<String> disabled = getIntent().getStringArrayListExtra(DISABLE_MEMBERS);
        if (disabled != null) {
            disabledMembers = disabled;
        }

        initViews();
        setupRecyclerViews();
        bindEvents();
        refreshSelectedChips();
        updateConfirmButton();

        if (groupId != null) {
            fetchGroupMembers(groupId);
        } else {
            fetchMyFriends();
        }
    }

    /**
     * 初始化页面控件。
     */
    private void initViews() {
        rvMembers = findViewById(R.id.rv_members);
        btnConfirm = findViewById(R.id.btn_confirm);
        searchInput = findViewById(R.id.et_search);
        searchIcon = findViewById(R.id.iv_search_icon);
        searchContentScroll = findViewById(R.id.hsv_search_content);
        selectedChipsContainer = findViewById(R.id.ll_selected_chips);

        TextView tvPageTitle = findViewById(R.id.tv_page_title);
        tvPageTitle.setText(R.string.create_group_select_member);
        findViewById(R.id.btn_back).setOnClickListener(v -> finish());

        if (mode.equals(UserListAdapter.LIST_MODE_NORMAL)) {
            btnConfirm.setVisibility(GONE);
        }
    }

    /**
     * 初始化成员列表。
     */
    private void setupRecyclerViews() {
        rvMembers.setLayoutManager(new LinearLayoutManager(this));
        selectCallMemberAdapter = new UserListAdapter();
        rvMembers.setAdapter(selectCallMemberAdapter);
        selectCallMemberAdapter.setMode(mode);
        selectCallMemberAdapter.setSelectionChangedListener((member, selected) -> {
            if (selected) {
                selectedMemberList.add(member);
            } else {
                removeSelectedMember(member.getUserId());
            }
            refreshSelectedChips();
            updateConfirmButton();
        });
    }

    /**
     * 绑定事件。
     */
    private void bindEvents() {
        btnConfirm.setOnClickListener(v -> {
            Intent resultIntent = new Intent();
            resultIntent.putStringArrayListExtra(SELECTED_MEMBERS, selectedMemberList.stream()
                    .map(UserListAdapter.UserInfoObj::getUserId)
                    .collect(Collectors.toCollection(ArrayList::new)));
            resultIntent.putStringArrayListExtra(SELECTED_MEMBERS_NAME, selectedMemberList.stream()
                    .map(UserListAdapter.UserInfoObj::getName)
                    .collect(Collectors.toCollection(ArrayList::new)));
            setResult(Activity.RESULT_OK, resultIntent);
            finish();
        });

        searchInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                applyFilter(s == null ? "" : s.toString());
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });
    }

    /**
     * 加载好友列表。
     */
    private void fetchMyFriends() {
        ServiceManager.getUserService().getFriendsList(1, 50, null, new ApiCallback<FriendsListData>() {
            @Override
            public void onSuccess(FriendsListData data) {
                List<FriendBean> items = data != null ? data.getItems() : null;
                List<UserListAdapter.UserInfoObj> memberList = new ArrayList<>();
                if (items != null) {
                    for (FriendBean member : items) {
                        UserListAdapter.UserInfoObj userInfoObj = new UserListAdapter.UserInfoObj(disabledMembers.contains(member.getUser_id()));
                        userInfoObj.setUserId(member.getUser_id());
                        userInfoObj.setName(member.getNickname());
                        userInfoObj.setAvatar(member.getAvatar());
                        memberList.add(userInfoObj);
                    }
                }
                setMembers(memberList);
            }

            @Override
            public void onError(int code, String message) {
                LogUtils.serverError("group", "loadSelectableMembers", code, message);
                Toast.makeText(SelectMemberActivity.this,
                        R.string.create_group_load_failed,
                        Toast.LENGTH_SHORT).show();
            }
        });
    }

    /**
     * 加载群成员。
     *
     * @param groupId 群组ID
     */
    private void fetchGroupMembers(String groupId) {
        ServiceManager.getUserService().getGroupInfo(groupId, new ApiCallback<GroupDetailBean>() {
            @Override
            public void onSuccess(GroupDetailBean data) {
                List<UserListAdapter.UserInfoObj> memberList = new ArrayList<>();
                if (data != null && data.getMembers() != null) {
                    for (GroupMemberBean member : data.getMembers()) {
                        UserListAdapter.UserInfoObj userInfoObj = new UserListAdapter.UserInfoObj(disabledMembers.contains(member.getUserId()));
                        userInfoObj.setUserId(member.getUserId());
                        userInfoObj.setName(member.getNickname());
                        userInfoObj.setAvatar(member.getAvatar());
                        memberList.add(userInfoObj);
                    }
                }
                setMembers(memberList);
            }

            @Override
            public void onError(int code, String message) {
                LogUtils.serverError("group", "loadSelectableMembers", code, message);
                Toast.makeText(SelectMemberActivity.this,
                        R.string.create_group_load_failed,
                        Toast.LENGTH_SHORT).show();
            }
        });
    }

    /**
     * 设置当前可选成员数据。
     *
     * @param members 成员列表
     */
    private void setMembers(List<UserListAdapter.UserInfoObj> members) {
        allMembers.clear();
        if (members != null) {
            allMembers.addAll(members);
        }
        applyFilter(searchInput.getText() == null ? "" : searchInput.getText().toString());
    }

    /**
     * 根据关键字过滤成员。
     *
     * tips：这里同时匹配昵称和 userId，保证多人通话拉人、群管理选人时都能快速定位目标成员。
     * 过滤只影响展示数据，不会清空已选中成员，顶部已选头像条始终保留当前选择结果。
     *
     * @param keyword 搜索关键字
     */
    private void applyFilter(String keyword) {
        String normalized = keyword == null ? "" : keyword.trim().toLowerCase(Locale.ROOT);
        if (TextUtils.isEmpty(normalized)) {
            selectCallMemberAdapter.setItems(new ArrayList<>(allMembers));
            return;
        }

        List<UserListAdapter.UserInfoObj> filtered = new ArrayList<>();
        for (UserListAdapter.UserInfoObj item : allMembers) {
            String name = item.getName() == null ? "" : item.getName();
            String userId = item.getUserId() == null ? "" : item.getUserId();
            if (name.toLowerCase(Locale.ROOT).contains(normalized)
                    || userId.toLowerCase(Locale.ROOT).contains(normalized)) {
                filtered.add(item);
            }
        }
        selectCallMemberAdapter.setItems(filtered);
    }

    /**
     * 刷新顶部已选成员头像条。
     *
     * tips：交互对齐 CreateGroupActivity，已选成员始终前置展示；为空时恢复搜索图标，避免输入区留白。
     */
    private void refreshSelectedChips() {
        selectedChipsContainer.removeAllViews();

        boolean hasSelected = !selectedMemberList.isEmpty();
        searchIcon.setVisibility(hasSelected ? View.GONE : View.VISIBLE);
        if (!hasSelected) {
            return;
        }

        LayoutInflater inflater = LayoutInflater.from(this);
        int index = 0;
        for (UserListAdapter.UserInfoObj item : selectedMemberList) {
            View chip = inflater.inflate(R.layout.item_create_group_selected_chip, selectedChipsContainer, false);
            ImageView avatar = chip.findViewById(R.id.iv_chip_avatar);
            TextView name = chip.findViewById(R.id.tv_chip_name);
            String displayName = TextUtils.isEmpty(item.getName()) ? item.getUserId() : item.getName();
            AvatarUtils.loadAvatar(avatar, item.getAvatar(), displayName, item.getUserId());
            name.setText(displayName);

            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    getResources().getDimensionPixelSize(R.dimen.create_group_selected_chip_height));
            if (index > 0) {
                lp.leftMargin = getResources().getDimensionPixelSize(R.dimen.create_group_selected_chip_spacing);
            }
            selectedChipsContainer.addView(chip, lp);
            index++;
        }

        searchContentScroll.post(() -> searchContentScroll.fullScroll(View.FOCUS_RIGHT));
    }

    /**
     * 更新确认按钮状态。
     */
    private void updateConfirmButton() {
        if (mode.equals(UserListAdapter.LIST_MODE_NORMAL)) {
            return;
        }
        btnConfirm.setEnabled(!selectedMemberList.isEmpty());
        btnConfirm.setText(getString(R.string.select_member_confirm_count, selectedMemberList.size()));
    }

    /**
     * 从已选列表中移除成员。
     *
     * @param userId 用户ID
     */
    private void removeSelectedMember(String userId) {
        for (int i = 0; i < selectedMemberList.size(); i++) {
            if (TextUtils.equals(selectedMemberList.get(i).getUserId(), userId)) {
                selectedMemberList.remove(i);
                return;
            }
        }
    }
}

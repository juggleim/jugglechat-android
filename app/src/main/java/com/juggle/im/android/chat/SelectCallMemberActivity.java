package com.juggle.im.android.chat;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.juggle.im.JIM;
import com.juggle.im.android.R;
import com.juggle.im.android.chat.call.BaseCallActivity;
import com.juggle.im.android.chat.component.UserListAdapter;
import com.juggle.im.android.server.beans.GroupDetailBean;
import com.juggle.im.android.server.beans.GroupMemberBean;
import com.juggle.im.android.server.http.ApiCallback;
import com.juggle.im.android.server.http.ServiceManager;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class SelectCallMemberActivity extends AppCompatActivity {
    public final static String GROUP_ID = "GROUP_ID";
    public final static String SELECTED_MEMBERS = "SELECTED_MEMBERS";

    public final static String DISABLE_MEMBERS = "DISABLE_MEMBERS";


    private RecyclerView rvMembers;
    private UserListAdapter selectCallMemberAdapter;
    private List<UserListAdapter.UserInfoObj> selectedMemberList = new ArrayList<>();
    private TextView btnConfirm;
    private TextView tvCancel;
    private List<String> disabledMembers = new ArrayList<>();


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_call_select_member);

        initViews();
        setupRecyclerViews();
        setClickListeners();

        String groupId = getIntent().getStringExtra(GROUP_ID);
        if (groupId != null) {
            fetchGroupMembers(groupId);
        }
        disabledMembers = getIntent().getStringArrayListExtra(DISABLE_MEMBERS);
    }

    private void initViews() {
        rvMembers = findViewById(R.id.rv_members);
        btnConfirm = findViewById(R.id.btn_confirm);
        tvCancel = findViewById(R.id.tv_cancel);
    }

    private void setupRecyclerViews() {
        // Member List
        rvMembers.setLayoutManager(new LinearLayoutManager(this));
        selectCallMemberAdapter = new UserListAdapter();
        rvMembers.setAdapter(selectCallMemberAdapter);
        selectCallMemberAdapter.setSelectionMode(true);
        selectCallMemberAdapter.setSelectionChangedListener(new UserListAdapter.OnSelectionChanged() {
            @Override
            public void onSelectionChanged(UserListAdapter.UserInfoObj member, boolean selected) {
                if (selected) {
                    selectedMemberList.add(member);
                } else {
                    int idx = -1;
                    for (int i = 0; i < selectedMemberList.size(); i++) {
                        if (selectedMemberList.get(i).getUserId().equals(member.getUserId())) {
                            idx = i;
                            break;
                        }
                    }
                    if (idx >= 0) {
                        selectedMemberList.remove(idx);
                    }
                }
                updateConfirmButton();
            }
        });
    }

    private void setClickListeners() {
        tvCancel.setOnClickListener(v -> finish());
        btnConfirm.setOnClickListener(v -> {
            String conversationId = getIntent().getStringExtra("conversationId");
            if (disabledMembers != null && disabledMembers.size() > 0) {
                Intent resultIntent = new Intent();
                resultIntent.putStringArrayListExtra(SELECTED_MEMBERS, selectedMemberList.stream()
                        .map(member -> member.getUserId())
                        .collect(Collectors.toCollection(ArrayList::new)));
                setResult(Activity.RESULT_OK, resultIntent);
                finish();
            } else {
                BaseCallActivity.startMultiCall(
                        this,
                        conversationId,
                        getIntent().getBooleanExtra("is_video_call", false),
                        JIM.getInstance().getCurrentUserId(),
                        selectedMemberList.stream()
                                .map(member -> member.getUserId())
                                .collect(Collectors.toList()),
                        "outgoing"
                );
                finish();
            }
        });
    }

    private void fetchGroupMembers(String groupId) {
        ServiceManager.getUserService().getGroupInfo(groupId, new ApiCallback<GroupDetailBean>() {
            @Override
            public void onSuccess(GroupDetailBean data) {
                if (data != null && data.getMembers() != null) {
                    List<UserListAdapter.UserInfoObj> memberList = new ArrayList<>();
                    for (GroupMemberBean member : data.getMembers()) {
                        boolean disabled = false;
                        if (disabledMembers != null && disabledMembers.contains(member.getUserId())) {
                            disabled = true;
                        }
                        UserListAdapter.UserInfoObj userInfoObj = new UserListAdapter.UserInfoObj(disabled);
                        userInfoObj.setUserId(member.getUserId());
                        userInfoObj.setName(member.getNickname());
                        userInfoObj.setAvatar(member.getAvatar());
                        memberList.add(userInfoObj);
                    }
                    selectCallMemberAdapter.setItems(memberList);
                }
            }

            @Override
            public void onError(int code, String message) {
                Toast.makeText(SelectCallMemberActivity.this, "Failed to load members: " + message, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void updateConfirmButton() {
        btnConfirm.setText("确定 (" + selectedMemberList.size() + ")");
    }
}
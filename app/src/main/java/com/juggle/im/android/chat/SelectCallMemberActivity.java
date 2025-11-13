package com.juggle.im.android.chat;

import android.content.Intent;
import android.os.Bundle;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.juggle.im.JIM;
import com.juggle.im.android.R;
import com.juggle.im.android.chat.call.BaseCallActivity;
import com.juggle.im.android.server.beans.GroupDetailBean;
import com.juggle.im.android.server.beans.GroupMemberBean;
import com.juggle.im.android.server.http.ApiCallback;
import com.juggle.im.android.server.http.ServiceManager;
import com.juggle.im.android.server.http.UserService;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class SelectCallMemberActivity extends AppCompatActivity {

    private RecyclerView rvMembers, rvSelectedMembers;
    private SelectCallMemberAdapter selectCallMemberAdapter;
    private SelectedCallMemberAdapter selectedCallMemberAdapter;
    private List<GroupMemberBean> memberList = new ArrayList<>();
    private List<GroupMemberBean> selectedMemberList = new ArrayList<>();
    private TextView btnConfirm;
    private TextView tvCancel;
    private EditText etSearch;
    private UserService userService;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_call_select_member);

        userService = ServiceManager.getUserService();

        initViews();
        setupRecyclerViews();
        setClickListeners();

        String groupId = getIntent().getStringExtra("GROUP_ID");
        if (groupId != null) {
            fetchGroupMembers(groupId);
        }
    }

    private void initViews() {
        rvMembers = findViewById(R.id.rv_members);
        rvSelectedMembers = findViewById(R.id.rv_selected_members);
        btnConfirm = findViewById(R.id.btn_confirm);
        tvCancel = findViewById(R.id.tv_cancel);
        etSearch = findViewById(R.id.et_search);
    }

    private void setupRecyclerViews() {
        // Member List
        rvMembers.setLayoutManager(new LinearLayoutManager(this));
        selectCallMemberAdapter = new SelectCallMemberAdapter(this, memberList, (member, isSelected) -> {
            if (isSelected) {
                selectedMemberList.add(member);
                selectedCallMemberAdapter.notifyItemInserted(selectedMemberList.size() - 1);
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
                    selectedCallMemberAdapter.notifyItemRemoved(idx);
                }
            }
            updateConfirmButton();
        });
        rvMembers.setAdapter(selectCallMemberAdapter);

        // Selected Member List
        rvSelectedMembers.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));
        selectedCallMemberAdapter = new SelectedCallMemberAdapter(this, selectedMemberList);
        rvSelectedMembers.setAdapter(selectedCallMemberAdapter);
    }

    private void setClickListeners() {
        tvCancel.setOnClickListener(v -> finish());
        btnConfirm.setOnClickListener(v -> {
            String conversationId = getIntent().getStringExtra("conversationId");
            BaseCallActivity.startMultiCall(
                    this,
                    conversationId,
                    true,
                    JIM.getInstance().getCurrentUserId(),
                    selectedMemberList.stream()
                            .map(member -> member.getUserId())
                            .collect(Collectors.toList()),
                    "outgoing"
            );
            finish();
        });
    }

    private void fetchGroupMembers(String groupId) {
        userService.getGroupInfo(groupId, new ApiCallback<GroupDetailBean>() {
            @Override
            public void onSuccess(GroupDetailBean data) {
                if (data != null && data.getMembers() != null) {
                    memberList.clear();
                    memberList.addAll(data.getMembers());
                    selectCallMemberAdapter.notifyDataSetChanged();
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
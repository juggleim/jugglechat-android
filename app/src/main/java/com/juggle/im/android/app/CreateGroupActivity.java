package com.juggle.im.android.app;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import android.widget.LinearLayout;

import com.juggle.im.android.R;
import com.juggle.im.android.chat.FriendsFragment;
import com.juggle.im.android.chat.component.UserListAdapter;
import com.juggle.im.android.server.beans.CreateGroupResult;
import com.juggle.im.android.server.http.ApiCallback;
import com.juggle.im.android.server.http.ServiceManager;
import com.juggle.im.android.utils.AvatarUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CreateGroupActivity extends AppCompatActivity implements FriendsFragment.SelectionListener {

    private FrameLayout selectedFlow;
    private FriendsFragment friendsFragment;
    private Button btnCreate;
    // maintain selected by id
    private Map<String, UserListAdapter.UserInfoObj> selectedMap = new HashMap<>();

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_create_group);

        selectedFlow = findViewById(R.id.selected_flow);
        btnCreate = findViewById(R.id.btn_create);
        TextView title = findViewById(R.id.toolbar_title);
        title.setText(R.string.create_group);

        // toolbar back
        View btnBack = findViewById(R.id.btn_back);
        if (btnBack != null) btnBack.setOnClickListener(v -> finish());

        // add friends fragment
        friendsFragment = new FriendsFragment();
        friendsFragment.setSelectionMode(UserListAdapter.LIST_MODE_SELECT_MEMBER);
        friendsFragment.setSelectionListener(this);
        getSupportFragmentManager().beginTransaction().replace(R.id.friends_container, friendsFragment).commitAllowingStateLoss();
        btnCreate.setOnClickListener(v -> doCreateGroup());
    }

    @Override
    public void onMemberSelected(UserListAdapter.UserInfoObj member, boolean selected) {
        if (selected) {
            selectedMap.put(member.getUserId(), member);
        } else {
            selectedMap.remove(member.getUserId());
        }
        if (selectedMap.isEmpty()) selectedFlow.setVisibility(View.GONE);
        else selectedFlow.setVisibility(View.VISIBLE);
        refreshSelectedFlow();
    }

    private void refreshSelectedFlow() {
        // We'll implement a simple wrapping by creating horizontal LinearLayout rows
        selectedFlow.removeAllViews();
        LayoutInflater inflater = LayoutInflater.from(this);
        int parentWidth = selectedFlow.getWidth();
        if (parentWidth == 0) {
            // not laid out yet; post and try again
            selectedFlow.post(this::refreshSelectedFlow);
            return;
        }

        LinearLayout currentRow = createRow();
        int usedWidth = 0;
        int spacing = dpToPx(6);

        for (UserListAdapter.UserInfoObj m : selectedMap.values()) {
            View v = inflater.inflate(R.layout.item_selected_member, null, false);
            ImageView iv = v.findViewById(R.id.iv_avatar);
            ImageView close = v.findViewById(R.id.iv_close);
            android.widget.TextView tvName = v.findViewById(R.id.tv_name);
            AvatarUtils.loadAvatar(iv, m.getAvatar(), m.getName());
            tvName.setText(m.getName() != null ? m.getName() : m.getUserId());
            close.setOnClickListener(c -> {
                selectedMap.remove(m.getUserId());
                if (friendsFragment != null) friendsFragment.uncheckUser(m.getUserId());
                refreshSelectedFlow();
            });

            // measure view
            v.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED);
            int w = v.getMeasuredWidth();

            if (usedWidth + w > parentWidth) {
                // add current row to frame and start a new row
                FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT);
                lp.topMargin = currentRow.getTop();
                selectedFlow.addView(currentRow, lp);

                currentRow = createRow();
                usedWidth = 0;
            }

            LinearLayout.LayoutParams childLp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            childLp.setMargins(0, 0, spacing, 0);
            currentRow.addView(v, childLp);
            usedWidth += w + spacing;
        }

        // add last row
        if (currentRow.getChildCount() > 0) {
            FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT);
            selectedFlow.addView(currentRow, lp);
        }
    }

    private LinearLayout createRow() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setLayoutParams(new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT));
        return row;
    }

    private int dpToPx(int dp) {
        float density = getResources().getDisplayMetrics().density;
        return Math.round(dp * density);
    }

    private void doCreateGroup() {
        if (selectedMap.isEmpty()) {
            Toast.makeText(this, "Please select at least one friend", Toast.LENGTH_SHORT).show();
            return;
        }
        // build members array
        List<Map<String, String>> members = new ArrayList<>();
        StringBuilder nameBuilder = new StringBuilder();
        for (UserListAdapter.UserInfoObj m : selectedMap.values()) {
            Map<String, String> mm = new HashMap<>();
            mm.put("user_id", m.getUserId());
            members.add(mm);
            if (nameBuilder.length() > 0) nameBuilder.append(", ");
            nameBuilder.append(m.getName() != null ? m.getName() : m.getUserId());
        }
        Map<String, Object> body = new HashMap<>();
        body.put("group_name", nameBuilder.toString());
        body.put("group_portrait", "");
        body.put("members", members);

        btnCreate.setEnabled(false);
        ServiceManager.getUserService().createGroup(body, new ApiCallback<CreateGroupResult>() {
            @Override
            public void onSuccess(CreateGroupResult data) {
                btnCreate.setEnabled(true);
                Toast.makeText(CreateGroupActivity.this, "Group created", Toast.LENGTH_SHORT).show();
                // return to conversation list
                finish();
            }

            @Override
            public void onError(int code, String message) {
                btnCreate.setEnabled(true);
                Toast.makeText(CreateGroupActivity.this, "Create failed: " + message, Toast.LENGTH_SHORT).show();
            }
        });
    }
}

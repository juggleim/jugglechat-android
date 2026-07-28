package com.juggle.im.android.chat;

import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.juggle.im.android.R;
import com.juggle.im.android.component.AbsAppActivity;
import com.juggle.im.android.server.http.ApiCallback;
import com.juggle.im.android.server.http.ServiceManager;

import java.util.List;
import com.juggle.im.android.utils.LogUtils;

/**
 * 群组管理权限二级设置页面。
 * 用于承接“谁可以添加成员/发言/置顶”等权限项，交互与 snailchat 的 GroupAuthSettingPage 一致。
 */
public class GroupManagementRoleSettingActivity extends AbsAppActivity {
    private static final String EXTRA_GROUP_ID = "extra_group_id";
    private static final String EXTRA_SETTING_TITLE = "extra_setting_title";
    private static final String EXTRA_SETTING_KEY = "extra_setting_key";
    private static final String EXTRA_CURRENT_VALUE = "extra_current_value";

    public static final String RESULT_SETTING_KEY = "result_setting_key";
    public static final String RESULT_SETTING_VALUE = "result_setting_value";

    private String groupId;
    private String settingTitle;
    private String settingKey;
    private int selectedValue;
    private boolean submitting;

    private TextView titleView;
    private TextView saveView;
    private RoleOptionAdapter optionAdapter;

    /**
     * 构建群权限二级设置页面的启动参数。
     *
     * @param context      页面上下文
     * @param groupId      群组 ID
     * @param settingTitle 页面标题
     * @param settingKey   接口字段名，例如 group_add_member_right
     * @param currentValue 当前权限值
     * @return 启动 Intent
     */
    public static Intent intentFor(Context context,
                                   String groupId,
                                   String settingTitle,
                                   String settingKey,
                                   int currentValue) {
        Intent intent = new Intent(context, GroupManagementRoleSettingActivity.class);
        intent.putExtra(EXTRA_GROUP_ID, groupId);
        intent.putExtra(EXTRA_SETTING_TITLE, settingTitle);
        intent.putExtra(EXTRA_SETTING_KEY, settingKey);
        intent.putExtra(EXTRA_CURRENT_VALUE, currentValue);
        return intent;
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_group_management_role_setting);
        setupWindowStyle();
        initArgs();
        if (isFinishing()) {
            return;
        }
        bindViews();
        bindEvents();
    }

    private void setupWindowStyle() {
        Window window = getWindow();
        window.setStatusBarColor(getColor(R.color.white));
        window.setNavigationBarColor(getColor(R.color.white));
        WindowInsetsControllerCompat controller = new WindowInsetsControllerCompat(window, window.getDecorView());
        controller.setAppearanceLightStatusBars(true);
    }

    /**
     * 简要描述：
     * 进入页面时先校验关键参数，避免缺少 groupId 或 settingKey 时发起无效接口请求。
     */
    private void initArgs() {
        Intent intent = getIntent();
        groupId = intent.getStringExtra(EXTRA_GROUP_ID);
        settingTitle = intent.getStringExtra(EXTRA_SETTING_TITLE);
        settingKey = intent.getStringExtra(EXTRA_SETTING_KEY);
        int currentValue = intent.getIntExtra(EXTRA_CURRENT_VALUE, GroupManagementRoleHelper.SETTING_ALL);
        selectedValue = GroupManagementRoleHelper.normalizeRole(currentValue);
        if (TextUtils.isEmpty(groupId) || TextUtils.isEmpty(settingKey)) {
            Toast.makeText(this, R.string.group_role_setting_invalid_args, Toast.LENGTH_SHORT).show();
            finish();
        }
    }

    private void bindViews() {
        titleView = findViewById(R.id.tv_title);
        saveView = findViewById(R.id.tv_save);
        RecyclerView recyclerView = findViewById(R.id.rv_options);
        titleView.setText(settingTitle);

        optionAdapter = new RoleOptionAdapter(GroupManagementRoleHelper.getRoleOptions());
        optionAdapter.setSelectedValue(selectedValue);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(optionAdapter);
    }

    private void bindEvents() {
        findViewById(R.id.iv_back).setOnClickListener(v -> finish());
        saveView.setOnClickListener(v -> submitRoleSetting());
        optionAdapter.setOptionClickListener(option -> {
            selectedValue = option.getValue();
            optionAdapter.setSelectedValue(selectedValue);
        });
    }

    private void submitRoleSetting() {
        if (submitting) {
            return;
        }
        submitting = true;
        saveView.setEnabled(false);
        ServiceManager.getUserService().setGroupManagement(groupId, settingKey, selectedValue, new ApiCallback<Void>() {
            @Override
            public void onSuccess(Void data) {
                Intent result = new Intent();
                result.putExtra(RESULT_SETTING_KEY, settingKey);
                result.putExtra(RESULT_SETTING_VALUE, selectedValue);
                setResult(RESULT_OK, result);
                finish();
            }

            @Override
            public void onError(int code, String message) {
                submitting = false;
                saveView.setEnabled(true);
                LogUtils.serverError("group", "saveRoleSetting", code, message);
                Toast.makeText(GroupManagementRoleSettingActivity.this,
                        R.string.group_management_save_failed,
                        Toast.LENGTH_SHORT).show();
            }
        });
    }

    private static class RoleOptionAdapter extends RecyclerView.Adapter<RoleOptionAdapter.RoleOptionViewHolder> {
        private final List<GroupManagementRoleHelper.RoleOption> options;
        private int selectedValue = GroupManagementRoleHelper.SETTING_ALL;
        private OnOptionClickListener optionClickListener;

        RoleOptionAdapter(List<GroupManagementRoleHelper.RoleOption> options) {
            this.options = options;
        }

        void setSelectedValue(int selectedValue) {
            this.selectedValue = selectedValue;
            notifyDataSetChanged();
        }

        void setOptionClickListener(OnOptionClickListener optionClickListener) {
            this.optionClickListener = optionClickListener;
        }

        @NonNull
        @Override
        public RoleOptionViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_group_management_role_option, parent, false);
            return new RoleOptionViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull RoleOptionViewHolder holder, int position) {
            GroupManagementRoleHelper.RoleOption option = options.get(position);
            holder.titleView.setText(option.getTitleRes());
            holder.checkedView.setVisibility(option.getValue() == selectedValue ? View.VISIBLE : View.INVISIBLE);

            GradientDrawable dotDrawable = new GradientDrawable();
            dotDrawable.setShape(GradientDrawable.OVAL);
            dotDrawable.setColor(option.getDotColor());
            holder.dotView.setBackground(dotDrawable);

            holder.dividerView.setVisibility(position == options.size() - 1 ? View.GONE : View.VISIBLE);
            holder.itemView.setOnClickListener(v -> {
                if (optionClickListener != null) {
                    optionClickListener.onOptionClick(option);
                }
            });
        }

        @Override
        public int getItemCount() {
            return options == null ? 0 : options.size();
        }

        static class RoleOptionViewHolder extends RecyclerView.ViewHolder {
            final View dotView;
            final TextView titleView;
            final ImageView checkedView;
            final View dividerView;

            RoleOptionViewHolder(@NonNull View itemView) {
                super(itemView);
                dotView = itemView.findViewById(R.id.v_dot);
                titleView = itemView.findViewById(R.id.tv_title);
                checkedView = itemView.findViewById(R.id.iv_checked);
                dividerView = itemView.findViewById(R.id.v_divider);
            }
        }

        interface OnOptionClickListener {
            void onOptionClick(GroupManagementRoleHelper.RoleOption option);
        }
    }
}

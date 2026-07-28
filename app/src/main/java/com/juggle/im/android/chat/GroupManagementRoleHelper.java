package com.juggle.im.android.chat;

import androidx.annotation.StringRes;

import com.juggle.im.android.R;
import com.juggle.im.android.i18n.AppRes;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 群组管理权限值转换工具。
 * 统一处理服务端权限位值与 UI 三档权限文案之间的映射关系，避免多页面出现不一致。
 */
public final class GroupManagementRoleHelper {
    public static final int SETTING_OWNER = 1;
    public static final int SETTING_ADMIN = 2;
    public static final int SETTING_MEMBER = 4;
    public static final int SETTING_ADMIN_OWNER = SETTING_OWNER | SETTING_ADMIN;
    public static final int SETTING_OWNER_MEMBER = SETTING_OWNER | SETTING_MEMBER;
    public static final int SETTING_ADMIN_MEMBER = SETTING_ADMIN | SETTING_MEMBER;
    public static final int SETTING_ALL = SETTING_OWNER | SETTING_ADMIN | SETTING_MEMBER;

    private static final List<RoleOption> ROLE_OPTIONS;

    static {
        List<RoleOption> options = new ArrayList<>();
        options.add(new RoleOption(R.string.group_role_owner_only, SETTING_OWNER, 0xFF5B0BE6));
        options.add(new RoleOption(R.string.group_role_all_members, SETTING_ALL, 0xFFE0493E));
        options.add(new RoleOption(R.string.group_role_owner_admin, SETTING_ADMIN_OWNER, 0xFFE03AE5));
        ROLE_OPTIONS = Collections.unmodifiableList(options);
    }

    private GroupManagementRoleHelper() {
    }

    /**
     * 返回二级设置页的权限选项列表。
     *
     * @return 可展示的权限选项（与 snailchat 保持一致）
     */
    public static List<RoleOption> getRoleOptions() {
        return ROLE_OPTIONS;
    }

    /**
     * 将服务端权限值归一化为 UI 可编辑的三档权限。
     *
     * @param value 服务端权限值
     * @return 归一化后的权限值（仅群主 / 群主和管理员 / 全部成员）
     */
    public static int normalizeRole(int value) {
        if (value == SETTING_OWNER || value == SETTING_ADMIN_OWNER || value == SETTING_ALL) {
            return value;
        }
        if (value == SETTING_OWNER_MEMBER) {
            return SETTING_OWNER;
        }
        if (value == SETTING_ADMIN_MEMBER || value == SETTING_ADMIN) {
            return SETTING_ADMIN_OWNER;
        }
        if (value == SETTING_MEMBER) {
            return SETTING_ALL;
        }
        return SETTING_ALL;
    }

    /**
     * 根据权限值输出设置页展示文案。
     *
     * @param role 权限值
     * @return 权限文案
     */
    public static String roleToText(int role) {
        int normalized = normalizeRole(role);
        if (normalized == SETTING_OWNER) {
            return AppRes.string(R.string.group_role_owner_only);
        }
        if (normalized == SETTING_ADMIN_OWNER) {
            return AppRes.string(R.string.group_role_owner_admin);
        }
        return AppRes.string(R.string.group_role_all_members);
    }

    /**
     * 判断当前群角色是否拥有指定设置权限。
     *
     * @param settingRight 服务端下发的权限位
     * @param memberRole 当前成员角色：0-成员、1-群主、2-管理员
     * @return true 表示允许执行设置
     */
    public static boolean hasSettingPermission(int settingRight, int memberRole) {
        int roleBit;
        if (memberRole == SETTING_OWNER) {
            roleBit = SETTING_OWNER;
        } else if (memberRole == SETTING_ADMIN) {
            roleBit = SETTING_ADMIN;
        } else if (memberRole == 0) {
            roleBit = SETTING_MEMBER;
        } else {
            return false;
        }
        return (settingRight & roleBit) == roleBit;
    }

    /**
     * 群权限选项模型。
     */
    public static final class RoleOption {
        @StringRes
        private final int titleRes;
        private final int value;
        private final int dotColor;

        public RoleOption(@StringRes int titleRes, int value, int dotColor) {
            this.titleRes = titleRes;
            this.value = value;
            this.dotColor = dotColor;
        }

        /**
         * TIPS: 返回资源 id 而非文案，语言切换后重建界面即可拿到新文案
         *
         * @return 选项标题资源 id
         */
        @StringRes
        public int getTitleRes() {
            return titleRes;
        }

        /**
         * @return 选项对应的权限值
         */
        public int getValue() {
            return value;
        }

        /**
         * @return 选项左侧圆点颜色
         */
        public int getDotColor() {
            return dotColor;
        }
    }
}

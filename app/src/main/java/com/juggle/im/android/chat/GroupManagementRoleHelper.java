package com.juggle.im.android.chat;

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
        options.add(new RoleOption("仅群主", SETTING_OWNER, 0xFF5B0BE6));
        options.add(new RoleOption("全部成员", SETTING_ALL, 0xFFE0493E));
        options.add(new RoleOption("群主和管理员", SETTING_ADMIN_OWNER, 0xFFE03AE5));
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
            return "仅群主";
        }
        if (normalized == SETTING_ADMIN_OWNER) {
            return "群主和管理员";
        }
        return "全部成员";
    }

    /**
     * 群权限选项模型。
     */
    public static final class RoleOption {
        private final String title;
        private final int value;
        private final int dotColor;

        public RoleOption(String title, int value, int dotColor) {
            this.title = title;
            this.value = value;
            this.dotColor = dotColor;
        }

        /**
         * @return 选项标题
         */
        public String getTitle() {
            return title;
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

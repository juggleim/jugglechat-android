package com.juggle.im.android.chat;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * 群设置权限位判断测试。
 */
public class GroupManagementRoleHelperTest {

    @Test
    public void shouldMatchSettingRightsToCurrentMemberRole() {
        assertTrue(GroupManagementRoleHelper.hasSettingPermission(
                GroupManagementRoleHelper.SETTING_OWNER, 1));
        assertFalse(GroupManagementRoleHelper.hasSettingPermission(
                GroupManagementRoleHelper.SETTING_OWNER, 0));
        assertFalse(GroupManagementRoleHelper.hasSettingPermission(
                GroupManagementRoleHelper.SETTING_OWNER, 2));

        assertTrue(GroupManagementRoleHelper.hasSettingPermission(
                GroupManagementRoleHelper.SETTING_ADMIN_OWNER, 1));
        assertTrue(GroupManagementRoleHelper.hasSettingPermission(
                GroupManagementRoleHelper.SETTING_ADMIN_OWNER, 2));
        assertFalse(GroupManagementRoleHelper.hasSettingPermission(
                GroupManagementRoleHelper.SETTING_ADMIN_OWNER, 0));

        assertTrue(GroupManagementRoleHelper.hasSettingPermission(
                GroupManagementRoleHelper.SETTING_ALL, 0));
        assertTrue(GroupManagementRoleHelper.hasSettingPermission(
                GroupManagementRoleHelper.SETTING_ALL, 1));
        assertTrue(GroupManagementRoleHelper.hasSettingPermission(
                GroupManagementRoleHelper.SETTING_ALL, 2));
        assertFalse(GroupManagementRoleHelper.hasSettingPermission(
                GroupManagementRoleHelper.SETTING_ALL, 3));
    }
}

package com.juggle.im.android.server.beans;

import com.google.gson.annotations.SerializedName;

public class GroupManagementBean {
    public int getGroupMute() {
        return groupMute;
    }

    public void setGroupMute(int groupMute) {
        this.groupMute = groupMute;
    }

    public int getMaxAdminCount() {
        return maxAdminCount;
    }

    public void setMaxAdminCount(int maxAdminCount) {
        this.maxAdminCount = maxAdminCount;
    }

    public int getAdminCount() {
        return adminCount;
    }

    public void setAdminCount(int adminCount) {
        this.adminCount = adminCount;
    }

    public int getGroupVerifyType() {
        return groupVerifyType;
    }

    public void setGroupVerifyType(int groupVerifyType) {
        this.groupVerifyType = groupVerifyType;
    }

    public int getHistoryMessageVisible() {
        return historyMessageVisible;
    }

    public void setHistoryMessageVisible(int historyMessageVisible) {
        this.historyMessageVisible = historyMessageVisible;
    }

    public int getGroupAddMemberRight() {
        return groupAddMemberRight;
    }

    public void setGroupAddMemberRight(int groupAddMemberRight) {
        this.groupAddMemberRight = groupAddMemberRight;
    }

    public int getGroupTopMsgRight() {
        return groupTopMsgRight;
    }

    public void setGroupTopMsgRight(int groupTopMsgRight) {
        this.groupTopMsgRight = groupTopMsgRight;
    }

    public int getGroupMentionAllRight() {
        return groupMentionAllRight;
    }

    public void setGroupMentionAllRight(int groupMentionAllRight) {
        this.groupMentionAllRight = groupMentionAllRight;
    }

    public int getGroupEditMsgRight() {
        return groupEditMsgRight;
    }

    public void setGroupEditMsgRight(int groupEditMsgRight) {
        this.groupEditMsgRight = groupEditMsgRight;
    }

    public int getGroupSendMsgRight() {
        return groupSendMsgRight;
    }

    public void setGroupSendMsgRight(int groupSendMsgRight) {
        this.groupSendMsgRight = groupSendMsgRight;
    }

    public int getGroupSetMsgLifeRight() {
        return groupSetMsgLifeRight;
    }

    public void setGroupSetMsgLifeRight(int groupSetMsgLifeRight) {
        this.groupSetMsgLifeRight = groupSetMsgLifeRight;
    }

    @SerializedName("group_mute")
    private int groupMute;
    @SerializedName("max_admin_count")
    private int maxAdminCount;
    @SerializedName("admin_count")
    private int adminCount;
    @SerializedName("group_verify_type")
    private int groupVerifyType;
    @SerializedName("group_his_msg_visible")
    private int historyMessageVisible;
    @SerializedName("group_add_member_right")
    private int groupAddMemberRight;
    @SerializedName("group_top_msg_right")
    private int groupTopMsgRight;
    @SerializedName("group_mention_all_right")
    private int groupMentionAllRight;
    @SerializedName("group_edit_msg_right")
    private int groupEditMsgRight;
    @SerializedName("group_send_msg_right")
    private int groupSendMsgRight;
    @SerializedName("group_set_msg_life_right")
    private int groupSetMsgLifeRight;

}

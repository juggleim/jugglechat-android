package com.juggle.im.android.server.http;

import com.juggle.im.android.server.beans.*;

import java.util.List;

/**
 * Plain interface for user-related API operations. Implementations should
 * perform
 * network requests and invoke the provided {@link ApiCallback} on the UI
 * thread.
 */
public interface UserService {
    void getVerificationCode(CodeRequest phone, ApiCallback<Void> callback);

    void login(LoginRequest phone, ApiCallback<LoginResult> callback);

    void register(RegisterRequest request, ApiCallback<LoginResult> callback);

    void updateUserInfo(UserInfoRequest userInfo, ApiCallback<Void> callback);

    void setAccount(String account, ApiCallback<Void> callback);

    /**
     * 修改当前用户密码。
     *
     * @param userId       用户 ID
     * @param password     原密码（MD5）
     * @param newPassword  新密码（MD5）
     * @param callback     请求回调
     */
    void updatePassword(String userId, String password, String newPassword, ApiCallback<Void> callback);

    void getUserInfo(String userId, ApiCallback<UserInfoBean> callback);

    void getQRCode(ApiCallback<QRCodeBean> callback);

    /**
     * Get friends list with optional pagination and order tag (pinyin initial).
     * page starts from 1, size default 20, max 50.
     */
    void getFriendsList(Integer page, Integer size, String orderTag, ApiCallback<FriendsListData> callback);

    /**
     * Search users by keyword. POST /jim/users/search {keyword: "..."}
     */
    void searchUsers(String keyword, ApiCallback<FriendsListData> callback);

    void searchFriends(String keyword, int offset, int limit, ApiCallback<FriendsListData> callback);

    void searchMyGroups(String keyword, int limit, ApiCallback<GroupListData> callback);

    public void myGroups(ApiCallback<GroupListData> callback);
    /**
     * 发送好友申请。
     * POST /jim/friends/apply {friend_id: "..."}
     *
     * @param friendId 目标好友 ID
     * @param callback 请求回调
     */
    void applyFriend(String friendId, ApiCallback<com.juggle.im.android.server.beans.FriendApplicationBean> callback);

    /**
     * 删除联系人。
     * POST /jim/friends/del {friend_ids: ["..."]}
     *
     * @param friendIds 待删除的联系人 ID 列表
     * @param callback 请求回调
     */
    void removeFriends(List<String> friendIds, ApiCallback<Void> callback);

    /**
     * Create a group with name, portrait and members. POST /jim/groups/add
     */
    void createGroup(Object body, ApiCallback<com.juggle.im.android.server.beans.CreateGroupResult> callback);

    public void getGroupInfo(String groupId, ApiCallback<GroupDetailBean> callback);

    void getGroupAnnouncement(String groupId, ApiCallback<GroupAnnouncementBean> callback);

    void getGroupQRCode(String groupId, ApiCallback<QRCodeBean> callback);

    void setGroupAnnouncement(String groupId, String content, ApiCallback<Void> callback);

    /**
     * 加群
     * 
     * @param groupId
     * @param userIds
     * @param callback
     */
    public void inviteJoinGroup(String groupId, List<String> userIds, ApiCallback<Void> callback);

    void removeGroupMembers(String groupId, List<String> userIds, ApiCallback<Void> callback);

    void setGroupMemberMute(String groupId, List<String> userIds, boolean isMute, ApiCallback<Void> callback);

    void setGroupHistoryMessageVisible(String groupId, boolean visible, ApiCallback<Void> callback);

    void setGroupManagement(String groupId, String managementType, int value, ApiCallback<Void> callback);

    void getGroupAdmins(String groupId, ApiCallback<GroupMembersData> callback);

    void addGroupAdmins(String groupId, List<String> adminIds, ApiCallback<Void> callback);

    void removeGroupAdmins(String groupId, List<String> adminIds, ApiCallback<Void> callback);

    void changeGroupOwner(String groupId, String ownerId, ApiCallback<Void> callback);

    void updateGroupInfo(String groupId, String groupName, String groupPortrait, ApiCallback<Void> callback);

    void setGroupDisplayName(String groupId, String displayName, ApiCallback<Void> callback);

    void quitGroup(String groupId, ApiCallback<Void> callback);

    void dissolveGroup(String groupId, ApiCallback<Void> callback);

    /**
     * Get friend applications list. GET /jim/friends/applications
     *
     * @param start Starting index for pagination
     * @param count Number of items to fetch (default 50)
     */
    void getFriendApplications(int start, int count, ApiCallback<FriendApplicationsData> callback);

    /**
     * 接受好友申请。POST /jim/friends/confirm {sponsor_id: "...", is_agree: true}
     * @param sponsorId 好友申请发起人ID
     */
    void acceptFriendApplication(String sponsorId, ApiCallback<Void> callback);

    /**
     * 拒绝好友申请。POST /jim/friends/confirm {sponsor_id: "...", is_agree: false}
     * @param sponsorId 好友申请发起人ID
     */
    void refuseFriendApplication(String sponsorId, ApiCallback<Void> callback);

    /**
     * Query block users list. GET /jim/users/blockusers/list
     */
    void getBlockUsers(int count, String offset, ApiCallback<BlockUsersData> callback);

    void submitFeedback(String category, String text, List<String> images, List<String> videos, ApiCallback<Void> callback);

    /**
     * 获取指定会话的消息配置。
     *
     * @param targetId 会话目标 ID
     * @param conversationType 会话类型
     * @param subChannel 子频道；无子频道时传空字符串
     * @param callback 请求回调
     */
    void getConversationConfig(String targetId,
                               int conversationType,
                               String subChannel,
                               ApiCallback<ConversationConfigBean> callback);

    /**
     * 设置指定会话的新消息自动删除周期。
     *
     * @param targetId 会话目标 ID
     * @param conversationType 会话类型
     * @param subChannel 子频道；无子频道时传空字符串
     * @param messageLifeTimeDays 自动删除天数，0 表示关闭
     * @param callback 请求回调
     */
    void setConversationMessageLifeTime(String targetId,
                                        int conversationType,
                                        String subChannel,
                                        int messageLifeTimeDays,
                                        ApiCallback<Void> callback);
}

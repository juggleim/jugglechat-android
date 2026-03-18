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

    /**
     * Apply (send friend request) to a user. POST /jim/friends/apply {friend_id:
     * "..."}
     */
    void applyFriend(String friendId, ApiCallback<com.juggle.im.android.server.beans.FriendApplicationBean> callback);

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
     * Query block users list. GET /jim/users/blockusers/list
     */
    void getBlockUsers(int count, String offset, ApiCallback<BlockUsersData> callback);

    void submitFeedback(String category, String text, List<String> images, List<String> videos, ApiCallback<Void> callback);
}

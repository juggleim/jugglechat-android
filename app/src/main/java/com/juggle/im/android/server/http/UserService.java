package com.juggle.im.android.server.http;

import com.juggle.im.android.server.beans.*;

/**
 * Plain interface for user-related API operations. Implementations should perform
 * network requests and invoke the provided {@link ApiCallback} on the UI thread.
 */
public interface UserService {
    void getSmsVerificationCode(CodeRequest phone, ApiCallback<Void> callback);

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

    /**
     * Apply (send friend request) to a user. POST /jim/friends/apply {friend_id: "..."}
     */
    void applyFriend(String friendId, ApiCallback<com.juggle.im.android.server.beans.FriendApplicationBean> callback);

    /**
     * Create a group with name, portrait and members. POST /jim/groups/add
     */
    void createGroup(Object body, ApiCallback<com.juggle.im.android.server.beans.CreateGroupResult> callback);

    public void getGroupInfo(String groupId, ApiCallback<GroupDetailBean> callback);
}

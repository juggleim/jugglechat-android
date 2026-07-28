package com.juggle.im.android.server.http;

import android.os.Handler;
import android.os.Looper;

import com.juggle.im.android.server.beans.GroupAnnouncementBean;
import com.juggle.im.android.server.beans.GroupDetailBean;
import com.juggle.im.android.server.beans.GroupListData;
import com.juggle.im.android.server.beans.GroupMembersData;
import com.juggle.im.android.server.beans.BlockUsersData;
import com.juggle.im.android.server.beans.LoginRequest;
import com.juggle.im.android.server.beans.LoginResult;
import com.juggle.im.android.server.beans.CodeRequest;
import com.juggle.im.android.server.beans.RegisterRequest;
import com.juggle.im.android.server.beans.UserInfoBean;
import com.juggle.im.android.server.beans.UserInfoRequest;
import com.juggle.im.android.server.beans.QRCodeBean;
import com.juggle.im.android.server.beans.FriendsListData;
import java.util.ArrayList;
import java.util.List;
import okhttp3.OkHttpClient;
import com.juggle.im.android.R;
import com.juggle.im.android.i18n.AppRes;

/**
 * OkHttp-based implementation of UserService. Calls run network requests
 * synchronously
 * on a background thread and dispatch callbacks on the main (UI) thread.
 */
public class UserServiceImpl extends BaseService implements UserService {
    private static final int CODE_INVALID_PROFILE_REQUEST = 4001;

    public UserServiceImpl(OkHttpClient client, String baseUrl) {
        super(client, baseUrl);
    }

    @Override
    public void getVerificationCode(CodeRequest request, ApiCallback<Void> callback) {
        if (request != null && request.getEmail() != null && !request.getEmail().trim().isEmpty()) {
            java.util.Map<String, String> body = new java.util.HashMap<>();
            body.put("email", request.getEmail().trim());
            enqueueJson("/jim/email/send", body, Void.class, callback);
            return;
        }
        enqueueJson("/jim/sms/send", request, Void.class, callback);
    }

    @Override
    public void login(LoginRequest request, ApiCallback<LoginResult> callback) {
        request.setPhone(request.getPhone());
        request.setPassword(request.getPassword());
        enqueueJson("/jim/login", request, LoginResult.class, callback);
    }

    @Override
    public void register(RegisterRequest request, ApiCallback<LoginResult> callback) {
        enqueueJson("/jim/register", request, LoginResult.class, callback);
    }

    @Override
    public void updateUserInfo(UserInfoRequest userInfo, ApiCallback<Void> callback) {
        if (userInfo == null) {
            dispatchProfileValidationError(callback, AppRes.string(R.string.profile_validation_empty));
            return;
        }
        if (!userInfo.hasValidAvatarProtocol()) {
            dispatchProfileValidationError(callback, AppRes.string(R.string.profile_validation_avatar));
            return;
        }
        enqueueJson("/jim/users/update", userInfo, Void.class, callback);
    }

    @Override
    public void setAccount(String account, ApiCallback<Void> callback) {
        java.util.Map<String, Object> body = new java.util.HashMap<>();
        body.put("account", account == null ? "" : account.trim());
        enqueueJson("/jim/users/setaccount", body, Void.class, callback);
    }

    /**
     * 修改当前用户密码。
     *
     * @param userId      用户 ID
     * @param password    原密码（MD5）
     * @param newPassword 新密码（MD5）
     * @param callback    请求回调
     */
    @Override
    public void updatePassword(String userId, String password, String newPassword, ApiCallback<Void> callback) {
        java.util.Map<String, Object> body = new java.util.HashMap<>();
        body.put("user_id", userId == null ? "" : userId.trim());
        body.put("password", password == null ? "" : password.trim());
        body.put("new_password", newPassword == null ? "" : newPassword.trim());
        enqueueJson("/jim/users/updatepwd", body, Void.class, callback);
    }

    @Override
    public void getUserInfo(String userId, ApiCallback<UserInfoBean> callback) {
        enqueueGet("/jim/users/info?user_id=" + userId, UserInfoBean.class, callback);
    }

    @Override
    public void getQRCode(ApiCallback<QRCodeBean> callback) {
        enqueueGet("/jim/users/qrcode", QRCodeBean.class, callback);
    }

    @Override
    public void getFriendsList(Integer page, Integer size, String orderTag, ApiCallback<FriendsListData> callback) {
        // apply defaults and bounds according to API spec
        int p = (page == null || page < 1) ? 1 : page;
        int s = (size == null || size < 1) ? 20 : size;
        if (s > 50)
            s = 50;
        StringBuilder sb = new StringBuilder("/jim/friends/list?");
        sb.append("page=").append(p).append("&size=").append(s);
        if (orderTag != null && !orderTag.isEmpty()) {
            sb.append("&order_tag=").append(orderTag);
        }
        enqueueGet(sb.toString(), FriendsListData.class, callback);
    }

    @Override
    public void searchUsers(String keyword, ApiCallback<FriendsListData> callback) {
        // POST {keyword: "..."}
        java.util.Map<String, String> body = new java.util.HashMap<>();
        body.put("keyword", keyword == null ? "" : keyword);
        enqueueJson("/jim/users/search", (Object) body, FriendsListData.class, callback);
    }

    @Override
    public void searchFriends(String keyword, int offset, int limit, ApiCallback<FriendsListData> callback) {
        java.util.Map<String, Object> body = new java.util.HashMap<>();
        body.put("key", keyword == null ? "" : keyword);
        body.put("limit", limit);
        enqueueJson("/jim/friends/search", (Object) body, FriendsListData.class, callback);
    }

    @Override
    public void searchMyGroups(String keyword, int limit, ApiCallback<GroupListData> callback) {
        java.util.Map<String, Object> body = new java.util.HashMap<>();
        body.put("keyword", keyword == null ? "" : keyword);
        body.put("limit", limit);
        enqueueJson("/jim/groups/mygroups/search", body, GroupListData.class, callback);
    }

    @Override
    public void myGroups(ApiCallback<GroupListData> callback) {
        enqueueGet("/jim/groups/mygroups", GroupListData.class, callback);
    }

    @Override
    public void applyFriend(String friendId,
            ApiCallback<com.juggle.im.android.server.beans.FriendApplicationBean> callback) {
        java.util.Map<String, String> body = new java.util.HashMap<>();
        body.put("friend_id", friendId == null ? "" : friendId);
        enqueueJson("/jim/friends/apply", body, com.juggle.im.android.server.beans.FriendApplicationBean.class,
                callback);
    }

    /**
     * 删除联系人。
     *
     * @param friendIds 待删除的联系人 ID 列表
     * @param callback 请求回调
     */
    @Override
    public void removeFriends(List<String> friendIds, ApiCallback<Void> callback) {
        java.util.Map<String, Object> body = new java.util.HashMap<>();
        body.put("friend_ids", friendIds == null ? new ArrayList<>() : friendIds);
        enqueueJson("/jim/friends/del", body, Void.class, callback);
    }

    @Override
    public void createGroup(Object body, ApiCallback<com.juggle.im.android.server.beans.CreateGroupResult> callback) {
        enqueueJson("/jim/groups/add", body, com.juggle.im.android.server.beans.CreateGroupResult.class, callback);
    }

    @Override
    public void getGroupInfo(String groupId, ApiCallback<GroupDetailBean> callback) {
        StringBuilder sb = new StringBuilder("/jim/groups/info?group_id");
        sb.append("=").append(groupId);
        enqueueGet(sb.toString(), GroupDetailBean.class, callback);
    }

    @Override
    public void getGroupAnnouncement(String groupId, ApiCallback<GroupAnnouncementBean> callback) {
        StringBuilder sb = new StringBuilder("/jim/groups/getgrpannouncement?group_id");
        sb.append("=").append(groupId);
        enqueueGet(sb.toString(), GroupAnnouncementBean.class, callback);
    }

    @Override
    public void getGroupQRCode(String groupId, ApiCallback<QRCodeBean> callback) {
        StringBuilder sb = new StringBuilder("/jim/groups/qrcode?group_id");
        sb.append("=").append(groupId);
        enqueueGet(sb.toString(), QRCodeBean.class, callback);
    }

    @Override
    public void setGroupAnnouncement(String groupId, String content, ApiCallback<Void> callback) {
        java.util.Map<String, Object> body = new java.util.HashMap<>();
        body.put("group_id", groupId);
        body.put("content", content == null ? "" : content);
        enqueueJson("/jim/groups/setgrpannouncement", body, Void.class, callback);
    }

    @Override
    public void inviteJoinGroup(String groupId, List<String> userIds, ApiCallback<Void> callback) {
        java.util.Map<String, Object> body = new java.util.HashMap<>();
        body.put("group_id", groupId);
        body.put("member_ids", userIds == null ? new ArrayList<>() : userIds);
        enqueueJson("/jim/groups/invite", body, Void.class, callback);
    }

    @Override
    public void removeGroupMembers(String groupId, List<String> userIds, ApiCallback<Void> callback) {
        java.util.Map<String, Object> body = new java.util.HashMap<>();
        body.put("group_id", groupId);
        body.put("member_ids", userIds == null ? new ArrayList<>() : userIds);
        enqueueJson("/jim/groups/members/del", body, Void.class, callback);
    }

    @Override
    public void setGroupMemberMute(String groupId, List<String> userIds, boolean isMute, ApiCallback<Void> callback) {
        java.util.Map<String, Object> body = new java.util.HashMap<>();
        body.put("group_id", groupId);
        body.put("member_ids", userIds == null ? new ArrayList<>() : userIds);
        body.put("is_mute", isMute ? 1 : 0);
        enqueueJson("/jim/groups/management/setgrpmembersmute", body, Void.class, callback);
    }

    @Override
    public void setGroupHistoryMessageVisible(String groupId, boolean visible, ApiCallback<Void> callback) {
        java.util.Map<String, Object> body = new java.util.HashMap<>();
        body.put("group_id", groupId);
        body.put("group_his_msg_visible", visible ? 1 : 0);
        enqueueJson("/jim/groups/management/sethismsgvisible", body, Void.class, callback);
    }

    @Override
    public void setGroupManagement(String groupId, String managementType, int value, ApiCallback<Void> callback) {
        java.util.Map<String, Object> body = new java.util.HashMap<>();
        body.put("group_id", groupId);
        if (managementType != null && !managementType.trim().isEmpty()) {
            body.put(managementType, value);
        }
        enqueueJson("/jim/groups/management/set", body, Void.class, callback);
    }

    @Override
    public void getGroupAdmins(String groupId, ApiCallback<GroupMembersData> callback) {
        StringBuilder sb = new StringBuilder("/jim/groups/management/administrators/list?group_id");
        sb.append("=").append(groupId);
        enqueueGet(sb.toString(), GroupMembersData.class, callback);
    }

    @Override
    public void addGroupAdmins(String groupId, List<String> adminIds, ApiCallback<Void> callback) {
        java.util.Map<String, Object> body = new java.util.HashMap<>();
        body.put("group_id", groupId);
        body.put("admin_ids", adminIds == null ? new ArrayList<>() : adminIds);
        enqueueJson("/jim/groups/management/administrators/add", body, Void.class, callback);
    }

    @Override
    public void removeGroupAdmins(String groupId, List<String> adminIds, ApiCallback<Void> callback) {
        java.util.Map<String, Object> body = new java.util.HashMap<>();
        body.put("group_id", groupId);
        body.put("admin_ids", adminIds == null ? new ArrayList<>() : adminIds);
        enqueueJson("/jim/groups/management/administrators/del", body, Void.class, callback);
    }

    @Override
    public void changeGroupOwner(String groupId, String ownerId, ApiCallback<Void> callback) {
        java.util.Map<String, Object> body = new java.util.HashMap<>();
        body.put("group_id", groupId);
        body.put("owner_id", ownerId == null ? "" : ownerId);
        enqueueJson("/jim/groups/management/chgowner", body, Void.class, callback);
    }

    @Override
    public void updateGroupInfo(String groupId, String groupName, String groupPortrait, ApiCallback<Void> callback) {
        java.util.Map<String, Object> body = new java.util.HashMap<>();
        body.put("group_id", groupId);
        body.put("group_name", groupName == null ? "" : groupName);
        body.put("group_portrait", groupPortrait == null ? "" : groupPortrait);
        enqueueJson("/jim/groups/update", body, Void.class, callback);
    }

    @Override
    public void setGroupDisplayName(String groupId, String displayName, ApiCallback<Void> callback) {
        java.util.Map<String, Object> body = new java.util.HashMap<>();
        body.put("group_id", groupId);
        body.put("grp_display_name", displayName == null ? "" : displayName);
        enqueueJson("/jim/groups/setdisplayname", body, Void.class, callback);
    }

    @Override
    public void quitGroup(String groupId, ApiCallback<Void> callback) {
        java.util.Map<String, Object> body = new java.util.HashMap<>();
        body.put("group_id", groupId);
        enqueueJson("/jim/groups/quit", body, Void.class, callback);
    }

    @Override
    public void dissolveGroup(String groupId, ApiCallback<Void> callback) {
        java.util.Map<String, Object> body = new java.util.HashMap<>();
        body.put("group_id", groupId);
        enqueueJson("/jim/groups/dissolve", body, Void.class, callback);
    }

    @Override
    public void getFriendApplications(int start, int count,
            ApiCallback<com.juggle.im.android.server.beans.FriendApplicationsData> callback) {
        StringBuilder sb = new StringBuilder("/jim/friends/applications?");
        sb.append("start=").append(start).append("&count=").append(count);
        enqueueGet(sb.toString(), com.juggle.im.android.server.beans.FriendApplicationsData.class, callback);
    }

    @Override
    public void acceptFriendApplication(String sponsorId, ApiCallback<Void> callback) {
        java.util.Map<String, Object> body = new java.util.HashMap<>();
        body.put("sponsor_id", sponsorId == null ? "" : sponsorId);
        body.put("is_agree", true);
        enqueueJson("/jim/friends/confirm", body, Void.class, callback);
    }

    @Override
    public void refuseFriendApplication(String sponsorId, ApiCallback<Void> callback) {
        java.util.Map<String, Object> body = new java.util.HashMap<>();
        body.put("sponsor_id", sponsorId == null ? "" : sponsorId);
        body.put("is_agree", false);
        enqueueJson("/jim/friends/confirm", body, Void.class, callback);
    }

    @Override
    public void getBlockUsers(int count, String offset, ApiCallback<BlockUsersData> callback) {
        int c = count <= 0 ? 20 : count;
        StringBuilder sb = new StringBuilder("/jim/users/blockusers/list?");
        sb.append("count=").append(c);
        if (offset != null && !offset.isEmpty()) {
            sb.append("&offset=").append(offset);
        }
        enqueueGet(sb.toString(), BlockUsersData.class, callback);
    }

    @Override
    public void submitFeedback(String category, String text, List<String> images, List<String> videos, ApiCallback<Void> callback) {
        java.util.Map<String, Object> body = new java.util.HashMap<>();
        body.put("category", category == null ? "" : category);
        body.put("text", text == null ? "" : text);
        body.put("images", images == null ? new ArrayList<>() : images);
        body.put("videos", videos == null ? new ArrayList<>() : videos);
        enqueueJson("/jim/feedbacks/add", body, Void.class, callback);
    }

    private void dispatchProfileValidationError(ApiCallback<Void> callback, String message) {
        if (callback == null) {
            return;
        }
        new Handler(Looper.getMainLooper()).post(() ->
                callback.onError(CODE_INVALID_PROFILE_REQUEST, message));
    }
}

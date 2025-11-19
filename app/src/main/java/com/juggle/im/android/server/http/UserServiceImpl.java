package com.juggle.im.android.server.http;

import android.os.Handler;
import android.os.Looper;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import com.juggle.im.android.server.beans.GroupBean;
import com.juggle.im.android.server.beans.GroupDetailBean;
import com.juggle.im.android.server.beans.GroupListData;
import com.juggle.im.android.server.beans.HttpResult;
import com.juggle.im.android.server.beans.LoginRequest;
import com.juggle.im.android.server.beans.LoginResult;
import com.juggle.im.android.server.beans.CodeRequest;
import com.juggle.im.android.server.beans.RegisterRequest;
import com.juggle.im.android.server.beans.SearchUserBean;
import com.juggle.im.android.server.beans.UserInfoBean;
import com.juggle.im.android.server.beans.UserInfoRequest;
import com.juggle.im.android.server.beans.QRCodeBean;
import com.juggle.im.android.server.beans.FriendsListData;
import com.qiniu.android.utils.MD5;

import java.io.IOException;
import java.util.List;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * OkHttp-based implementation of UserService. Calls run network requests synchronously
 * on a background thread and dispatch callbacks on the main (UI) thread.
 */
public class UserServiceImpl extends BaseService implements UserService {
    public UserServiceImpl(OkHttpClient client, String baseUrl) {
        super(client, baseUrl);
    }
    @Override
    public void getSmsVerificationCode(CodeRequest request, ApiCallback<Void> callback) {
        enqueueJson("/jim/sms/send", request, Void.class, callback);
    }

    @Override
    public void login(LoginRequest request, ApiCallback<LoginResult> callback) {
        String pwd = MD5.encrypt(request.getPassword().getBytes());
        request.setPassword(pwd);
        enqueueJson("/jim/login", request, LoginResult.class, callback);
    }

    @Override
    public void register(RegisterRequest request, ApiCallback<LoginResult> callback) {
        enqueueJson("/jim/register", request, LoginResult.class, callback);
    }

    @Override
    public void updateUserInfo(UserInfoRequest userInfo, ApiCallback<Void> callback) {
        enqueueJson("/jim/users/update", userInfo, Void.class, callback);
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
        if (s > 50) s = 50;
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
    public void applyFriend(String friendId, ApiCallback<com.juggle.im.android.server.beans.FriendApplicationBean> callback) {
        java.util.Map<String, String> body = new java.util.HashMap<>();
        body.put("friend_id", friendId == null ? "" : friendId);
        enqueueJson("/jim/friends/apply", body, com.juggle.im.android.server.beans.FriendApplicationBean.class, callback);
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
    public void inviteJoinGroup(String groupId, List<String> userIds, ApiCallback<Void> callback) {
        StringBuilder sb = new StringBuilder("/jim/groups/invite");
        java.util.Map<String, Object> body = new java.util.HashMap<>();
        body.put("group_id", groupId);
        body.put("member_ids", userIds);
        enqueueJson(sb.toString(), body, Void.class, callback);
    }
}

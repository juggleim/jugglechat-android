package com.juggle.im.android.chat.message;

import android.text.TextUtils;
import android.util.Log;

import com.juggle.im.JIM;
import com.juggle.im.model.MessageContent;
import com.juggle.im.model.UserInfo;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import com.juggle.im.android.i18n.AppRes;
import com.juggle.im.android.R;

public class GroupNotifyMessage extends MessageContent {
    public final static String ACTION = "jgd:grpntf";

    public GroupNotifyMessage() {
        mContentType = ACTION;
    }

    @Override
    public int getFlags() {
        return MessageFlag.IS_SAVE.getValue();
    }

    @Override
    public byte[] encode() {
        JSONArray membersJson = new JSONArray();
        for (UserInfo userInfo : mMembers) {
            JSONObject member = jsonFromUserInfo(userInfo);
            membersJson.put(member);
        }
        JSONObject content = new JSONObject();
        try {
            content.put(MEMBERS, membersJson);
            content.put(TYPE, mType.getValue());
            content.put(NAME, mName);
            if (mOperator != null) {
                JSONObject operatorJson = jsonFromUserInfo(mOperator);
                content.put(OPERATOR, operatorJson);
            }
        } catch (JSONException e) {
            Log.e("GroupNotifyMessage", "encode JSONException " + e.getMessage());
        }
        return content.toString().getBytes(StandardCharsets.UTF_8);
    }

    @Override
    public void decode(byte[] data) {
        if (data == null) {
            Log.e("GroupNotifyMessage", "decode data is null");
            return;
        }
        String jsonStr = new String(data, StandardCharsets.UTF_8);

        try {
            JSONObject jsonObject = new JSONObject(jsonStr);
            mType = GroupNotifyType.from(jsonObject.optInt(TYPE));
            JSONArray membersJson = jsonObject.optJSONArray(MEMBERS);
            if (membersJson != null) {
                List<UserInfo> members = new ArrayList<>();
                for (int i = 0; i < membersJson.length(); i++) {
                    UserInfo member = userInfoFromJson(membersJson.getJSONObject(i));
                    members.add(member);
                }
                mMembers = members;
            }
            JSONObject operatorJson = jsonObject.optJSONObject(OPERATOR);
            if (operatorJson != null) {
                mOperator = userInfoFromJson(operatorJson);
            }
            mName = jsonObject.optString(NAME);
        } catch (JSONException e) {
            Log.e("GroupNotifyMessage", "decode JSONException " + e.getMessage());
        }
    }

    @Override
    public String conversationDigest() {
        // TIPS: 摘要必须调用时取串，静态常量会锁死在类加载时的语言
        return AppRes.string(R.string.msg_group_notify);
    }

    public String description() {
        boolean isSender = !TextUtils.isEmpty(mOperator.getUserId())
                && mOperator.getUserId().equals(JIM.getInstance().getCurrentUserId());
        String sender = isSender ? AppRes.string(R.string.group_notify_you) : mOperator.getUserName();
        StringBuilder userList = new StringBuilder();
        for (UserInfo member : mMembers) {
            userList.append(member.getUserName()).append(", ");
        }
        String newOwner = "";
        boolean isOwner = false;
        if (mType == GroupNotifyType.CHANGE_OWNER) {
            if (!mMembers.isEmpty()) {
                UserInfo member = mMembers.get(0);
                if (member.getUserId().equals(JIM.getInstance().getCurrentUserId())) {
                    isOwner = true;
                }
                newOwner = isOwner ? AppRes.string(R.string.group_notify_you) : member.getUserName();
            }
        }

        int l = userList.length();
        if (l > 2) {
            userList.delete(l-2, l-1);
        }
        String ul = userList.toString();
        switch (mType) {
            case ADD_MEMBER:
                return AppRes.string(R.string.group_notify_add_member, sender, ul);
            case REMOVE_MEMBER:
                return AppRes.string(R.string.group_notify_remove_member, sender, ul);
            case RENAME:
                return AppRes.string(R.string.group_notify_rename, sender, mName);
            case CHANGE_OWNER:
                return AppRes.string(R.string.group_notify_change_owner, newOwner);
            case JOIN:
                return AppRes.string(R.string.group_notify_join, sender);
            default:
                return "";
        }
    }

    public enum GroupNotifyType {
        OTHER(0),
        ADD_MEMBER(1),
        REMOVE_MEMBER(2),
        RENAME(3),
        CHANGE_OWNER(4),
        JOIN(5);

        final int mValue;

        GroupNotifyType(int value) {
            mValue = value;
        }

        public int getValue() {
            return mValue;
        }

        public static GroupNotifyType from(int value) {
            for (GroupNotifyType type : values()) {
                if (type.mValue == value) {
                    return type;
                }
            }
            return OTHER;
        }
    }

    public GroupNotifyType getType() {
        return mType;
    }

    public void setType(GroupNotifyType type) {
        mType = type;
    }

    public List<UserInfo> getMembers() {
        return mMembers;
    }

    public void setMembers(List<UserInfo> members) {
        mMembers = members;
    }

    public UserInfo getOperator() {
        return mOperator;
    }

    public void setOperator(UserInfo operator) {
        mOperator = operator;
    }

    public String getName() {
        return mName;
    }

    public void setName(String name) {
        this.mName = name;
    }

    private UserInfo userInfoFromJson(JSONObject jsonObject) {
        UserInfo userInfo = new UserInfo();
        userInfo.setUserId(jsonObject.optString(USER_ID));
        userInfo.setUserName(jsonObject.optString(NICKNAME));
        userInfo.setPortrait(jsonObject.optString(AVATAR));
        return userInfo;
    }

    private JSONObject jsonFromUserInfo(UserInfo userInfo) {
        JSONObject result = new JSONObject();
        try {
            result.put(USER_ID, userInfo.getUserId());
            if (!TextUtils.isEmpty(userInfo.getUserName())) {
                result.put(NICKNAME, userInfo.getUserName());
            }
            if (!TextUtils.isEmpty(userInfo.getPortrait())) {
                result.put(AVATAR, userInfo.getPortrait());
            }
        } catch (JSONException e) {
            Log.e("GroupNotifyMessage", "jsonFromUserInfo JSONException " + e.getMessage());
        }
        return result;
    }

    private GroupNotifyType mType;
    private List<UserInfo> mMembers = new ArrayList<>();
    private UserInfo mOperator;
    private String mName;

    private static final String MEMBERS = "members";
    private static final String USER_ID = "user_id";
    private static final String AVATAR = "avatar";
    private static final String NICKNAME = "nickname";
    private static final String TYPE = "type";
    private static final String OPERATOR = "operator";
    private static final String NAME = "name";
}

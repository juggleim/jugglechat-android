package com.jet.im.internal.core.db;

import android.database.Cursor;

import com.jet.im.model.GroupInfo;
import com.jet.im.model.UserInfo;

public class UserInfoSql {
    static UserInfo userInfoWithCursor(Cursor cursor) {
        UserInfo info = new UserInfo();
        info.setUserId(CursorHelper.readString(cursor, COL_USER_ID));
        info.setUserName(CursorHelper.readString(cursor, COL_NAME));
        info.setPortrait(CursorHelper.readString(cursor, COL_PORTRAIT));
        return info;
    }

    static GroupInfo groupInfoWithCursor(Cursor cursor) {
        GroupInfo info = new GroupInfo();
        info.setGroupId(CursorHelper.readString(cursor, COL_GROUP_ID));
        info.setGroupName(CursorHelper.readString(cursor, COL_NAME));
        info.setPortrait(CursorHelper.readString(cursor, COL_PORTRAIT));
        return info;
    }

    static final String SQL_CREATE_USER_TABLE = "CREATE TABLE IF NOT EXISTS user ("
                                                + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                                                + "user_id VARCHAR (64),"
                                                + "name VARCHAR (64),"
                                                + "portrait TEXT,"
                                                + "extension TEXT"
                                                + ")";
    static final String SQL_CREATE_GROUP_TABLE = "CREATE TABLE IF NOT EXISTS group_info ("
                                                + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                                                + "group_id VARCHAR (64),"
                                                + "name VARCHAR (64),"
                                                + "portrait TEXT,"
                                                + "extension TEXT"
                                                + ")";
    static final String SQL_CREATE_USER_INDEX = "CREATE UNIQUE INDEX IF NOT EXISTS idx_user ON user(user_id)";
    static final String SQL_CREATE_GROUP_INDEX = "CREATE UNIQUE INDEX IF NOT EXISTS idx_group ON group_info(group_id)";
    static final String SQL_GET_USER_INFO = "SELECT * FROM user WHERE user_id = ?";
    static final String SQL_GET_GROUP_INFO = "SELECT * FROM group_info WHERE group_id = ?";
    static final String SQL_INSERT_USER_INFO = "INSERT OR REPLACE INTO user (user_id, name, portrait, extension) VALUES (?, ?, ?, ?)";
    static final String SQL_INSERT_GROUP_INFO = "INSERT OR REPLACE INTO group_info (group_id, name, portrait, extension) VALUES (?, ?, ?, ?)";
    static final String COL_USER_ID = "user_id";
    static final String COL_GROUP_ID = "group_id";
    static final String COL_NAME = "name";
    static final String COL_PORTRAIT = "portrait";
    static final String COL_EXTENSION = "extension";
}
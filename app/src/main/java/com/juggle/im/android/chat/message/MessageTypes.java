package com.juggle.im.android.chat.message;

/**
 * 消息类型常量定义
 * 与 Dart 项目 snailchat/lib/common/enum.dart 中的 CustomMsgName 对应
 */
public class MessageTypes {
    // 群组通知消息
    public static final String GROUP_NTF = "jgd:grpntf";
    // 用户屏蔽通知消息
    public static final String USER_BLOCK_NTF = "jgd:ublockntf";
    // 表情贴纸消息
    public static final String STICKER_EMOJI = "snl:sticker";
    // 骰子/猜拳游戏消息
    public static final String STICKER_GAME = "jgd:sticker_game";
    // 好友通知消息
    public static final String FRIEND_NTF = "jgd:friendntf";
    // 好友申请消息
    public static final String FRIEND_APPLY = "jgd:friendapply";
    // 名片消息
    public static final String CONTACT_CARD = "jgd:contactcard";
    // 阅后即焚时效消息
    public static final String LIFE_MSG_TIME = "jgd:lifetime_msg";
    // 时间线通知消息
    public static final String TIMELINE = "jgd:timeline";
    // 输入状态消息
    public static final String TYPING_NTF = "snl:typing";
    // 朋友圈通知消息
    public static final String MOMENT_NTF = "jgd:postnotify";
    // 数据同步通知消息
    public static final String SYNC_DATA_NTF = "snl:syncdntf";
}

/**
 * 好友申请类型
 */
class FriendApplyType {
    static final int NONE = -1;
    static final int ADD = 0;
    static final int CONFIRM = 1;
}

/**
 * 数据同步类型
 */
class SyncDataType {
    static final int NONE = 0;
    static final int FRIEND_ALIAS = 1;
}

/**
 * 输入状态类型
 */
class TypingStatus {
    static final int END = 0;
    static final int START = 1;
}

/**
 * 游戏贴纸类型
 */
class StickerGameType {
    static final String DICE = "dice";
    static final String MORA = "mora";
}

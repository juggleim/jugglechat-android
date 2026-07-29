package com.juggle.im.android.chat.call;

import com.juggle.im.call.ICallSession;
import com.juggle.im.call.model.CallMember;

import java.util.ArrayList;
import java.util.List;

/**
 * 远端成员离开后的通话结束策略。
 *
 * <p>用于兼容“界面是一对一、底层是多人通话会话”的跨端场景。</p>
 */
final class RemoteCallTerminationPolicy {
    private RemoteCallTerminationPolicy() {
    }

    /**
     * 判断远端成员离开后是否应结束当前单聊通话。
     *
     * @param currentUserId 当前用户ID
     * @param leavingUserIds 本次离开的用户ID列表
     * @param remainingMemberUserIds SDK会话中剩余的成员ID列表
     * @return 本次有远端用户离开且会话中已无远端成员时返回 {@code true}
     */
    static boolean shouldFinishAfterRemoteLeave(String currentUserId,
                                                List<String> leavingUserIds,
                                                List<String> remainingMemberUserIds) {
        if (!containsRemoteUser(currentUserId, leavingUserIds)) {
            return false;
        }
        return !containsRemoteUser(currentUserId, remainingMemberUserIds);
    }

    /**
     * 根据 SDK 当前会话成员快照判断是否应结束单聊通话。
     *
     * @param currentUserId 当前用户ID
     * @param leavingUserIds 本次离开的用户ID列表
     * @param callSession 当前通话会话
     * @return 会话中已无远端成员时返回 {@code true}
     */
    static boolean shouldFinishAfterRemoteLeave(String currentUserId,
                                                List<String> leavingUserIds,
                                                ICallSession callSession) {
        if (callSession == null) {
            return false;
        }
        List<String> remainingMemberUserIds = new ArrayList<>();
        List<CallMember> members = callSession.getMembers();
        if (members != null) {
            for (CallMember member : members) {
                if (member != null && member.getUserInfo() != null) {
                    remainingMemberUserIds.add(member.getUserInfo().getUserId());
                }
            }
        }
        return shouldFinishAfterRemoteLeave(
                currentUserId,
                leavingUserIds,
                remainingMemberUserIds);
    }

    private static boolean containsRemoteUser(String currentUserId, List<String> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return false;
        }
        for (String userId : userIds) {
            if (userId != null && !userId.isEmpty() && !userId.equals(currentUserId)) {
                return true;
            }
        }
        return false;
    }
}

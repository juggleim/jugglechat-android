package com.juggle.im.internal.core.network;

import com.juggle.im.internal.model.ConcreteConversationInfo;
import com.juggle.im.internal.model.ConcreteMessage;
import com.juggle.im.internal.model.upload.UploadOssType;
import com.juggle.im.internal.model.upload.UploadPreSignCred;
import com.juggle.im.internal.model.upload.UploadQiNiuCred;
import com.juggle.im.model.TimePeriod;
import com.juggle.im.model.UserInfo;

import java.util.List;

import app_messages.Connect;

class PBRcvObj {

    static class ConnectAck {
        int code;
        String userId;
        String session;
        String extra;
    }

    static class PublishMsgAck {
        int index;
        int code;
        String msgId;
        long timestamp;
        long seqNo;
    }

    static class PublishMsgBody {
        ConcreteMessage rcvMessage;
        int index;
        int qos;
    }

    static class QryAck {
        QryAck(Connect.QueryAckMsgBody body) {
            this.index = body.getIndex();
            this.code = body.getCode();
            this.timestamp = body.getTimestamp();
        }

        int index;
        int code;
        long timestamp;
    }

    static class QryHisMsgAck extends QryAck {
        boolean isFinished;
        List<ConcreteMessage> msgList;

        QryHisMsgAck(Connect.QueryAckMsgBody body) {
            super(body);
        }
    }

    static class SyncConvAck extends QryAck {
        boolean isFinished;
        List<ConcreteConversationInfo> convList;
        List<ConcreteConversationInfo> deletedConvList;

        SyncConvAck(Connect.QueryAckMsgBody body) {
            super(body);
        }
    }

    static class QryReadDetailAck extends QryAck {
        List<UserInfo> readMembers;
        List<UserInfo> unreadMembers;

        QryReadDetailAck(Connect.QueryAckMsgBody body) {
            super(body);
        }
    }

    static class SimpleQryAck extends QryAck {
        SimpleQryAck(Connect.QueryAckMsgBody body) {
            super(body);
        }
    }

    static class TimestampQryAck extends QryAck {
        long operationTime;

        TimestampQryAck(Connect.QueryAckMsgBody body) {
            super(body);
        }
    }

    static class QryFileCredAck extends QryAck {
        UploadOssType ossType;
        UploadQiNiuCred qiNiuCred;
        UploadPreSignCred preSignCred;

        QryFileCredAck(Connect.QueryAckMsgBody body) {
            super(body);
        }
    }

    static class ConversationInfoAck extends QryAck {
        ConcreteConversationInfo conversationInfo;

        ConversationInfoAck(Connect.QueryAckMsgBody body) {
            super(body);
        }
    }

    static class GlobalMuteAck extends QryAck {
        boolean isMute;
        String timezone;
        List<TimePeriod> periods;

        GlobalMuteAck(Connect.QueryAckMsgBody body) {
            super(body);
        }
    }

    static class PublishMsgNtf {
        long syncTime;
    }

    static class DisconnectMsg {
        int code;
        long timestamp;
        String extra;
    }

    static class PBRcvType {
        static final int parseError = 0;
        static final int cmdMatchError = 1;
        static final int connectAck = 2;
        static final int publishMsgAck = 3;
        static final int qryHisMessagesAck = 4;
        static final int syncConversationsAck = 5;
        static final int syncMessagesAck = 6;
        static final int publishMsg = 7;
        static final int publishMsgNtf = 8;
        static final int pong = 9;
        static final int disconnectMsg = 10;
        static final int qryReadDetailAck = 11;
        static final int simpleQryAck = 12;
        static final int simpleQryAckCallbackTimestamp = 13;
        static final int conversationSetTopAck = 14;
        static final int qryFileCredAck = 15;
        static final int addConversationAck = 16;
        static final int globalMuteAck = 17;
    }

    public int getRcvType() {
        return mRcvType;
    }

    public void setRcvType(int rcvType) {
        mRcvType = rcvType;
    }

    ConnectAck mConnectAck;
    PublishMsgAck mPublishMsgAck;
    QryHisMsgAck mQryHisMsgAck;
    SyncConvAck mSyncConvAck;
    PublishMsgBody mPublishMsgBody;
    PublishMsgNtf mPublishMsgNtf;
    DisconnectMsg mDisconnectMsg;
    QryReadDetailAck mQryReadDetailAck;
    SimpleQryAck mSimpleQryAck;
    TimestampQryAck mTimestampQryAck;
    QryFileCredAck mQryFileCredAck;
    ConversationInfoAck mConversationInfoAck;
    GlobalMuteAck mGlobalMuteAck;
    private int mRcvType;
}


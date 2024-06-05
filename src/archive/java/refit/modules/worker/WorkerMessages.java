package refit.modules.worker;

import reptor.chronos.PushMessageSink;
import reptor.chronos.message.MulticastChannel;
import reptor.distrbt.certify.GroupConnectionCertifier;
import reptor.distrbt.com.Message;
import reptor.distrbt.com.MessageMapper;
import reptor.distrbt.com.NetworkMessage;
import reptor.replct.MessageCategoryID;
import reptor.replct.ProtocolID;
import reptor.replct.connect.ConnectionCertifierCollection;
import reptor.replct.invoke.InvocationExtensions.FinishedReplyObserver;
import reptor.replct.invoke.InvocationMessages.Reply;


public class WorkerMessages
{

    private static final int WORKER_BASE              = ProtocolID.COMMON | MessageCategoryID.WORKER;
    public  static final int REPLICA_CERTIFICATION_ID = WORKER_BASE + 1;
    public  static final int CLIENT_CERTIFICATION_ID  = WORKER_BASE + 2;
    public  static final int SEND_REPLY_ID            = WORKER_BASE + 3;


    public interface ReplicaCertificationJob extends Message
    {
        void execute(MessageMapper mapper, GroupConnectionCertifier repcon);

        @Override
        default int getTypeID()
        {
            return REPLICA_CERTIFICATION_ID;
        }
    }


    public interface ClientCertificationJob extends Message
    {
        void execute(MessageMapper mapper, ConnectionCertifierCollection clicons);

        @Override
        default int getTypeID()
        {
            return CLIENT_CERTIFICATION_ID;
        }
    }




    public static class VerifyReplicaMessage implements ReplicaCertificationJob
    {

        private final NetworkMessage                          m_msg;
        private final PushMessageSink<? super NetworkMessage> m_sender;


        public VerifyReplicaMessage(NetworkMessage msg, PushMessageSink<? super NetworkMessage> sender)
        {
            m_msg    = msg;
            m_sender = sender;
        }


        @Override
        public String toString()
        {
            return "{VERIFY_REPLICA_MESSAGE}";
        }


        @Override
        public void execute(MessageMapper mapper, GroupConnectionCertifier repcon)
        {
            mapper.verifyMessage( m_msg, repcon );
            m_sender.enqueueMessage( m_msg );
        }

    }


    public static class VerifyClientMessage implements ClientCertificationJob
    {

        private final NetworkMessage                          m_msg;
        private final PushMessageSink<? super NetworkMessage> m_sender;


        public VerifyClientMessage(NetworkMessage msg, PushMessageSink<? super NetworkMessage> sender)
        {
            m_msg    = msg;
            m_sender = sender;
        }


        @Override
        public String toString()
        {
            return "{VERIFY_CLIENT_MESSAGE}";
        }


        @Override
        public void execute(MessageMapper mapper, ConnectionCertifierCollection clicons)
        {
            mapper.verifyMessage( m_msg, clicons );
            m_sender.enqueueMessage( m_msg );
        }

    }


    public static class TransmitToReplicas implements ReplicaCertificationJob
    {

        private final NetworkMessage     m_msg;
        private final MulticastChannel<? super NetworkMessage> m_trans;


        public TransmitToReplicas(NetworkMessage msg, MulticastChannel<? super NetworkMessage> trans)
        {
            m_msg   = msg;
            m_trans = trans;
        }


        @Override
        public String toString()
        {
            return "{REPLICA_CERTIFICATION}";
        }


        @Override
        public void execute(MessageMapper mapper, GroupConnectionCertifier repcon)
        {
            mapper.certifyAndSerializeMessage( m_msg, repcon.getCertifier() );
            m_trans.enqueueMessage( m_msg );
        }
    }


    public static class SendReply implements Message
    {

        private final Reply          m_reply;
        private final PushMessageSink<? super Reply> m_channel;


        public SendReply(Reply reply, PushMessageSink<? super Reply> channel)
        {
            m_reply   = reply;
            m_channel = channel;
        }


        @Override
        public int getTypeID()
        {
            return SEND_REPLY_ID;
        }


        @Override
        public String toString()
        {
            return "{SEND_REPLY}";
        }


        public void execute(MessageMapper mapper, ConnectionCertifierCollection clicons,
                            FinishedReplyObserver finrepobserver)
        {
            mapper.certifyAndSerializeMessage( m_reply, clicons.getCertifier( m_reply.getRequester() ).getCertifier() );
            m_channel.enqueueMessage( m_reply );

            finrepobserver.replyFinished( m_reply );
        }
    }

}

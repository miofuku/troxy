package reptor.replct.invoke.bft;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import reptor.chronos.com.PushMessageSink;
import reptor.chronos.link.MulticastLink;
import reptor.distrbt.com.Message;
import reptor.distrbt.com.MessageMapper;
import reptor.distrbt.com.NetworkMessage;
import reptor.distrbt.common.data.ImmutableData;
import reptor.replct.MessageHandler;
import reptor.replct.common.quorums.MessageCollectors;
import reptor.replct.invoke.InvocationExtensions;
import reptor.replct.invoke.InvocationExtensions.FinishedReplyObserver;
import reptor.replct.invoke.InvocationExtensions.ReceivedRequestObserver;
import reptor.replct.invoke.InvocationMessages;
import reptor.replct.invoke.InvocationMessages.Reply;
import reptor.replct.invoke.InvocationMessages.Request;
import reptor.replct.invoke.InvocationMessages.RequestExecuted;

import java.util.Random;


public abstract class BFTReplicaInstance implements MessageHandler<Message>
{

    //-------------------------------------//
    //               Types                 //
    //-------------------------------------//

    public enum State
    {
        INITIALIZED,
        REQUESTED,
        EXECUTED_SPECULATIVELY,
        EXECUTED,
        FINISHED;
    }


    //-------------------------------------//
    //         Members and Subjects        //
    //-------------------------------------//

    private static final Logger s_logger = LoggerFactory.getLogger( BFTReplicaInstance.class );

    protected final short                                       m_clino;
    protected final int                                         m_slotno;
    protected final PushMessageSink<NetworkMessage>             m_cliconn;

    protected final byte                                        m_repno;
    protected final MessageMapper                               m_mapper;
    protected final MulticastLink<? super NetworkMessage>       m_replicas;
    protected final ReceivedRequestObserver                     m_reqobserver;
    protected final FinishedReplyObserver                       m_repobserver;

    protected byte                      m_contact      = -1;
    protected State                     m_state        = null;
    protected long                      m_invno        = -1;
    protected Request                   m_request      = null;
    private   boolean                   m_reqready     = false;
    protected ImmutableData             m_result       = null;
    protected Object                    m_extcntxt     = null;
    protected Reply                     m_reply        = null;

    private Random                      random         = new Random();


    public BFTReplicaInstance(short shardno, short clino, int slotno,
                              PushMessageSink<NetworkMessage> cliconn, BFTReplicaProvider invprov)
    {
        m_clino     = clino;
        m_slotno    = slotno;
        m_cliconn   = cliconn;
        m_repno     = invprov.getInvocationReplica().getReplicaGroup().getReplicaNumber();
        m_mapper    = invprov.getMessageMapper();
        m_replicas  = invprov.getReplicaConnection();

        InvocationExtensions invexts = invprov.getInvocationReplica().getInvocationExtensions();
        m_reqobserver = invexts.getReceivedRequestObserver( clino );
        m_repobserver = invexts.getFinishedReplyObserver( shardno );
    }


    //-------------------------------------//
    //           External State            //
    //-------------------------------------//


    @Override
    public String toString()
    {
        return String.format( "INVRI[%03d-%03d][%1$d:%d]", m_clino, m_slotno, m_invno );
    }


    public int getSlotNumber()
    {
        return m_slotno;
    }


    public long getInvocationNumber()
    {
        return m_invno;
    }


    public State getState()
    {
        return m_state;
    }


    public Request getRequest()
    {
        return m_request;
    }


    public ImmutableData getResult()
    {
        return m_result;
    }


    public Reply getReply()
    {
        return m_reply;
    }


    //-------------------------------------//
    //       Configuration Interface       //
    //-------------------------------------//

    public void initContact(byte contact)
    {
        m_contact = contact;
    }


    public void initInvocation(long invno)
    {
        m_invno     = invno;
        m_request   = null;
        m_result    = null;
        m_extcntxt  = null;
        m_reply     = null;

        advanceState( State.INITIALIZED );
    }


    //-------------------------------------//
    //          Handler Interface          //
    //-------------------------------------//

    public boolean isRequestRequired(Request request)
    {
        return m_request==null ||
                !m_request.isPanic() && ( request.isPanic() ||
                        m_request.useReadOnlyOptimization() && !request.useReadOnlyOptimization() );
    }


    @Override
    public boolean handleMessage(Message msg)
    {
        switch( msg.getTypeID() )
        {
        case InvocationMessages.REQUEST_ID:
            return handleRequest( (Request) msg );
        case InvocationMessages.REQUEST_EXECUTED_ID:
            handleCommandExecuted( (RequestExecuted) msg );
            return false;
        case InvocationMessages.REPLY_ID:
            handleReply( (Reply) msg );
            return false;
        default:
            throw new UnsupportedOperationException( msg.toString() );
        }
    }


    public abstract boolean handleRequest(Request request);


    protected void checkRequest(Request request)
    {
        assert request!=null;
        assert request.getSender()==m_clino && request.getNumber()==m_invno : this + " vs. " + request ;
        assert request.isCertificateValid();
    }


    protected void storeRequest(Request request)
    {
        m_request  = request;
        m_reqready = true;
    }


    public Request retrievePendingRequest()
    {
        if( !m_reqready )
            return null;
        else
        {
            m_reqready = false;
            return m_request;
        }
    }


    public abstract void handleCommandExecuted(RequestExecuted reqexecd);


    protected void storeResult(RequestExecuted reqexecd)
    {
        if( isRequestRequired( reqexecd.getRequest() ) )
        {
            checkRequest( reqexecd.getRequest() );

            m_request = reqexecd.getRequest();
        }

        m_result   = reqexecd.getResult();
        m_extcntxt = reqexecd.getExtensionContext();
    }


    protected Reply createReply(boolean isfull, boolean isspec, ImmutableData result)
    {
        Reply reply = new Reply( m_repno, m_clino, m_invno, isfull, isspec, result, m_contact, m_extcntxt );
        reply.setValid();

        return reply;
    }


    protected void storeReply(Reply reply)
    {
        m_reply = reply;
    }


    public abstract void handleReply(Reply reply);


    //-------------------------------------//
    //        Additional Internals         //
    //-------------------------------------//

    protected boolean isAlreadyKnown(Request msg)
    {
        return MessageCollectors.isMessageAlreadyKnown( msg, m_request );
    }


    // TODO: What about speculative execution?
    protected void invocationStarted(Request request)
    {
        m_reqobserver.requestReceived( m_clino, request );
    }


    protected void invocationFinished(Reply reply)
    {
        m_repobserver.replyFinished( reply );
    }


    protected void forwardReplyToContact(Reply reply)
    {
        s_logger.debug( "{} forward reply {}", this, reply );

        m_replicas.enqueueUnicast( m_contact, reply );
    }


    public void forwardForFastRead(Request request)
    {
        s_logger.debug( "{} forward read-only request {}", this, request );

        short rep = 1;
        if (m_repno==1)
            rep = 2;

        m_replicas.enqueueUnicast(rep, request);
    }


    public void forwardToOthers(Request request)
    {
        s_logger.debug( "{} forward read-only request {}", this, request );

        for (short i=0; i<m_replicas.size(); i++) {
            if (i == m_repno)
                continue;
            m_replicas.enqueueUnicast(i, request);
        }
    }


    protected void sendReplyToClient(Reply reply)
    {
        s_logger.debug( "{} send reply {}", this, reply );

        m_cliconn.enqueueMessage( reply );
    }


    protected void advanceState(State state)
    {
        m_state = state;
    }

}

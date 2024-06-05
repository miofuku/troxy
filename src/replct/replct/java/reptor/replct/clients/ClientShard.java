package reptor.replct.clients;

import java.util.ArrayDeque;
import java.util.Queue;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import reptor.chronos.ChronosAddress;
import reptor.chronos.ChronosTask;
import reptor.chronos.com.DomainEndpoint;
import reptor.chronos.com.MessageQueue;
import reptor.chronos.com.PushMessageSink;
import reptor.chronos.context.SchedulerContext;
import reptor.chronos.link.MulticastLink;
import reptor.chronos.orphics.AbstractMaster;
import reptor.chronos.orphics.Actor;
import reptor.chronos.orphics.MessageQueueHandler;
import reptor.chronos.portals.QueuePortal;
import reptor.distrbt.com.Message;
import reptor.distrbt.com.MessageMapper;
import reptor.distrbt.com.NetworkMessage;
import reptor.distrbt.domains.SelectorDomainContext;
import reptor.jlib.NotImplementedException;
import reptor.replct.agree.AgreementPeers;
import reptor.replct.agree.View;
import reptor.replct.agree.ViewLogging;
import reptor.replct.agree.view.StableView;
import reptor.replct.agree.view.ViewChangeMessages;
import reptor.replct.connect.CommunicationMessages;
import reptor.replct.connect.CommunicationMessages.NewConnection;
import reptor.replct.invoke.ClientToWorkerAssignment;
import reptor.replct.invoke.InvocationMessages;
import reptor.replct.invoke.InvocationMessages.Reply;
import reptor.replct.invoke.InvocationMessages.Request;
import reptor.replct.invoke.InvocationMessages.RequestExecuted;
import reptor.replct.invoke.InvocationReplicaHandler;
import reptor.replct.invoke.InvocationReplicaProvider;


// TODO: What about applied checkpoints (and asynchronous requests)?
// TODO: Fairness strategy for clients.
public class ClientShard extends AbstractMaster<SelectorDomainContext>
                         implements Actor, PushMessageSink<Message>,
                                    MessageQueueHandler<MessageQueue<Message>>,
                                    DomainEndpoint<PushMessageSink<Message>>
{

    //-------------------------------------//
    //         Members and Subjects        //
    //-------------------------------------//

    private static final Logger s_logger = LoggerFactory.getLogger( ClientShard.class );

    private final SchedulerContext<? extends SelectorDomainContext> m_master;

    private final short                             m_shardno;
    private final InvocationReplicaHandler[]        m_clients;

    private final Queue<InvocationReplicaHandler>   m_readytasks = new ArrayDeque<>();
    private final QueuePortal<Message>              m_portal;
    private final ClientHandlingProcess             m_cliproc;
    private final ClientToWorkerAssignment          m_clitowrk;

    private PushMessageSink<Message>                m_readwritechannel;
    private PushMessageSink<Message>                m_readonlychannel;

    private StableView                      m_enqview;


    public ClientShard(SchedulerContext<? extends SelectorDomainContext> master, short shardno,
                       MessageMapper mapper, MulticastLink<? super NetworkMessage> repconn, ClientHandlingProcess cliproc)
    {
        m_master    = master;
        m_shardno   = shardno;
        m_cliproc   = cliproc;
        m_clitowrk  = cliproc.getInvocationReplica().getInvocation().getClientToWorkerAssignment();

        m_portal = new QueuePortal<>( this );

        InvocationReplicaProvider invprov =
                cliproc.getInvocationReplica().createInvocationProvider( shardno, mapper, repconn );

        m_clients = new InvocationReplicaHandler[ m_clitowrk.getNumberOfWorkersForShard( shardno ) ];

        for( int wrkno=0; wrkno<m_clients.length; wrkno++ )
            m_clients[ wrkno ] = invprov.createHandler( this, shardno, wrkno );
    }


    @Override
    protected SchedulerContext<? extends SelectorDomainContext> master()
    {
        return m_master;
    }


    //-------------------------------------//
    //           External State            //
    //-------------------------------------//

    @Override
    public String toString()
    {
        return String.format( "CLI%02d", m_shardno );
    }


    public short getNumber()
    {
        return m_shardno;
    }


    @Override
    public SelectorDomainContext getDomainContext()
    {
        return m_master.getDomainContext();
    }


    public InvocationReplicaHandler[] getClients()
    {
        return m_clients;
    }


    //-------------------------------------//
    //       Configuration Interface       //
    //-------------------------------------//

    public void initPeers(AgreementPeers peers)
    {
        ChronosAddress domaddr = getDomainContext().getDomainAddress();
        int[]          orders  = m_cliproc.getClientHandling().getOrderShardsForClientShard( m_shardno );

        m_readwritechannel = peers.createRoundRobinChannel( peers.getOrderShards(), orders, domaddr );

        if( peers.getExecutors().size()>1 )
            throw new NotImplementedException();

        m_readonlychannel = peers.getExecutors().get( 0 ).createChannel( domaddr );
    }


    public void initContact(byte coord)
    {
        for( InvocationReplicaHandler client : m_clients )
            client.initContact( m_clitowrk.getContactForClient( client.getClientNumber(), coord ) );
    }


    //-------------------------------------//
    //            Actor Interface          //
    //-------------------------------------//

    @Override
    public PushMessageSink<Message> createChannel(ChronosAddress origin)
    {
        return origin.equals( domain().getDomainAddress() ) ? this : m_portal.createChannel( origin );
    }


    @Override
    public void enqueueMessage(Message msg)
    {
        switch( msg.getTypeID() )
        {
        case ViewChangeMessages.NEW_VIEW_STABLE_ID:
            enqueueStableView( (StableView) msg );
            break;
        case CommunicationMessages.NEW_CONNECTION_ID:
            enqueueNewConnection( (NewConnection) msg );
            break;
        case InvocationMessages.REQUEST_ID:
            enqueueRequest( (Request) msg );
            break;
        case InvocationMessages.REQUEST_EXECUTED_ID:
            enqueueRequestExecuted( (RequestExecuted) msg );
            break;
        case InvocationMessages.REPLY_ID:
            enqueueReply( (Reply) msg );
            break;
        default:
            throw new UnsupportedOperationException( msg.toString() );
        }
    }


    private void enqueueStableView(StableView sv)
    {
        assert m_enqview==null || sv.getViewNumber()>m_enqview.getViewNumber();

        m_enqview = sv;

        notifyReady();
    }


    private void enqueueNewConnection(NewConnection newconn)
    {
        client( newconn.getRemoteEndpoint().getProcessNumber() ).enqueueNewConnection( newconn );
    }


    private void enqueueRequest(Request request)
    {
        client( request.getSender() ).enqueueRequest( request );
    }


    private void enqueueRequestExecuted(RequestExecuted reqexecd)
    {
        client( reqexecd.getRequest().getSender() ).enqueueRequestExecuted( reqexecd );
    }


    private void enqueueReply(Reply reply)
    {
        client( reply.getRequester() ).enqueueReply( reply );
    }


    private InvocationReplicaHandler client(short clino)
    {
        return m_clients[ m_clitowrk.getWorkerForClient( m_shardno, clino ) ];
    }


    @Override
    public void taskReady(ChronosTask task)
    {
        // TODO: Typed interface?
        m_readytasks.add( (InvocationReplicaHandler) task );

        notifyReady();
    }


    @Override
    public void messagesReady(MessageQueue<Message> queue)
    {
        notifyReady();
    }


    @Override
    public boolean execute()
    {
        if( m_enqview!=null )
        {
            handleNewViewStable( m_enqview );

            m_enqview = null;
        }

        if( m_portal.isReady() )
        {
            m_portal.retrieveMessages();

            Message msg;
            while( ( msg = m_portal.poll() )!=null )
                enqueueMessage( msg );
        }

        InvocationReplicaHandler client;

        while( ( client = m_readytasks.poll() )!=null )
        {
            client.execute();

            Request request;

            while( ( request = client.pollPendingRequests() )!=null )
                ( request.useReadOnlyOptimization() ? m_readonlychannel : m_readwritechannel ).enqueueMessage( request );
        }

        return isDone( true );
    }


    public void handleNewViewStable(StableView sv)
    {
        s_logger.debug( ViewLogging.MARKER, "switch to view {}", sv.getViewNumber() );

        View  view    = sv.getView();
        byte contact = m_cliproc.getClientHandling().getOrdering().getContact( view.getNumber(), view.getReplicaNumber() );

        initContact( contact );
    }

}

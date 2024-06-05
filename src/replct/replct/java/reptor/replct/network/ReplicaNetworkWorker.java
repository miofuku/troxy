package reptor.replct.network;

import java.util.List;

import reptor.chronos.ChronosTask;
import reptor.chronos.com.PushMessageSink;
import reptor.chronos.context.SchedulerContext;
import reptor.chronos.link.MulticastLink;
import reptor.distrbt.com.Message;
import reptor.distrbt.com.NetworkMessage;
import reptor.distrbt.com.connect.NetworkConnectionProvider;
import reptor.distrbt.com.connect.NetworkGroupConnection;
import reptor.distrbt.domains.SelectorDomainContext;
import reptor.replct.MessageCategoryID;
import reptor.replct.MessageHandler;
import reptor.replct.MessageTypeID;
import reptor.replct.ReplicaPeerGroup;
import reptor.replct.agree.checkpoint.CheckpointNetworkMessage;
import reptor.replct.agree.order.OrderNetworkMessage;
import reptor.replct.agree.view.ViewChangeNetworkMessage;
import reptor.replct.common.modules.PublicMasterActor;
import reptor.replct.connect.CommunicationMessages;
import reptor.replct.connect.CommunicationMessages.NewConnection;
import reptor.replct.invoke.InvocationMessages;
import reptor.replct.invoke.InvocationMessages.Reply;


public class ReplicaNetworkWorker extends PublicMasterActor implements MessageHandler<Message>
{

    private final short                  m_netno;
    private final NetworkGroupConnection m_conn;

    private ReplicaPeers                            m_peers;
    private List<PushMessageSink<? super Message>>  m_orders;
    private List<PushMessageSink<? super Message>>  m_views;
    private List<PushMessageSink<? super Message>>  m_chkpts;
    private List<PushMessageSink<? super Message>>  m_clints;


    public ReplicaNetworkWorker(SchedulerContext<? extends SelectorDomainContext> master, short netno,
                                ReplicaPeerGroup repgroup, NetworkConnectionProvider connprov)
    {
        super( master, null );

        m_netno = netno;

        m_conn = new NetworkGroupConnection( this, netno, repgroup.size(), connprov );
        m_conn.initGroup( repno -> repno==repgroup.getReplicaNumber() ? null : netno*100+repno, null );
    }


    @Override
    public String toString()
    {
        return String.format( "RNT%02d", m_netno );
    }


    public MulticastLink<NetworkMessage> getReplicaGroupChannel()
    {
        return m_conn;
    }


    @Override
    protected void processMessage(Message msg)
    {
        handleMessage( msg );
    }


    @Override
    public boolean handleMessage(Message msg)
    {
        switch( msg.getTypeID() )
        {
        case CommunicationMessages.NEW_CONNECTION_ID:
            handleNewConnection( (NewConnection) msg );
            break;
        default:
            throw new UnsupportedOperationException( msg.toString() );
        }

        return false;
    }


    public void initPeers(ReplicaPeers peers)
    {
        m_peers  = peers;
        m_orders = peers.createChannels( peers.getOrderShards(), domainAddress() );
        m_views  = peers.createChannels( peers.getViewChangeShards(), domainAddress() );
        m_chkpts = peers.createChannels( peers.getCheckpointShards(), domainAddress() );
        m_clints = peers.createChannels( peers.getClientShards(), domainAddress() );

        m_conn.initReceiver( this::dispatchIncomingMessage );
    }


    private void dispatchIncomingMessage(NetworkMessage msg)
    {
        PushMessageSink<? super Message> sink;

        switch( MessageTypeID.category( msg.getTypeID() ) )
        {
        case MessageCategoryID.ORDER:
            sink = m_orders.get( m_peers.getInternalOrderCoordinator( ((OrderNetworkMessage) msg).getOrderNumber() ) );
            break;
        case MessageCategoryID.VIEW_CHANGE:
            sink = m_views.get( m_peers.getInternalViewChangeHandler( (ViewChangeNetworkMessage) msg ) );
            break;
        case MessageCategoryID.CHECKPOINT:
            sink = m_chkpts.get( m_peers.getInternalCheckpointCoordinator( (CheckpointNetworkMessage) msg ) );
            break;
        case MessageCategoryID.CLIENT:
            {
                short clientid;

                if( msg.getTypeID()==InvocationMessages.REQUEST_ID )
                    clientid = msg.getSender();
                else
                    clientid = ((Reply) msg).getRequester();

                sink = m_clints.get( m_peers.getClientShard( clientid ) );
            }
            break;
        default:
            throw new UnsupportedOperationException( msg.toString() );
        }

        sink.enqueueMessage( msg );
    }


    public void handleNewConnection(NewConnection newconn)
    {
        // open() is activating.
        markReady();

        m_conn.getConnection( newconn.getRemoteEndpoint().getProcessNumber() ).open( newconn.getState() );
    }


    @Override
    protected void executeSubjects()
    {
        while( m_conn.isReady() )
            m_conn.execute();
    }


    @Override
    public void taskReady(ChronosTask task)
    {
        notifyReady();
    }

}

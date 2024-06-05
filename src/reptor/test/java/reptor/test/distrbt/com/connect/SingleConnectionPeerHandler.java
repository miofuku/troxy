package reptor.test.distrbt.com.connect;

import java.util.Objects;

import reptor.chronos.ChronosTask;
import reptor.chronos.com.PushMessageSink;
import reptor.chronos.context.ChronosSystemContext;
import reptor.chronos.context.SchedulerContext;
import reptor.chronos.orphics.AbstractMaster;
import reptor.distrbt.com.MessageMapper;
import reptor.distrbt.com.NetworkMessage;
import reptor.distrbt.com.connect.PushNetworkTransmissionConnection;
import reptor.distrbt.com.connect.NetworkHandshakeWorker;
import reptor.distrbt.domains.SelectorDomainContext;
import reptor.replct.connect.StandardHandshakeState;


public class SingleConnectionPeerHandler extends AbstractMaster<SelectorDomainContext>
                                         implements SingleConnectionPeer.Context
{

    private final SchedulerContext<? extends SelectorDomainContext> m_cntxt;

    private final ChronosSystemContext  m_domctrl;
    private final MessageMapper     m_mapper;
    private NetworkHandshakeWorker        m_connector;
    private PushNetworkTransmissionConnection       m_conn;
    private SingleConnectionPeer    m_peer;


    public SingleConnectionPeerHandler(SchedulerContext<? extends SelectorDomainContext> cntxt, ChronosSystemContext domctrl,
                                       MessageMapper mapper)
    {
        m_cntxt     = cntxt;
        m_domctrl   = domctrl;
        m_mapper    = mapper;
        m_connector = null;
        m_conn      = null;
    }


    @Override
    protected SchedulerContext<? extends SelectorDomainContext> master()
    {
        return m_cntxt;
    }


    public void init(NetworkHandshakeWorker connector, PushNetworkTransmissionConnection conn, SingleConnectionPeer peer)
    {
        m_connector = Objects.requireNonNull( connector );
        m_conn      = Objects.requireNonNull( conn );
        m_peer      = Objects.requireNonNull( peer );

        m_conn.getInbound().initReceiver( peer );
    }


    @Override
    public void taskReady(ChronosTask task)
    {
        notifyReady();
    }


    @Override
    public boolean execute()
    {
        if( !m_conn.isOpen() )
        {
            m_connector.execute();

            StandardHandshakeState hsstate = (StandardHandshakeState) m_connector.pollNewConnections();

            if( hsstate!=null )
            {
                // Informing it about the established connection activates the peer which in turn can cause
                // a call to taskReady() and hence notifyReady().
                // If this task is not marked as ready, it would inform the master about its ready state
                // although being currently executed. The same holds for open().
                assert isReady();

                m_conn.open( hsstate );
                m_peer.connectionEstablished();
            }
        }
        else
        {
            // TODO: Better: 1. receive messages, 2. execute peer, 3. send messages
            if( m_conn.isReady() )
                m_conn.execute();

            if( m_peer.isReady() )
                m_peer.execute();
        }

        return isDone( !m_conn.isReady() && !m_peer.isReady() );
    }


    @Override
    public MessageMapper getMessageMapper()
    {
        return m_mapper;
    }


    @Override
    public PushMessageSink<? super NetworkMessage> getPeerChannel()
    {
        return m_conn.getOutbound();
    }


    @Override
    public ChronosSystemContext getSystemContext()
    {
        return m_domctrl;
    }

}

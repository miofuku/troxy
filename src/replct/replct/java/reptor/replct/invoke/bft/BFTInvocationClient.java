package reptor.replct.invoke.bft;

import java.util.Arrays;
import java.util.Objects;
import java.util.function.BiConsumer;

import reptor.chronos.context.SchedulerContext;
import reptor.distrbt.certify.suites.AuthorityInstances;
import reptor.distrbt.com.connect.ConnectionConfiguration;
import reptor.distrbt.domains.SelectorDomainContext;
import reptor.distrbt.io.net.NetworkExtensions.ConnectionObserver;
import reptor.replct.connect.Handshaking;
import reptor.replct.connect.HandshakingProcess;
import reptor.replct.invoke.InvocationClient;
import reptor.replct.invoke.InvocationClientProvider;


public abstract class BFTInvocationClient implements InvocationClient
{

    protected final BFTInvocation           m_invoke;
    protected final Handshaking<?>          m_handshake;
    private final int[][]                   m_summary;
    private ConnectionObserver              m_connobs;
    private ConnectionConfiguration         m_repconns;


    public BFTInvocationClient(BFTInvocation invoke, Handshaking<?> handshake, boolean summarize)
    {
        m_invoke    = Objects.requireNonNull( invoke );
        m_handshake = Objects.requireNonNull( handshake );

        if( !summarize )
            m_summary = null;
        else
        {
            m_summary = new int[ invoke.getReplicaGroup().size() ][];
            Arrays.setAll( m_summary, i -> new int[ invoke.getClientToWorkerAssignment().getNumberOfShards() ] );
        }
    }


    @Override
    public BFTInvocationClient connectionObserver(ConnectionObserver connobs)
    {
        m_connobs  = Objects.requireNonNull( connobs );
        m_repconns = createClientToReplicaConnectionConfiguration( connobs );

        return this;
    }


    protected ConnectionConfiguration createClientToReplicaConnectionConfiguration(ConnectionObserver connobs)
    {
        return getInvocation().getConnections().createClientToReplicaConnectionConfiguration( m_connobs );
    }


    @Override
    public HandshakingProcess<?> createHandshake(short clino)
    {
        return m_handshake.createConnectorProcess( clino );
    }


    @Override
    public InvocationClientProvider createInvocationProvider()
    {
        return new BFTClientProvider( this, getClientSummarizer() );
    }


    public abstract BFTClientHandler createInvocationHandler(
            SchedulerContext<? extends SelectorDomainContext> master, short clino, byte contact,
            short remshardno, int[] remaddrnos, AuthorityInstances authinsts,
            BFTClientProvider invprov, ProphecySketcher sketcher);


    protected BiConsumer<Byte, Short> getClientSummarizer()
    {
        return m_summary==null ? null : (contact, remshardno) -> { m_summary[ contact ][ remshardno ]++; };
    }


    @Override
    public BFTInvocation getInvocation()
    {
        return m_invoke;
    }


    public ConnectionConfiguration getReplicaConnectionConfiguration()
    {
        return m_repconns;
    }


    @Override
    public ConnectionObserver getConnectionObserver()
    {
        return m_connobs;
    }


    @Override
    public int[][] getSummary()
    {
        return m_summary;
    }

}

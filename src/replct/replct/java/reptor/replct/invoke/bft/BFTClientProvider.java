package reptor.replct.invoke.bft;

import java.util.function.BiConsumer;

import reptor.chronos.context.SchedulerContext;
import reptor.distrbt.certify.suites.AuthorityInstances;
import reptor.distrbt.com.MessageMapper;
import reptor.distrbt.com.connect.NetworkConnectionProvider;
import reptor.distrbt.com.connect.NetworkGroupConnection;
import reptor.distrbt.domains.SelectorDomainContext;
import reptor.replct.invoke.ClientToWorkerAssignment;
import reptor.replct.invoke.InvocationClientHandler;
import reptor.replct.invoke.InvocationClientProvider;


public class BFTClientProvider implements InvocationClientProvider
{

    private final BFTInvocationClient       m_invcli;
    private final BiConsumer<Byte, Short>   m_clisum;
    private final byte                      m_nreplicas;
    private final MessageMapper             m_mapper;
    private final NetworkConnectionProvider m_connprov;


    public BFTClientProvider(BFTInvocationClient invcli, BiConsumer<Byte, Short> clisum)
    {
        m_invcli    = invcli;
        m_clisum    = clisum;
        m_nreplicas = invcli.getInvocation().getReplicaGroup().size();

        m_mapper   = invcli.getInvocation().getMapping().createClientMessageMapper();
        m_connprov = invcli.getReplicaConnectionConfiguration().connectionProvider( m_mapper );
    }


    @Override
    public InvocationClientHandler createInvocationHandler(SchedulerContext<? extends SelectorDomainContext> master, short clino, ProphecySketcher sketcher)
    {
        AuthorityInstances authinsts = m_invcli.getInvocation().createAuthorityInstancesForClientHandler( clino );

        ClientToWorkerAssignment clitowrk = m_invcli.getInvocation().getClientToWorkerAssignment();
        byte  contact    = clitowrk.getContactForClient( clino, (byte) 1 );
        short remshardno = clitowrk.getShardForClient( clino );
        int[] remaddrnos = clitowrk.getAddressesForClientShard( remshardno );

        if( m_clisum!=null )
            m_clisum.accept( contact, remshardno );

        return m_invcli.createInvocationHandler( master, clino, contact, remshardno, remaddrnos, authinsts, this, sketcher );
    }


    public NetworkGroupConnection createReplicaConnection(BFTClientHandler handler,  short clino)
    {
        NetworkGroupConnection repconn = new NetworkGroupConnection( handler, clino, m_nreplicas, m_connprov );
        repconn.initGroup( i -> clino*100+i, null );
        repconn.initReceiver( handler );

        return repconn;
    }


    public BFTInvocationClient getInvocationClient()
    {
        return m_invcli;
    }


    public MessageMapper getMessageMapper()
    {
        return m_mapper;
    }

}

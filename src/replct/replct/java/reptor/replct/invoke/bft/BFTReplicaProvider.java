package reptor.replct.invoke.bft;

import java.util.Objects;

import reptor.chronos.link.MulticastLink;
import reptor.distrbt.com.MessageMapper;
import reptor.distrbt.com.NetworkMessage;
import reptor.replct.invoke.InvocationReplicaProvider;

// Full name: BFT invocation replica handler provider.
public abstract class BFTReplicaProvider implements InvocationReplicaProvider
{

    protected final short                                       m_clintshard;
    protected final BFTInvocationReplica                        m_invrep;
    protected final MessageMapper                               m_mapper;
    protected final MulticastLink<? super NetworkMessage>       m_repconn;


    public BFTReplicaProvider(BFTInvocationReplica invrep, short clintshard, MessageMapper mapper,
                              MulticastLink<? super NetworkMessage> repconn)
    {
        m_clintshard = clintshard;
        m_invrep     = Objects.requireNonNull( invrep );
        m_mapper     = Objects.requireNonNull( mapper );
        m_repconn    = Objects.requireNonNull( repconn );
    }


    protected short clientNumber(short clintshard, int wrkno)
    {
        return m_invrep.getInvocation().getClientToWorkerAssignment().getClientForWorker( clintshard, wrkno );
    }


    public BFTInvocationReplica getInvocationReplica()
    {
        return m_invrep;
    }


    public MessageMapper getMessageMapper()
    {
        return m_mapper;
    }


    public MulticastLink<? super NetworkMessage> getReplicaConnection()
    {
        return m_repconn;
    }

}

package refit.hybstero.suite;

import java.util.function.Function;

import reptor.chronos.SchedulerContext;
import reptor.chronos.message.MulticastChannel;
import reptor.distrbt.com.MessageMapper;
import reptor.distrbt.com.NetworkMessage;
import reptor.distrbt.domains.SelectorDomainContext;
import reptor.jlib.NotImplementedException;
import reptor.replct.ReplicaPeerGroup;
import reptor.replct.agree.common.checkpoint.Checkpointing;
import reptor.replct.agree.common.order.OrderExtensions;
import reptor.replct.clients.ClientShard;
import reptor.replct.clients.ClientShard.ClientShardContext;
import reptor.replct.common.InstanceDistribution;
import reptor.replct.common.modules.ProtocolShardModule;
import reptor.replct.invoke.bft.BFTInvocation;
import reptor.replct.secure.Cryptography;


public abstract class Hybster
{

    private final Checkpointing m_chkptprot = null;


    public Hybster(ReplicaPeerGroup grpconf,
                  InstanceDistribution propdist, boolean rotate,
                  Cryptography secrets)
    {
        throw new NotImplementedException();
    }

    public boolean usesDedicatedCheckpointProcessors()
    {
        return false;
    }

    public boolean usesDedicatedViewChangeProcessors()
    {
        return false;
    }


    public Checkpointing getCheckpointing()
    {
        return m_chkptprot;
    }


//    public ViewDependentMessage createInitialStableView(ReplicaPeerGroup repgroup)
//    {
//        return new HybsterNewViewStable( createView( 0, repgroup ) );
//    }

    public ProtocolShardModule createOrderShardProcessor(SchedulerContext<? extends SelectorDomainContext> master,
            short shardno, OrderExtensions extmanager,
            MulticastChannel<? super NetworkMessage> reptrans, Function<ClientShardContext, ClientShard> clifac, Cryptography crypto, ReplicaPeerGroup repgroup, BFTInvocation invoke, MessageMapper mapper)
    {
//        if( !Config.USE_TSS_FOR_REPLICAS )
//            throw new UnsupportedOperationException();
//
//        ConnectionFactory confac = new ConnectionFactory( m_repno, shardno, 1, Config.createTrustedModule(), m_keystore, m_nreplicas );
//
//        return new HybsterShardProcessor( master, shardno, this, m_grpconf, extmanager, confac, reptrans );
        throw new  NotImplementedException();
    }

    public ProtocolShardModule createViewChangeShardProcessor(SchedulerContext<? extends SelectorDomainContext> master,
            short no, MulticastChannel<? super NetworkMessage> reptrans)
    {
        throw new UnsupportedOperationException();
    }

    public ProtocolShardModule createCheckpointShardProcessor(SchedulerContext<? extends SelectorDomainContext> master,
            short no, MulticastChannel<? super NetworkMessage> reptrans)
    {
        throw new UnsupportedOperationException();
    }

}

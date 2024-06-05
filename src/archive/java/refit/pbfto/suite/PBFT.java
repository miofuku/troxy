package refit.pbfto.suite;

public abstract class PBFT
{

//    private final Checkpointing m_chkptprot = null;
//
//    public PBFT(ReplicaPeerGroup grpconf,
//                InstanceDistribution propdist, boolean rotate,
//                Cryptography secrets)
//    {
//
//        throw new NotImplementedException();
//
////        if( Config.USE_TSS_FOR_REPLICAS )
////            throw new UnsupportedOperationException();
////
////        m_confac = new ConnectionFactory( m_repno, m_keystore, m_nreplicas );
//    }
//
//    @Override
//    public Checkpointing getCheckpointing()
//    {
//        return m_chkptprot;
//    }
//
//
//    @Override
//    public boolean usesDedicatedCheckpointProcessors()
//    {
//        return true;
//    }
//
//    @Override
//    public boolean usesDedicatedViewChangeProcessors()
//    {
//        return true;
//    }
//
//    @Override
//    public ViewDependentMessage createInitialStableView(ReplicaPeerGroup repgroup)
//    {
//        return new PBFTNewViewStable( createView( 0, repgroup ), null );
//    }
//
//    @Override
//    public ProtocolShardModule createOrderShardProcessor(SchedulerContext<? extends SelectorDomainContext> master,
//            short no, OrderExtensions extmanager,
//            MulticastChannel<? super NetworkMessage> reptrans, Function<ClientShard.Context, ClientShard> clifac, Cryptography crypto, ReplicaPeerGroup repgroup, Invocation invoke, MessageMapper mapper)
//    {
//        return new PBFTOrderShardProcessor( master, no, this, repgroup, extmanager, reptrans );
//    }
//
//    @Override
//    public ProtocolShardModule createViewChangeShardProcessor(SchedulerContext<? extends SelectorDomainContext> master,
//            short no, MulticastChannel<? super NetworkMessage> reptrans)
//    {
//        return new PBFTViewChangeShardModule( master, no, this, reptrans );
//    }
//
//    @Override
//    public ProtocolShardModule createCheckpointShardProcessor(SchedulerContext<? extends SelectorDomainContext> master,
//            short no, MulticastChannel<? super NetworkMessage> reptrans)
//    {
//        return new PBFTCheckpointShardModule( master, no, this, null, reptrans );
//    }

}

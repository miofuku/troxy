package refit.pbfto.order;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import distrbt.com.transmit.MessageTransmitter;
import refit.pbfto.PBFTProtocolShard;
import refit.pbfto.order.PBFTOrderMessages.PBFTPrePrepare;
import refit.pbfto.suite.PBFT;
import refit.pbfto.view.PBFTViewChangeMessages.PBFTNewView;
import refit.pbfto.view.PBFTViewChangeMessages.PBFTNewViewStable;
import reptor.chronos.message.MulticastChannel;
import reptor.distrbt.com.NetworkMessage;
import reptor.replct.ReplicaPeerGroup;
import reptor.replct.agree.common.order.OrderNetworkMessage;
import sun.reflect.generics.reflectiveObjects.NotImplementedException;


public class PBFTOrderShard extends PBFTProtocolShard
{
    private static final Logger s_logger = LoggerFactory.getLogger( PBFTOrderShard.class );

    private final PBFTOrderShardContext m_ordercntxt;

    private MessageTransmitter m_certreptrans;

    private Set<NetworkMessage>    m_uncertains = new HashSet<>();

    private byte m_repno = -1;


    public PBFTOrderShard(PBFT repprot, short no, ReplicaPeerGroup grpconf, PBFTOrderShardContext ordercntxt, MulticastChannel<? super NetworkMessage> reptrans)
    {
        super( repprot, no, reptrans );

        m_ordercntxt = ordercntxt;

        // Actually the ID.
        m_repno      = grpconf.getReplicaNumber();

        m_certreptrans = new MessageTransmitter( m_mapper, getDefaultReplicaConnection().getCertifier(), reptrans );

    }


    public MessageTransmitter getCertifyingReplicaTransmitter()
    {
        return m_certreptrans;
    }



    public OrderInstance createOrderInstance(OrderInstanceContext cntxt, byte repno)
    {
        return new PBFTOrderInstance( cntxt, this, repno );
    }


    public OrderNetworkMessage[][] abortView(Iterator<OrderInstance> curinsts)
    {
//        ArrayList<OrderNetworkMessage[]> prepproofs = new ArrayList<>( Config.CHECKPOINT_INTERVAL );
//
//        while( curinsts.hasNext() )
//        {
//            PBFTOrderInstanceCertifier cert = ((PBFTOrderInstance) curinsts.next()).abort();
//
//            if( cert.isPrepared() )
//                prepproofs.add( cert.getProof() );
//
//            if( !cert.isCommitted() && cert.prePrepare!=null && cert.prePrepare.getSender()==m_repno )
//                m_uncertains.add( cert.prePrepare.getCommand() );
//        }
//
//        s_logger.debug( ViewLogging.MARKER, "{} abort view with {} prepared and {} uncertain instances", this,
//                        prepproofs.size(), m_uncertains.size() );
//
//        return prepproofs.toArray( new OrderNetworkMessage[0][0] );
        throw new  NotImplementedException();
    }


    public void initView(Iterator<OrderInstance> curinsts, PBFTNewViewStable nv)
    {
        PBFTNewView nvshard =
                nv.getNewViewShards()!=null ? nv.getNewViewShards()[ m_shardno ] : null;
        PBFTPrePrepare[] newprepreps =
                nvshard!=null && nvshard.getNewPrePrepares().length>0 ? nvshard.getNewPrePrepares() : null;

        int ppidx = newprepreps!=null ? 0 : -1;

        while( curinsts.hasNext() )
        {
            PBFTOrderInstance inst = (PBFTOrderInstance) curinsts.next();
            PBFTPrePrepare    pp   = null;

            if( ppidx>=0 && ppidx<newprepreps.length && newprepreps[ ppidx ].getOrderNumber()==inst.getInstanceID() )
                pp = newprepreps[ ppidx++ ];

            inst.advanceView( nv.getView(), pp );
        }

        assert ppidx==-1 || ppidx==newprepreps.length;

        // We could check if the proposals are contained in the new view ...
        // or we simply reorder them.
        for( NetworkMessage msg : m_uncertains )
            m_ordercntxt.enqueueProposal( msg );

        m_uncertains.clear();
    }
}

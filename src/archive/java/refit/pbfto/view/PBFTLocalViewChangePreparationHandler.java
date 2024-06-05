package refit.pbfto.view;

import java.util.Arrays;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import refit.pbfto.view.PBFTLocalViewChangePreparationMessages.PBFTCheckpointShardViewChange;
import refit.pbfto.view.PBFTLocalViewChangePreparationMessages.PBFTOrderShardViewChange;
import refit.pbfto.view.PBFTLocalViewChangePreparationMessages.PBFTViewChangeReady;
import refit.pbfto.view.PBFTLocalViewChangePreparationMessages.PBFTViewChangeShardViewChange;
import reptor.replct.agree.common.checkpoint.CheckpointMessages.Checkpoint;
import reptor.replct.agree.common.order.OrderNetworkMessage;
import reptor.replct.agree.common.view.LocalViewChangePreparationProtocol;
import reptor.replct.agree.common.view.ViewChangeShardMessage;
import reptor.replct.agree.common.view.LocalViewChangePreparationProtocol.Context;



public class PBFTLocalViewChangePreparationHandler extends LocalViewChangePreparationProtocol.Handler
{

    private static final Logger s_logger = LoggerFactory.getLogger( PBFTLocalViewChangePreparationHandler.class );

    private Checkpoint[]             m_chkptproof = null;
    private OrderNetworkMessage[][] m_prepset    = null;


    public PBFTLocalViewChangePreparationHandler(Context cntxt)
    {
        super( cntxt );
    }


    public Checkpoint[] getCheckpointProof()
    {
        return m_chkptproof;
    }

    public OrderNetworkMessage[][] getPreparedSet()
    {
        return m_prepset;
    }


    public boolean prepareOrderShard(OrderNetworkMessage[][] prepset)
    {
        return prepareShard( new PBFTOrderShardViewChange( m_cntxt.getShardNumber(), m_curviewno, prepset ) );
    }

    public boolean prepareCheckpointShard(Checkpoint[] stablechkpt)
    {
        return prepareShard( new PBFTCheckpointShardViewChange( m_cntxt.getShardNumber(), m_curviewno, stablechkpt ) );
    }

    public boolean prepareViewChangeShard()
    {
        return prepareShard( new PBFTViewChangeShardViewChange( m_cntxt.getShardNumber(), m_curviewno ) );
    }


    @Override
    protected boolean viewchangeComplete()
    {
        Checkpoint[] latestchkpt = null;

        for( ViewChangeShardMessage msg : getCheckpointShardViewChanges() )
        {
            PBFTCheckpointShardViewChange cvc = (PBFTCheckpointShardViewChange) msg;
            if( cvc.getCheckpointProof()!=null &&
                    ( latestchkpt==null ||
                      cvc.getCheckpointProof()[ 0 ].getOrderNumber() > latestchkpt[ 0 ].getOrderNumber() ) )
                latestchkpt = cvc.getCheckpointProof();
        }

        PBFTOrderShardViewChange[] ordervcs = getOrderShardViewChanges().toArray( new PBFTOrderShardViewChange[ 0 ] );

        return notifyViewChangeReady( new PBFTViewChangeReady( m_curviewno, latestchkpt, ordervcs ) );
    }


    @Override
    protected boolean viewchangeReady()
    {
        PBFTViewChangeReady vcred = (PBFTViewChangeReady) getViewChangeReady();

        m_chkptproof = vcred.getCheckpointProof();

        long chkptinst = m_chkptproof!=null ? m_chkptproof[ 0 ].getOrderNumber() : -1;

        // There is one view change shard per order shard.
        OrderNetworkMessage[][] prepset = vcred.getOrderViewChanges()[ m_cntxt.getShardNumber() ].getPrepareProofs();

        // Did a shard know a newer checkpoint?
        if( prepset.length>0 && prepset[ 0 ][ 0 ].getOrderNumber()<=chkptinst )
        {
            s_logger.debug( "{} adapt prepare set from {} to {}", this, prepset[ 0 ][ 0 ].getOrderNumber(), chkptinst );

            int s = 0;
            for( OrderNetworkMessage[] pp : prepset )
                if( pp[ 0 ].getOrderNumber()<=chkptinst )
                    s++;

            prepset = Arrays.copyOfRange( prepset, s, prepset.length );
        }

        m_prepset = prepset;

        return super.viewchangeReady();
    }


    @Override
    protected void clearResult()
    {
        super.clearResult();

        m_chkptproof = null;
        m_prepset    = null;
    }

}

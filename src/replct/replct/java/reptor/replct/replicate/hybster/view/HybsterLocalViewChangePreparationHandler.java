package reptor.replct.replicate.hybster.view;

import java.util.Arrays;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import reptor.replct.agree.checkpoint.OnePhaseCheckpointShard.CheckpointCertificate;
import reptor.replct.agree.order.OrderNetworkMessage;
import reptor.replct.agree.view.LocalViewChangePreparationProtocol;
import reptor.replct.agree.view.ViewChangeShardMessage;
import reptor.replct.agree.view.LocalViewChangePreparationProtocol.Context;
import reptor.replct.replicate.hybster.view.HybsterLocalViewChangePreparationMessages.HybsterCheckpointShardViewChange;
import reptor.replct.replicate.hybster.view.HybsterLocalViewChangePreparationMessages.HybsterOrderShardViewChange;
import reptor.replct.replicate.hybster.view.HybsterLocalViewChangePreparationMessages.HybsterViewChangeReady;
import reptor.replct.replicate.hybster.view.HybsterLocalViewChangePreparationMessages.HybsterViewChangeShardViewChange;


public class HybsterLocalViewChangePreparationHandler extends LocalViewChangePreparationProtocol.Handler
{

    private static final Logger s_logger = LoggerFactory.getLogger( HybsterLocalViewChangePreparationHandler.class );

    private CheckpointCertificate m_chkptcert    = null;
    private OrderNetworkMessage[] m_prepset      = null;
    private long                  m_orderno_last = -1;


    public HybsterLocalViewChangePreparationHandler(Context cntxt)
    {
        super( cntxt );
    }


    public CheckpointCertificate getCheckpointCertificate()
    {
        return m_chkptcert;
    }

    public OrderNetworkMessage[] getPreparedSet()
    {
        return m_prepset;
    }

    public long getNumberOfLastActiveInstance()
    {
        return m_orderno_last;
    }


    public boolean prepareOrderShard(OrderNetworkMessage[] prepset, long orderno_last)
    {
        return prepareShard( new HybsterOrderShardViewChange( m_cntxt.getShardNumber(), m_curviewno, prepset, orderno_last ) );
    }

    public boolean prepareCheckpointShard(CheckpointCertificate stablechkpt)
    {
        return prepareShard( new HybsterCheckpointShardViewChange( m_cntxt.getShardNumber(), m_curviewno, stablechkpt ) );
    }

    public boolean prepareViewChangeShard()
    {
        return prepareShard( new HybsterViewChangeShardViewChange( m_cntxt.getShardNumber(), m_curviewno ) );
    }


    @Override
    protected boolean viewchangeComplete()
    {
        CheckpointCertificate latestchkpt = null;

        for( ViewChangeShardMessage msg : getCheckpointShardViewChanges() )
        {
            HybsterCheckpointShardViewChange cvc = (HybsterCheckpointShardViewChange) msg;
            if( cvc.getCheckpointCertificate()!=null &&
                    ( latestchkpt==null ||
                      cvc.getCheckpointCertificate().getOrderNumber() > latestchkpt.getOrderNumber() ) )
                latestchkpt = cvc.getCheckpointCertificate();
        }

        HybsterOrderShardViewChange[] ordervcs = getOrderShardViewChanges().toArray( new HybsterOrderShardViewChange[ 0 ] );

        return notifyViewChangeReady( new HybsterViewChangeReady( m_curviewno, latestchkpt, ordervcs ) );
    }


    @Override
    protected boolean viewchangeReady()
    {
        HybsterViewChangeReady vcred = (HybsterViewChangeReady) getViewChangeReady();

        m_chkptcert = vcred.getCheckpointCertificate();

        long chkptinst = m_chkptcert!=null ? m_chkptcert.getOrderNumber() : -1;

        // There is one view change shard per order shard.
        OrderNetworkMessage[] prepset = vcred.getOrderViewChanges()[ m_cntxt.getShardNumber() ].getPrepareMessages();

        // Did a shard know a newer checkpoint?
        if( prepset.length>0 && prepset[ 0 ].getOrderNumber()<=chkptinst )
        {
            s_logger.debug( "{} adapt prepare set from {} to {}", this, prepset[ 0 ].getOrderNumber(), chkptinst );

            int s = 0;
            for( OrderNetworkMessage msg : prepset )
                if( msg.getOrderNumber()<=chkptinst )
                    s++;

            prepset = Arrays.copyOfRange( prepset, s, prepset.length );
        }

        m_prepset      = prepset;
        m_orderno_last = vcred.getOrderViewChanges()[ m_cntxt.getShardNumber() ].getNumberOfLastActiveInstance();

        return super.viewchangeReady();
    }


    @Override
    protected void clearResult()
    {
        super.clearResult();

        m_chkptcert = null;
        m_prepset   = null;
    }

}

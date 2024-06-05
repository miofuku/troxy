package reptor.replct.replicate.pbft.view;

import reptor.chronos.ChronosAddress;
import reptor.chronos.com.PushMessageSink;
import reptor.chronos.context.ChronosDomainContext;
import reptor.distrbt.com.Message;
import reptor.replct.MessageHandler;
import reptor.replct.ProtocolHandler;
import reptor.replct.agree.AgreementPeers;
import reptor.replct.agree.checkpoint.CheckpointMessages.Checkpoint;
import reptor.replct.agree.order.OrderNetworkMessage;
import reptor.replct.agree.view.LocalViewChangePreparationProtocol;
import reptor.replct.agree.view.ViewChangeMessages;
import reptor.replct.replicate.pbft.view.PbftViewChangeMessages.PbftNewViewStable;


public class PbftViewChangeHandler implements ProtocolHandler, MessageHandler<Message>,
                                                  LocalViewChangePreparationProtocol.Context
{

    public static enum State
    {
        VIEW_CHANGE_INITIATED,
        VIEW_CHANGE_PREPARED,
        VIEW_STABLE
    }

    private final ChronosDomainContext m_cntxt;
    private final short  m_shardno;

//    private final PBFTLocalViewChangePreparationHandler          m_locvc;
//    private final PBFTLocalStableViewProtocol.Handler m_locsv;

    private PushMessageSink<Message> m_vccoord = null;

    private State                 m_state      = State.VIEW_CHANGE_INITIATED;
    private PbftNewViewStable m_stableview = null;


    public PbftViewChangeHandler(ChronosDomainContext cntxt, short shardno)
    {
        m_cntxt   = cntxt;
        m_shardno = shardno;
//        m_locvc   = new PBFTLocalViewChangePreparationHandler( this );
//        m_locsv   = PBFTLocalStableViewProtocol.Handler.createForLearner( this );
    }


    private ChronosAddress domain()
    {
        return m_cntxt.getDomainAddress();
    }


    @Override
    public String toString()
    {
        return m_cntxt.toString();
    }


    public State getState()
    {
        return m_state;
    }

//    public int getViewNumber()
//    {
//        return m_locvc.getViewNumber();
//    }

    public PbftNewViewStable getStableView()
    {
        return m_stableview;
    }


    public boolean isViewChangeInitiated()
    {
        return m_state==State.VIEW_CHANGE_INITIATED;
    }

    public boolean isViewChangePrepared()
    {
        return m_state==State.VIEW_CHANGE_PREPARED;
    }

    public boolean isViewStable()
    {
        return m_state==State.VIEW_STABLE;
    }


    @Override
    public boolean handleMessage(Message msg)
    {
        switch( msg.getTypeID() )
        {
        case ViewChangeMessages.PREPARE_VIEW_CHANGE_ID:
            return handleLocalViewChange( msg );
        case ViewChangeMessages.NEW_VIEW_STABLE_ID:
            return handleLocalStableView( msg );
        default:
            throw new IllegalStateException( msg.toString() );
        }
    }


    public void initPeers(AgreementPeers peers)
    {
        m_vccoord = peers.getViewChangeShards().get( peers.getInternalViewChangeCoordinator( 0 ) ).createChannel( domain() );
    }

    public void requestViewChange()
    {
        // We are not the VC coordinator, thus nothing happens in that moment.
//        m_locvc.requestViewChange();
    }

    public boolean prepareOrderShard(OrderNetworkMessage[][] prepset)
    {
//        m_locvc.prepareOrderShard( prepset );

        m_state = State.VIEW_CHANGE_PREPARED;
        return true;
    }

    public boolean prepareCheckpointShard(Checkpoint[] stablechkpt)
    {
//        m_locvc.prepareCheckpointShard( stablechkpt );

        m_state = State.VIEW_CHANGE_PREPARED;
        return true;
    }

    private boolean handleLocalViewChange(Message msg)
    {
//        if( !m_locvc.handleMessage( msg ) )
//            return false;
//
//        assert m_locvc.isViewChangeInitiated();

        m_state      = State.VIEW_CHANGE_INITIATED;
        m_stableview = null;

        // At least the view number changed.
        return true;
    }

    private boolean handleLocalStableView(Message msg)
    {
//        if( !m_locsv.handleMessage( msg ) )
//            return false;
//
//        m_stableview = m_locsv.getNewView();
//
//        m_locvc.initStableView( m_stableview.getView() );
//        m_locsv.initStableView( m_stableview.getView() );
        m_stableview = (PbftNewViewStable) msg;

        m_state = State.VIEW_STABLE;
        return true;
    }


    @Override
    public short getShardNumber()
    {
        return m_shardno;
    }

    @Override
    public boolean isInternalCoordinator()
    {
        return false;
    }

    @Override
    public void enqueueForViewChangeCoordinator(Message msg)
    {
        m_vccoord.enqueueMessage( msg );
    }

    @Override
    public void enqueueForViewChangeAcceptors(Message msg)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public void enqueueForViewChangeLearners(Message msg)
    {
        throw new UnsupportedOperationException();
    }

}

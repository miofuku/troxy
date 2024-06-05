package refit.pbfto.view;

import refit.pbfto.view.PBFTViewChangeMessages.PBFTNewViewStable;
import reptor.chronos.ChronosDomainContext;
import reptor.chronos.PushMessageSink;
import reptor.distrbt.com.Message;
import reptor.replct.MessageHandler;
import reptor.replct.ProtocolHandler;
import reptor.replct.agree.common.AgreementPeers;
import reptor.replct.agree.common.checkpoint.CheckpointMessages.Checkpoint;
import reptor.replct.agree.common.order.OrderNetworkMessage;
import reptor.replct.agree.common.view.LocalViewChangePreparationProtocol;
import reptor.replct.agree.common.view.ViewChangeMessages;


public class PBFTViewChangeHandler implements ProtocolHandler, MessageHandler<Message>,
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

    private final PBFTLocalViewChangePreparationHandler          m_locvc;
    private final PBFTLocalStableViewProtocol.Handler m_locsv;

    private PushMessageSink<Message> m_vccoord = null;

    private State             m_state      = State.VIEW_CHANGE_INITIATED;
    private PBFTNewViewStable m_stableview = null;


    public PBFTViewChangeHandler(ChronosDomainContext cntxt, short shardno)
    {
        m_cntxt   = cntxt;
        m_shardno = shardno;
        m_locvc   = new PBFTLocalViewChangePreparationHandler( this );
        m_locsv   = PBFTLocalStableViewProtocol.Handler.createForLearner( this );
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

    public int getViewNumber()
    {
        return m_locvc.getViewNumber();
    }

    public PBFTNewViewStable getStableView()
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
            return handleLocalSwitchView( msg );
        default:
            throw new IllegalStateException( msg.toString() );
        }
    }


    public void initPeers(AgreementPeers config)
    {
        m_vccoord = config.getViewChangeShards().get( config.getInternalViewChangeCoordinator( 0 ) ).createChannel( m_cntxt.getDomainAddress() );
    }

    public void requestViewChange()
    {
        // We are not the VC coordinator, thus nothing happens in that moment.
        m_locvc.requestViewChange();
    }

    public boolean prepareOrderShard(OrderNetworkMessage[][] prepset)
    {
        m_locvc.prepareOrderShard( prepset );

        m_state = State.VIEW_CHANGE_PREPARED;
        return true;
    }

    public boolean prepareCheckpointShard(Checkpoint[] stablechkpt)
    {
        m_locvc.prepareCheckpointShard( stablechkpt );

        m_state = State.VIEW_CHANGE_PREPARED;
        return true;
    }

    private boolean handleLocalViewChange(Message msg)
    {
        if( !m_locvc.handleMessage( msg ) )
            return false;

        assert m_locvc.isViewChangeInitiated();

        m_state      = State.VIEW_CHANGE_INITIATED;
        m_stableview = null;

        // At least the view number changed.
        return true;
    }

    private boolean handleLocalSwitchView(Message msg)
    {
        if( !m_locsv.handleMessage( msg ) )
            return false;

        m_stableview = m_locsv.getNewView();

        m_locvc.initStableView( m_stableview.getView() );
        m_locsv.initStableView( m_stableview.getView() );

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
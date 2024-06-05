package reptor.replct.agree.view;

import java.util.Collection;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import reptor.distrbt.com.Message;
import reptor.replct.MessageHandler;
import reptor.replct.ProtocolHandler;
import reptor.replct.agree.AgreementPeers;
import reptor.replct.agree.View;
import reptor.replct.agree.view.LocalViewChangePreparationMessages.PrepareViewChange;
import reptor.replct.agree.view.LocalViewChangePreparationMessages.RequestViewChange;


public class LocalViewChangePreparationProtocol
{

    public enum State
    {
        VIEW_CHANGE_INITIATED,
        VIEW_CHANGE_PREPARED,
        VIEW_CHANGE_COMPLETE,
        VIEW_CHANGE_READY,
        VIEW_STABLE
    }


    public interface Context extends LocalViewChangeBaseContext
    {
        void enqueueForViewChangeAcceptors(Message msg);
        void enqueueForViewChangeLearners(Message msg);
    }


    public static class Handler implements ProtocolHandler, MessageHandler<Message>
    {

        private static final Logger s_logger = LoggerFactory.getLogger( Handler.class );

        protected final Context m_cntxt;
        protected final boolean m_isintcoord;

        protected int   m_curviewno = -1;
        protected State m_state     = State.VIEW_CHANGE_INITIATED;

        // Result
        private ViewDependentMessage m_vcready = null;

        // Coordinator members
        private LocalViewChangePreparationCollector m_vccoll = null;
        private int m_lastinit  = -1;
        private int m_lastready = -1;


        private int m_nordervcs = -1;
        private int m_nchkptvcs = -1;
        private int m_nvwchgvcs = -1;


        public Handler(Context cntxt)
        {
            m_cntxt      = cntxt;
            m_isintcoord = cntxt.isInternalCoordinator();
        }


        public State getState()
        {
            return m_state;
        }

        public boolean isViewChangeInitiated()
        {
            return m_state==State.VIEW_CHANGE_INITIATED;
        }

        public int getViewNumber()
        {
            return m_curviewno;
        }

        public boolean isViewChangeComplete()
        {
            return m_state==State.VIEW_CHANGE_COMPLETE;
        }

        public Collection<ViewChangeShardMessage> getOrderShardViewChanges()
        {
            return m_vccoll.getOrderShardViewChanges();
        }

        public Collection<ViewChangeShardMessage> getCheckpointShardViewChanges()
        {
            return m_vccoll.getCheckpointShardViewChanges();
        }

        public Collection<ViewChangeShardMessage> getViewShardViewChanges()
        {
            return m_vccoll.getViewShardViewChanges();
        }

        public boolean isViewChangeReady()
        {
            return m_state==State.VIEW_CHANGE_READY;
        }

        public ViewDependentMessage getViewChangeReady()
        {
            return m_vcready;
        }


        @Override
        public boolean handleMessage(Message msg)
        {
            switch( msg.getTypeID() )
            {
            case ViewChangeMessages.REQUEST_VIEW_CHANGE_ID:
                return handleRequestViewChange( (RequestViewChange) msg );
            case ViewChangeMessages.PREPARE_VIEW_CHANGE_ID:
                return handlePrepareViewChange( (PrepareViewChange) msg );
            case ViewChangeMessages.ORDER_SHARD_VIEW_CHANGE_ID:
            case ViewChangeMessages.CHECKPOINT_SHARD_VIEW_CHANGE_ID:
            case ViewChangeMessages.VIEW_SHARD_VIEW_CHANGE_ID:
                if( !m_isintcoord || m_state!=State.VIEW_CHANGE_PREPARED )
                    return prepareShard( (ViewChangeShardMessage) msg );
                else
                    return handleViewChangePrepared( (ViewChangeShardMessage) msg );
            case ViewChangeMessages.VIEW_CHANGE_READY_ID:
                if( m_isintcoord && ((ViewDependentMessage) msg).getViewNumber()>m_lastready )
                    return notifyViewChangeReady( (ViewDependentMessage) msg );
                else
                    return handleViewChangeReady( (ViewDependentMessage) msg );
            default:
                throw new IllegalStateException( msg.toString() );
            }
        }


        public void initPeers(AgreementPeers peers)
        {
            assert m_vccoll==null;

            m_nordervcs = peers.getOrderShards().size();
            m_nchkptvcs = peers.getCheckpointShards().size();
            m_nvwchgvcs = peers.getViewChangeShards().size();
        }


        public void initStableView(View view)
        {
            m_curviewno = view.getNumber();

            clearResult();

            m_state = State.VIEW_STABLE;
        }


        public boolean requestViewChange()
        {
            RequestViewChange reqvc = new RequestViewChange( m_curviewno+1 );

            m_cntxt.enqueueForViewChangeCoordinator( reqvc );
            return false;
        }


        public boolean handleRequestViewChange(RequestViewChange reqvc)
        {
            assert m_isintcoord;

            if( reqvc.getViewNumber()<=m_lastinit )
                return false;

            m_lastinit = reqvc.getViewNumber();

            s_logger.debug( "{} initiate view change for view {}", m_cntxt, m_lastinit );

            return instructPrepareViewChange( reqvc.getViewNumber() );
        }


        public boolean instructPrepareViewChange(int viewno)
        {
            m_cntxt.enqueueForViewChangeAcceptors( new PrepareViewChange( viewno ) );
            return false;
        }


        public boolean handlePrepareViewChange(PrepareViewChange prepvc)
        {
            if( prepvc.getViewNumber()<=m_curviewno )
                return false;

            m_curviewno = prepvc.getViewNumber();

            if( m_isintcoord )
                m_vccoll = new LocalViewChangePreparationCollector( m_nordervcs, m_nchkptvcs, m_nvwchgvcs );

            clearResult();

            s_logger.debug( "{} prepare view change for view {}", m_cntxt, m_curviewno );

            return viewchangeInitiated();
        }


        protected boolean viewchangeInitiated()
        {
            m_state = State.VIEW_CHANGE_INITIATED;
            return true;
        }


        public boolean prepareShard(ViewChangeShardMessage vcprep)
        {
            s_logger.debug( "{} view change prepared for {} {}", m_cntxt, vcprep.getViewNumber(), vcprep );

            m_state = State.VIEW_CHANGE_PREPARED;

            m_cntxt.enqueueForViewChangeCoordinator( vcprep );
            return true;
        }


        public boolean handleViewChangePrepared(ViewChangeShardMessage vcprep)
        {
            assert m_isintcoord && m_state!=State.VIEW_CHANGE_COMPLETE;

            if( vcprep.getViewNumber()<m_curviewno )
                return false;

            assert vcprep.getViewNumber()==m_curviewno;

            if( !m_vccoll.addViewChange( vcprep ) )
                return false;
            else
                return viewchangeComplete();
        }


        protected boolean viewchangeComplete()
        {
            m_state = State.VIEW_CHANGE_COMPLETE;
            return true;
        }


        public boolean notifyViewChangeReady(ViewDependentMessage vcred)
        {
            assert m_isintcoord && vcred.getViewNumber()>m_lastready;

            m_lastready = vcred.getViewNumber();

            m_cntxt.enqueueForViewChangeLearners( vcred );
            return false;
        }


        public boolean handleViewChangeReady(ViewDependentMessage vcred)
        {
            assert vcred.getViewNumber()==m_curviewno;

            s_logger.debug( "{} view change ready for view {}", m_cntxt, m_curviewno );

            m_vcready = vcred;

            return viewchangeReady();
        }


        protected boolean viewchangeReady()
        {
            m_state = State.VIEW_CHANGE_READY;
            return true;
        }


        protected void clearResult()
        {
            m_vcready = null;
        }

    }

}

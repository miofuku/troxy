package reptor.replct.replicate.hybster.view;

import java.util.Arrays;
import java.util.function.IntFunction;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import reptor.chronos.Orphic;
import reptor.distrbt.com.Message;
import reptor.distrbt.com.MessageMapper;
import reptor.replct.MessageHandler;
import reptor.replct.agree.AgreementPeers;
import reptor.replct.agree.View;
import reptor.replct.agree.view.LocalViewChangeBaseContext;
import reptor.replct.agree.view.LocalViewChangeVerificationProtocol;
import reptor.replct.agree.view.ViewChangeContentCertifier;
import reptor.replct.agree.view.ViewChangeMessages;
import reptor.replct.agree.view.ViewChangeNetworkMessage;
import reptor.replct.agree.view.InternalViewChangeMessages.ViewChangeVerificationMessage;
import reptor.replct.replicate.hybster.view.HybsterLocalStableViewMessages.HybsterConfirmNewView;
import reptor.replct.replicate.hybster.view.HybsterLocalStableViewMessages.HybsterNewViewShardConfirmed;
import reptor.replct.replicate.hybster.view.HybsterViewChangeMessages.HybsterNewView;
import reptor.replct.replicate.hybster.view.HybsterViewChangeMessages.HybsterNewViewStable;


public class HybsterLocalStableViewProtocol
{

    public interface Context extends LocalViewChangeBaseContext
    {
        MessageMapper         getMessageMapper();

        void                  verifyViewChangeMessage(ViewChangeNetworkMessage msg);

        void enqueueForStableViewAcceptors(Message msg);
        void enqueueForStableViewLearners(Message msg);
    }


    public static class Handler extends LocalViewChangeVerificationProtocol.Handler<HybsterNewView>
                                implements MessageHandler<Message>
    {
        private static class HybsterNewViewCertifier extends ViewChangeContentCertifier<HybsterNewView>
        {
            public HybsterNewViewCertifier(byte repno, int nshards)
            {
                super( repno, nshards );
            }
        }

        private static final Logger s_logger = LoggerFactory.getLogger( Handler.class );

        private final Context           m_acccntxt;
        private final IntFunction<View> m_viewfac;

        private HybsterNewViewStable m_newview = null;

        // Coordinator members
        private int m_nnvshards = -1;


        public static Handler createForLearner(Orphic lercntxt)
        {
            return new Handler( lercntxt );
        }

        public static Handler createForAcceptor(Context acccntxt, IntFunction<View> viewfac)
        {
            return new Handler( acccntxt, viewfac );
        }

        public Handler(Orphic lercntxt)
        {
            super( lercntxt );

            m_acccntxt = null;
            m_viewfac  = null;
        }

        public Handler(Context acccntxt, IntFunction<View> viewfac)
        {
            super( acccntxt, acccntxt.getShardNumber(), acccntxt.isInternalCoordinator() );

            m_acccntxt = acccntxt;
            m_viewfac  = viewfac;
        }


        // TODO: Use view certificate instead of an internal message.
        public HybsterNewViewStable getNewView()
        {
            return m_newview;
        }


        @Override
        public boolean handleMessage(Message msg)
        {
            switch( msg.getTypeID() )
            {
            case HybsterViewChangeMessages.HYBSTER_NEW_VIEW_ID:
                if( !m_isintcoord )
                    return forwardNewView( (HybsterNewView) msg );
                else
                    return handleNewView( (HybsterNewView) msg );
            case ViewChangeMessages.CONFIRM_NEW_VIEW_ID:
                return handleConfirmNewView( (HybsterConfirmNewView) msg );
            case ViewChangeMessages.NEW_VIEW_SHARD_CONFIRMED_ID:
                return handleNewViewShardConfirmed( (HybsterNewViewShardConfirmed) msg );
            case ViewChangeMessages.NEW_VIEW_STABLE_ID:
                return handleNewViewStable( (HybsterNewViewStable) msg );
            default:
                throw new IllegalStateException( msg.toString() );
            }
        }


        public void initPeers(AgreementPeers peers)
        {
            m_nnvshards = peers.getViewChangeShards().size();
        }


        @Override
        public void initStableView(View view)
        {
            // Do we have a group reconfiguration? Okay, for now, it's only possible for the initial view.
            if( m_isintcoord && view.getNumber()==1 && m_acccntxt!=null )
            {
                HybsterNewViewCertifier[] certifs = new HybsterNewViewCertifier[ view.getNumberOfReplicas() ];
                Arrays.setAll( certifs, i -> new HybsterNewViewCertifier( (byte) i, m_nnvshards ) );

                init( certifs );
            }

            m_newview = null;

            super.initStableView( view );
        }


        public boolean forwardNewView(HybsterNewView nv)
        {
            return forwardExternalMessage( nv );
        }


        public boolean handleNewView(HybsterNewView nv)
        {
            return handleExternalMessage( nv );
        }


        @Override
        protected boolean messageComplete(ViewChangeContentCertifier<HybsterNewView> certif)
        {
            HybsterNewView[] shards = certif.getMessages().toArray( new HybsterNewView[ 0 ] );
            return instructConfirmMessage( new HybsterConfirmNewView( shards ) );
        }


        public boolean handleConfirmNewView(HybsterConfirmNewView confnv)
        {
            return handleConfirmMessage( confnv );
        }


        @Override
        protected boolean confirmMessage(ViewChangeVerificationMessage<HybsterNewView[]> confmsg)
        {
            HybsterNewView nv = confmsg.getMessage()[ m_shardno ];

            s_logger.debug( "{} confirm {}", m_acccntxt, nv );

            m_acccntxt.verifyViewChangeMessage( nv );

            return notifyMessageShardConfirmed( new HybsterNewViewShardConfirmed( nv ) );
        }


        public boolean handleNewViewShardConfirmed(HybsterNewViewShardConfirmed nvconf)
        {
            return handleMessageShardConfirmed( nvconf );
        }


        @Override
        protected boolean messageConfirmed(ViewChangeContentCertifier<HybsterNewView> certif)
        {
            HybsterNewView[]     nvshards = certif.getMessages().toArray( new HybsterNewView[ 0 ] );
            View                  view     = m_viewfac.apply( certif.getViewNumber() );
            HybsterNewViewStable nv       = new HybsterNewViewStable( view, nvshards );

            return notifyResult( nv );
        }


        public boolean handleNewViewStable(HybsterNewViewStable nv)
        {
            assert m_curviewno<nv.getViewNumber() || m_isintcoord && m_curviewno==nv.getViewNumber();
            assert m_newview==null;

            s_logger.debug( "{} switch to view {}", m_cntxt, nv.getViewNumber() );

            m_curviewno = nv.getViewNumber();
            m_newview   = nv;

            return true;
        }


        @Override
        public void enqueueForViewChangeCoordinator(Message msg)
        {
            m_acccntxt.enqueueForViewChangeCoordinator( msg );
        }

        @Override
        public void enqueueForVerificationAcceptors(Message msg)
        {
            m_acccntxt.enqueueForStableViewAcceptors( msg );
        }

        @Override
        public void enqueueForVerificationLearners(Message msg)
        {
            m_acccntxt.enqueueForStableViewLearners( msg );
        }

    }

}

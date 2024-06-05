package refit.pbfto.view;

import java.util.Arrays;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import refit.pbfto.view.PBFTLocalStableViewMessages.PBFTConfirmNewView;
import refit.pbfto.view.PBFTLocalStableViewMessages.PBFTNewViewShardConfirmed;
import refit.pbfto.view.PBFTViewChangeMessages.PBFTNewView;
import refit.pbfto.view.PBFTViewChangeMessages.PBFTNewViewStable;
import reptor.chronos.Orphic;
import reptor.distrbt.certify.VerifierGroup;
import reptor.distrbt.com.Message;
import reptor.distrbt.com.MessageMapper;
import reptor.replct.MessageHandler;
import reptor.replct.agree.common.AgreementPeers;
import reptor.replct.agree.common.view.LocalViewChangeBaseContext;
import reptor.replct.agree.common.view.LocalViewChangeVerificationProtocol;
import reptor.replct.agree.common.view.View;
import reptor.replct.agree.common.view.ViewChangeContentCertifier;
import reptor.replct.agree.common.view.ViewChangeMessages;
import reptor.replct.agree.common.view.InternalViewChangeMessages.ViewChangeVerificationMessage;


public class PBFTLocalStableViewProtocol
{

    public interface Context extends LocalViewChangeBaseContext
    {
        void enqueueForStableViewAcceptors(Message msg);
        void enqueueForStableViewLearners(Message msg);

        MessageMapper       getMessageMapper();
        VerifierGroup  getStrongVerifier();
        View                createView(int viewno);
    }


    public static class Handler extends LocalViewChangeVerificationProtocol.Handler<PBFTNewView>
                                implements MessageHandler<Message>
    {
        private static class PBFTNewViewCertifier extends ViewChangeContentCertifier<PBFTNewView>
        {
            public PBFTNewViewCertifier(byte repno, int nshards)
            {
                super( repno, nshards );
            }
        }

        private static final Logger s_logger = LoggerFactory.getLogger( Handler.class );

        private final Context m_acccntxt;

        private PBFTNewViewShardCertifier m_nvshardcertif;
        private PBFTNewViewStable         m_newview  = null;

        // Coordinator members
        private int m_nnvshards = -1;


        public static Handler createForLearner(Orphic lercntxt)
        {
            return new Handler( lercntxt );
        }

        public static Handler createForAcceptor(Context acccntxt)
        {
            return new Handler( acccntxt );
        }

        public Handler(Orphic lercntxt)
        {
            super( lercntxt );

            m_acccntxt= null;
        }

        public Handler(Context acccntxt)
        {
            super( acccntxt, acccntxt.getShardNumber(), acccntxt.isInternalCoordinator() );

            m_acccntxt = acccntxt;
        }


        // TODO: Use view certificate instead of an internal message.
        public PBFTNewViewStable getNewView()
        {
            return m_newview;
        }


        @Override
        public boolean handleMessage(Message msg)
        {
            switch( msg.getTypeID() )
            {
            case PBFTViewChangeMessages.PBFT_NEW_VIEW_ID:
                if( !m_isintcoord )
                    return forwardNewView( (PBFTNewView) msg );
                else
                    return handleNewView( (PBFTNewView) msg );
            case ViewChangeMessages.CONFIRM_NEW_VIEW_ID:
                return handleConfirmNewView( (PBFTConfirmNewView) msg );
            case ViewChangeMessages.NEW_VIEW_SHARD_CONFIRMED_ID:
                return handleNewViewShardConfirmed( (PBFTNewViewShardConfirmed) msg );
            case ViewChangeMessages.NEW_VIEW_STABLE_ID:
                return handleNewViewStable( (PBFTNewViewStable) msg );
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
            if( view.getNumber()==0 && m_acccntxt!=null )
            {
                m_nvshardcertif = new PBFTNewViewShardCertifier( m_acccntxt.getMessageMapper(), m_acccntxt.getStrongVerifier() );

                if( m_isintcoord )
                {
                    PBFTNewViewCertifier[] certifs = new PBFTNewViewCertifier[ view.getNumberOfReplicas() ];
                    Arrays.setAll( certifs, i -> new PBFTNewViewCertifier( (byte) i, m_nnvshards ) );

                    init( certifs );
                }
            }

            m_newview = null;

            super.initStableView( view );
        }


        public boolean forwardNewView(PBFTNewView nv)
        {
            return forwardExternalMessage( nv );
        }


        public boolean handleNewView(PBFTNewView nv)
        {
            return handleExternalMessage( nv );
        }


        @Override
        protected boolean messageComplete(ViewChangeContentCertifier<PBFTNewView> certif)
        {
            PBFTNewView[] shards = certif.getMessages().toArray( new PBFTNewView[ 0 ] );
            return instructConfirmMessage( new PBFTConfirmNewView( shards ) );
        }


        public boolean handleConfirmNewView(PBFTConfirmNewView confnv)
        {
            return handleConfirmMessage( confnv );
        }


        @Override
        protected boolean confirmMessage(ViewChangeVerificationMessage<PBFTNewView[]> confmsg)
        {
            PBFTNewView nv = confmsg.getMessage()[ m_shardno ];

            s_logger.debug( "{} confirm {}", m_acccntxt, nv );

            if( !m_nvshardcertif.addNewView( nv ) )
                return false;
            else
                return notifyMessageShardConfirmed( new PBFTNewViewShardConfirmed( nv ) );
        }


        public boolean handleNewViewShardConfirmed(PBFTNewViewShardConfirmed nvconf)
        {
            return handleMessageShardConfirmed( nvconf );
        }


        @Override
        protected boolean messageConfirmed(ViewChangeContentCertifier<PBFTNewView> certif)
        {
            PBFTNewView[]     nvshards = certif.getMessages().toArray( new PBFTNewView[ 0 ] );
            View              view     = m_acccntxt.createView( certif.getViewNumber() );
            PBFTNewViewStable nv       = new PBFTNewViewStable( view, nvshards );

            return notifyResult( nv );
        }


        public boolean handleNewViewStable(PBFTNewViewStable nv)
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

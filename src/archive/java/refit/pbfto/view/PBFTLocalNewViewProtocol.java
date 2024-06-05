package refit.pbfto.view;

import java.util.Arrays;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import refit.common.BallotBox;
import refit.pbfto.view.PBFTLocalNewViewMessages.PBFTConfirmViewChange;
import refit.pbfto.view.PBFTLocalNewViewMessages.PBFTNewViewReady;
import refit.pbfto.view.PBFTLocalNewViewMessages.PBFTViewChangeShardConfirmed;
import refit.pbfto.view.PBFTViewChangeMessages.PBFTViewChange;
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
import sun.reflect.generics.reflectiveObjects.NotImplementedException;


public class PBFTLocalNewViewProtocol
{

    public static enum State
    {
        VIEW_CHANGE_INITIATED,
        VIEW_CHANGE_CONFIRMED,
        VIEW_CHANGE_AGREED,
        NEW_VIEW_READY,
        VIEW_STABLE
    }


    public interface Context extends LocalViewChangeBaseContext
    {
        void enqueueForNewViewAcceptors(Message msg);
        void enqueueForNewViewLearners(Message msg);

        MessageMapper       getMessageMapper();
        VerifierGroup  getDefaultVerifier();
        VerifierGroup  getStrongVerifier();
        VerifierGroup  getClientVerifier();
    }


    public static class Handler extends LocalViewChangeVerificationProtocol.Handler<PBFTViewChange>
                                implements MessageHandler<Message>
    {
        // Certifies the correctness of all shards of a view change message from a replica.
        // TODO: Only one checkpoint certificate.
        // TODO: All vcshard start after the checkpoint.
        // TODO: Instance certificates are only contained once.
        private static class PBFTViewChangeCertifier extends ViewChangeContentCertifier<PBFTViewChange>
        {
            public PBFTViewChangeCertifier(byte repno, int nshards)
            {
                super( repno, nshards );
            }
        }

        private static final Logger s_logger = LoggerFactory.getLogger( Handler.class );

        private final Context m_cntxt;

        private State m_state = State.VIEW_CHANGE_INITIATED;

        private byte  m_locrepno;
        private View  m_groupview;
        private PBFTViewChangeShardCertifier m_vcshardcertif;

        private PBFTNewViewReady m_nvready = null;

        // Coordinator members
        private int m_nvcshards = -1;

        private BallotBox<Byte, Integer, PBFTViewChangeCertifier> m_vcballot;


        public Handler(Context cntxt)
        {
            super( cntxt, cntxt.getShardNumber(), cntxt.isInternalCoordinator() );

//            m_cntxt    = cntxt;
//            m_vcballot = new BallotBox<>( Config.QUORUM );
            throw new  NotImplementedException();
        }


        public State getState()
        {
            return m_state;
        }

        public int getViewNumber()
        {
            return m_curviewno;
        }

        public boolean isViewChangeConfirmed()
        {
            return m_state==State.VIEW_CHANGE_CONFIRMED;
        }

        public boolean isViewChangeAgreed()
        {
            return m_state==State.VIEW_CHANGE_AGREED;
        }

        public boolean isNewViewReady()
        {
            return m_state==State.NEW_VIEW_READY;
        }

        public PBFTNewViewReady getNewView()
        {
            return m_nvready;
        }


        @Override
        public boolean handleMessage(Message msg)
        {
            switch( msg.getTypeID() )
            {
            case PBFTViewChangeMessages.PBFT_VIEW_CHANGE_ID:
                if( !m_isintcoord )
                    return forwardViewChange( (PBFTViewChange) msg );
                else
                    return handleViewChange( (PBFTViewChange) msg );
            case ViewChangeMessages.CONFIRM_VIEW_CHANGE_ID:
                return handleConfirmViewChange( (PBFTConfirmViewChange) msg );
            case ViewChangeMessages.VIEW_CHANGE_CONFIRMED_ID:
                return handleViewChangeConfirmed( (PBFTViewChangeShardConfirmed) msg );
            case ViewChangeMessages.NEW_VIEW_READY_ID:
                return handleNewViewReady( (PBFTNewViewReady) msg );
            default:
                throw new IllegalStateException( msg.toString() );
            }
        }

        public void initPeers(AgreementPeers peers)
        {
            m_nvcshards = peers.getViewChangeShards().size();
        }


        @Override
        public void initStableView(View view)
        {
            if( view.getNumber()==0 )
            {
                m_locrepno  = view.getReplicaGroup().getReplicaNumber();
                m_groupview = view;

                m_vcshardcertif = new PBFTViewChangeShardCertifier( m_cntxt.getMessageMapper(), m_cntxt.getDefaultVerifier(),
                                                m_cntxt.getStrongVerifier(), m_cntxt.getClientVerifier(),
                                                m_locrepno );
                if( m_isintcoord )
                {
                    PBFTViewChangeCertifier[] certifs = new PBFTViewChangeCertifier[ view.getNumberOfReplicas() ];
                    Arrays.setAll( certifs, i -> new PBFTViewChangeCertifier( (byte) i, m_nvcshards ) );

                    init( certifs );
                }
            }

            m_nvready = null;

            super.initStableView( view );

            m_state = State.VIEW_STABLE;
        }


        public boolean forwardViewChange(PBFTViewChange vc)
        {
            return forwardExternalMessage( vc );
        }


        public boolean handleViewChange(PBFTViewChange vc)
        {
            return handleExternalMessage( vc );
        }


        @Override
        protected boolean messageComplete(ViewChangeContentCertifier<PBFTViewChange> certif)
        {
            PBFTViewChange[] shards = certif.getMessages().toArray( new PBFTViewChange[ 0 ] );
            return instructConfirmMessage( new PBFTConfirmViewChange( shards ) );
        }


        public boolean handleConfirmViewChange(PBFTConfirmViewChange confvc)
        {
            return handleConfirmMessage( confvc );
        }


        @Override
        protected boolean confirmMessage(ViewChangeVerificationMessage<PBFTViewChange[]> confmsg)
        {
            PBFTViewChange vc = confmsg.getMessage()[ m_shardno ];

            s_logger.debug( "{} confirm {}", m_cntxt, vc );

            if( !m_vcshardcertif.addViewChange( vc ) )
                return false;
            else
                return notifyMessageShardConfirmed( new PBFTViewChangeShardConfirmed( vc ) );
        }


        public boolean handleViewChangeConfirmed(PBFTViewChangeShardConfirmed vcconf)
        {
            return handleMessageShardConfirmed( vcconf );
        }


        @Override
        protected boolean messageConfirmed(ViewChangeContentCertifier<PBFTViewChange> certif)
        {
//            m_vcballot.add( certif.getReplicaNumber(), certif.getViewNumber(), (PBFTViewChangeCertifier) certif );
//
//            State nextstate;
//
//            if( m_certifs[ m_locrepno ].isConfirmed() && m_vcballot.getDecision()!=null )
//                nextstate = State.VIEW_CHANGE_AGREED;
//            // TODO: This is not sufficient. Confirmed is the f+1th highest view number of all received view changes.
//            else if( m_vcballot.size()>=Config.WEAK_QUORUM )
//                nextstate = State.VIEW_CHANGE_CONFIRMED;
//            else
//                nextstate = State.VIEW_CHANGE_INITIATED;
//
//            // TODO: multiple new views?
//            if( nextstate==m_state )
//                return false;
//            else if( nextstate==State.VIEW_CHANGE_AGREED &&
//                     m_groupview.getCoordinator( m_vcballot.getDecision() )==m_locrepno &&
//                     m_vcballot.getDecision()>m_curviewno )
//                return notifyResult( createNewViewReady() );
//            else
//            {
//                m_state = nextstate;
//                return true;
//            }
            throw new  NotImplementedException();
        }


        public boolean handleNewViewReady(PBFTNewViewReady nvred)
        {
            assert m_curviewno<nvred.getViewNumber() || m_isintcoord && m_curviewno==nvred.getViewNumber();
            assert m_nvready==null;

            s_logger.debug( "{} new view ready {}", m_cntxt, nvred.getViewNumber() );

            m_curviewno = nvred.getViewNumber();
            m_nvready   = nvred;

            m_state = State.NEW_VIEW_READY;
            return true;
        }


        private PBFTNewViewReady createNewViewReady()
        {
            // TODO: NewViewCertificate
            int  viewno = m_vcballot.getDecision();
            long mins   = 0;
            long maxs   = 0;

//            PBFTViewChange[][] nvproof = new PBFTViewChange[ Config.QUORUM ][];
//
//            Iterator<PBFTViewChangeCertifier> iter = m_vcballot.getDecidingBallots().iterator();
//            for( int i=0; i<nvproof.length; i++ )
//            {
//                nvproof[ i ] = iter.next().getMessages().toArray( new PBFTViewChange[ 0 ] );
//
//                for( int k=0; k<nvproof[ i ].length; k++ )
//                {
//                    PBFTViewChange vc = nvproof[ i ][ k ];
//
//                    Checkpoint[] cc = vc.getCheckpointCertificate();
//                    if( cc.length>0 && cc[ 0 ].getOrderNumber()>mins )
//                        mins = cc[ 0 ].getOrderNumber();
//
//                    OrderNetworkMessage[][] pc = vc.getPrepareCertificates();
//                    if( pc.length>0 && pc[ pc.length-1 ][ 0 ].getOrderNumber()>maxs )
//                        maxs = pc[ pc.length-1 ][ 0 ].getOrderNumber();
//                }
//            }
//
//            if( maxs==0 )
//                maxs = mins;
//
//            s_logger.debug( "{} new view {} ready with {}-{}", m_cntxt, viewno, mins, maxs );
//
//            return new PBFTNewViewReady( viewno, nvproof, mins, maxs );
            throw new  NotImplementedException();
        }


        @Override
        protected void initCertifier(ViewChangeContentCertifier<PBFTViewChange> certif, int viewno)
        {
            if( certif.isConfirmed() )
                m_vcballot.remove( certif.getReplicaNumber() );

            super.initCertifier( certif, viewno );
        }


        @Override
        protected void enqueueForViewChangeCoordinator(Message msg)
        {
            m_cntxt.enqueueForViewChangeCoordinator( msg );
        }

        @Override
        protected void enqueueForVerificationAcceptors(Message msg)
        {
            m_cntxt.enqueueForNewViewAcceptors( msg );
        }

        @Override
        protected void enqueueForVerificationLearners(Message msg)
        {
            m_cntxt.enqueueForNewViewLearners( msg );
        }

    }

}

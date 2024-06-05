package reptor.replct.replicate.hybster.view;

import java.util.Arrays;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import reptor.distrbt.com.Message;
import reptor.distrbt.com.MessageMapper;
import reptor.jlib.NotImplementedException;
import reptor.replct.MessageHandler;
import reptor.replct.ReplicaPeerGroup;
import reptor.replct.agree.AgreementPeers;
import reptor.replct.agree.View;
import reptor.replct.agree.view.LocalViewChangeBaseContext;
import reptor.replct.agree.view.LocalViewChangeVerificationProtocol;
import reptor.replct.agree.view.ViewChangeContentCertifier;
import reptor.replct.agree.view.ViewChangeMessages;
import reptor.replct.agree.view.ViewChangeNetworkMessage;
import reptor.replct.agree.view.InternalViewChangeMessages.ViewChangeVerificationMessage;
import reptor.replct.replicate.hybster.view.HybsterLocalNewViewMessages.HybsterConfirmViewChange;
import reptor.replct.replicate.hybster.view.HybsterLocalNewViewMessages.HybsterNewViewReady;
import reptor.replct.replicate.hybster.view.HybsterLocalNewViewMessages.HybsterViewChangeShardConfirmed;
import reptor.replct.replicate.hybster.view.HybsterViewChangeMessages.HybsterViewChange;


public class HybsterLocalNewViewProtocol
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
        MessageMapper               getMessageMapper();

        ReplicaPeerGroup   getReplicaGroupConfiguration();

        void                        verifyViewChangeMessage(ViewChangeNetworkMessage msg);

        void enqueueForNewViewAcceptors(Message msg);
        void enqueueForNewViewLearners(Message msg);
    }


    public static class Handler extends LocalViewChangeVerificationProtocol.Handler<HybsterViewChange>
                                implements MessageHandler<Message>
    {
        // Certifies the correctness of all shards of a view change message from a replica.
        // TODO: Only one checkpoint certificate.
        // TODO: All vcshard start after the checkpoint.
        // TODO: Instance certificates are only contained once.
        private static class HybsterViewChangeCertifier extends ViewChangeContentCertifier<HybsterViewChange>
        {
            public HybsterViewChangeCertifier(byte repno, int nshards)
            {
                super( repno, nshards );
            }
        }

        private static final Logger s_logger = LoggerFactory.getLogger( Handler.class );

        private final Context       m_cntxt;
//        private final MessageMapper m_mapper;
        private State m_state = State.VIEW_CHANGE_INITIATED;

//        private byte  m_locrepno;
//        private View  m_groupview;
//
//       private final int m_strongquorumsize;
//       private final int m_weakquorumsize;


        private HybsterNewViewReady m_nvready = null;

        // Coordinator members
        private int m_nvcshards = -1;

//        private BallotBox<Byte, Integer, HybsterXViewChangeCertifier> m_vcballot;


        public Handler(Context cntxt)
        {
            super( cntxt, cntxt.getShardNumber(), cntxt.isInternalCoordinator() );

//            m_strongquorumsize = cntxt.getReplicaGroupConfiguration().getNumberOfReplicas()/2 + 1;
//            m_weakquorumsize   = cntxt.getReplicaGroupConfiguration().getNumberOfTolerableFaults() + 1;

            m_cntxt    = cntxt;
//            m_mapper   = cntxt.getMessageMapper();
//            m_vcballot = new BallotBox<>( m_strongquorumsize );
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

        public HybsterNewViewReady getNewView()
        {
            return m_nvready;
        }


        @Override
        public boolean handleMessage(Message msg)
        {
            switch( msg.getTypeID() )
            {
            case HybsterViewChangeMessages.HYBSTER_VIEW_CHANGE_ID:
                if( !m_isintcoord )
                    return forwardViewChange( (HybsterViewChange) msg );
                else
                    return handleViewChange( (HybsterViewChange) msg );
            case ViewChangeMessages.CONFIRM_VIEW_CHANGE_ID:
                return handleConfirmViewChange( (HybsterConfirmViewChange) msg );
            case ViewChangeMessages.VIEW_CHANGE_CONFIRMED_ID:
                return handleViewChangeConfirmed( (HybsterViewChangeShardConfirmed) msg );
            case ViewChangeMessages.NEW_VIEW_READY_ID:
                return handleNewViewReady( (HybsterNewViewReady) msg );
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
            if( view.getNumber()==1 )
            {
//                m_locrepno  = view.getReplicaNumber();
//                m_groupview = view;

                if( m_isintcoord )
                {
                    HybsterViewChangeCertifier[] certifs = new HybsterViewChangeCertifier[ view.getNumberOfReplicas() ];
                    Arrays.setAll( certifs, i -> new HybsterViewChangeCertifier( (byte) i, m_nvcshards ) );

                    init( certifs );
                }
            }
            else
            {
//                m_vcballot.clear();
                throw new NotImplementedException();
            }

            m_nvready = null;

            super.initStableView( view );

            m_state = State.VIEW_STABLE;
        }


        public boolean forwardViewChange(HybsterViewChange vc)
        {
            return forwardExternalMessage( vc );
        }


        public boolean handleViewChange(HybsterViewChange vc)
        {
            return handleExternalMessage( vc );
        }


        @Override
        protected boolean messageComplete(ViewChangeContentCertifier<HybsterViewChange> certif)
        {
            HybsterViewChange[] shards = certif.getMessages().toArray( new HybsterViewChange[ 0 ] );
            return instructConfirmMessage( new HybsterConfirmViewChange( shards ) );
        }


        public boolean handleConfirmViewChange(HybsterConfirmViewChange confvc)
        {
            return handleConfirmMessage( confvc );
        }


        @Override
        protected boolean confirmMessage(ViewChangeVerificationMessage<HybsterViewChange[]> confmsg)
        {
            HybsterViewChange vc = confmsg.getMessage()[ m_shardno ];

            s_logger.debug( "{} confirm {}", m_cntxt, vc );

            assert vc.isCertificateValid()==null;

            m_cntxt.verifyViewChangeMessage( vc );

            // TODO: Verify checkpoint certificate.

//            long lastorderno = -1;
//
//            // TODO: Method that validates complete certificates.
//            // TODO: Maximum size for the set?
//            // TODO: We would need the complete proposal if we proposed hashes.
//            HybsterXOrderInstanceCertifier cert =
//                    new HybsterXOrderInstanceCertifier( m_locrepno, true, m_mapper, m_defverif, m_cliverif );
//
//            for( OrderNetworkMessage[] pp : prepset )
//            {
//                // TODO: Real threshold
//                //if( pp.length!=<threshold> )
//                // TODO: Currently, the first message has to be the PREPREPARE
//                long iorderno = pp[ 0 ].getOrderNumber();
//                int  iviewno  = pp[ 0 ].getViewNumber();
//
//                if( iviewno>=viewno )
//                    throw new UnsupportedOperationException();
//
//                // TODO: Do we know which instances can be contained? (Currently, yes.)
//                if( iorderno<=lastorderno )
//                    throw new UnsupportedOperationException();
//                else
//                    lastorderno = iorderno;
//
//                cert.init( iorderno, iviewno  );
//
//                for( OrderNetworkMessage m : pp )
//                {
//                    switch( m.getTypeID() )
//                    {
//                    case HybsterXOrderMessages.HYBSTERX_PREPARE_ID:
//                        cert.addPrepare( (HybsterXPrepare) m );
//                        break;
//                    case HybsterXOrderMessages.HYBSTERX_COMMIT_ID:
//                        cert.addCommit( (HybsterXCommit) m );
//                        break;
//                    default:
//                        throw new UnsupportedOperationException();
//                    }
//
//                    if( cert.isPrepared() || cert.isCommitted() )
//                        break;
//                }
//
//                if( !cert.isPrepared() && !cert.isCommitted() )
//                    throw new VerificationException();
//            }

            vc.setInnerMessagesValid( true );

            return notifyMessageShardConfirmed( new HybsterViewChangeShardConfirmed( vc ) );
        }


        public boolean handleViewChangeConfirmed(HybsterViewChangeShardConfirmed vcconf)
        {
            return handleMessageShardConfirmed( vcconf );
        }


        @Override
        protected boolean messageConfirmed(ViewChangeContentCertifier<HybsterViewChange> certif)
        {
//            m_vcballot.add( certif.getReplicaNumber(), certif.getViewNumber(), (HybsterXViewChangeCertifier) certif );
//
//            State nextstate;
//
//            if( m_certifs[ m_locrepno ].isConfirmed() && m_vcballot.getDecision()!=null )
//                nextstate = State.VIEW_CHANGE_AGREED;
//            // TODO: This is not sufficient. Confirmed is the f+1th highest view number of all received view changes.
//            else if( m_vcballot.size()>=m_weakquorumsize )
//                nextstate = State.VIEW_CHANGE_CONFIRMED;
//            else
//                nextstate = State.VIEW_CHANGE_INITIATED;
//
//            // TODO: multiple new views?
//            if( nextstate==m_state )
//                return false;
//            else if( nextstate==State.VIEW_CHANGE_AGREED &&
//                     m_groupview.getCoordinator( m_vcballot.getDecision() )==m_locrepno &&
//                     m_vcballot.getDecision()>m_curviewno &&
//                     // TODO: We would have to force the creation of an own view change if we haven't done it so far.
//                     m_vcballot.hasVoted( m_locrepno ) )
//                return notifyResult( createNewViewReady() );
//            else
//            {
//                m_state = nextstate;
//                return true;
//            }
            throw new NotImplementedException();
        }


        public boolean handleNewViewReady(HybsterNewViewReady nvred)
        {
            assert m_curviewno<nvred.getViewNumber() || m_isintcoord && m_curviewno==nvred.getViewNumber();
            assert m_nvready==null;

            s_logger.debug( "{} new view ready {}", m_cntxt, nvred.getViewNumber() );

            m_curviewno = nvred.getViewNumber();
            m_nvready   = nvred;

            m_state = State.NEW_VIEW_READY;
            return true;
        }


//        private HybsterXNewViewReady createNewViewReady()
//        {
//            // TODO: NewViewCertificate
//            int  viewno = m_vcballot.getDecision();
//            long mins   = 0;
//            long maxs   = 0;
//
//            HybsterXViewChange[][] nvproof = new HybsterXViewChange[ m_strongquorumsize ][];
//
//            Iterator<HybsterXViewChangeCertifier> iter = m_vcballot.getDecidingBallots().iterator();
//            for( int i=0; i<nvproof.length; i++ )
//            {
//                nvproof[ i ] = iter.next().getMessages().toArray( new HybsterXViewChange[ 0 ] );
//
//                for( int k=0; k<nvproof[ i ].length; k++ )
//                {
//                    HybsterXViewChange vc = nvproof[ i ][ k ];
//
//                    CheckpointCertificate stablechkpt = vc.getCheckpointCertificate();
//                    if( stablechkpt!=null && stablechkpt.getOrderNumber()>mins )
//                        mins = stablechkpt.getOrderNumber();
//
//                    OrderNetworkMessage[] pc = vc.getPrepareMessages();
//                    if( pc.length>0 && pc[ pc.length-1 ].getOrderNumber()>maxs )
//                        maxs = pc[ pc.length-1 ].getOrderNumber();
//                }
//            }
//
//            if( maxs==0 )
//                maxs = mins;
//
//            s_logger.debug( "{} new view {}, ready with {}-{}", m_cntxt, viewno, mins, maxs );
//
//            return new HybsterXNewViewReady( viewno, nvproof, mins, maxs );
//        }


//        @Override
//        protected void initCertifier(ViewChangeContentCertifier<HybsterXViewChange> certif, int viewno)
//        {
//            if( certif.isConfirmed() )
//                m_vcballot.remove( certif.getReplicaNumber() );
//
//            super.initCertifier( certif, viewno );
//        }


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

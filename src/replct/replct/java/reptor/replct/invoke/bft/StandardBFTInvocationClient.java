package reptor.replct.invoke.bft;

import java.net.InetSocketAddress;
import java.util.Random;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import reptor.chronos.context.SchedulerContext;
import reptor.chronos.link.MulticastLink;
import reptor.distrbt.certify.Certifier;
import reptor.distrbt.certify.GroupConnectionCertifier;
import reptor.distrbt.certify.suites.AuthorityInstances;
import reptor.distrbt.com.MessageMapper;
import reptor.distrbt.com.NetworkMessage;
import reptor.distrbt.com.connect.NetworkGroupConnection;
import reptor.distrbt.domains.SelectorDomainContext;
import reptor.jlib.collect.FixedSlidingWindow;
import reptor.replct.ReplicaGroup;
import reptor.replct.connect.Handshaking;
import reptor.replct.connect.StandardHandshakeState;
import reptor.replct.invoke.InvocationMessages.Reply;
import reptor.replct.invoke.InvocationMessages.Request;
import reptor.replct.service.ServiceCommand;


public class StandardBFTInvocationClient extends BFTInvocationClient
{

    public StandardBFTInvocationClient(BFTInvocation invoke, Handshaking<?> handshake, boolean summarize)
    {
        super( invoke, handshake, summarize );
    }

    @Override
    public BFTClientHandler createInvocationHandler(SchedulerContext<? extends SelectorDomainContext> master,
            short clino, byte contact, short remshardno, int[] remaddrnos, AuthorityInstances authinsts,
            BFTClientProvider invprov, ProphecySketcher sketcher)
    {
        return new StandardBFTClientHandler( master, clino, contact, remshardno, remaddrnos, authinsts, invprov, sketcher );
    }


    //-------------------------------------//
    //               Handler               //
    //-------------------------------------//

    public static class StandardBFTClientHandler extends BFTClientHandler
    {
        private final GroupConnectionCertifier                        m_repcertif;
        private final NetworkGroupConnection                          m_repconn;
        private final FixedSlidingWindow<StandardBFTClientInstance>   m_invwnd;

        private long        m_contactinv = BFTInvocation.NO_INVOCATION;
        private int         m_nconns     = 0;

        public StandardBFTClientHandler(SchedulerContext<? extends SelectorDomainContext> master, short clino, byte contact,
                                        short remshardno, int[] remaddrnos, AuthorityInstances authinsts,
                                        BFTClientProvider invprov, ProphecySketcher sketcher)
        {
            super( master, clino, contact, invprov.getInvocationClient() );

            StandardBFTInvocation invoke = (StandardBFTInvocation) invprov.getInvocationClient().getInvocation();

            m_repcertif = invoke.createClientToReplicaCertifier( clino, remshardno, contact, authinsts );
            m_repconn   = invprov.createReplicaConnection( this, clino );

            m_invwnd = new FixedSlidingWindow<>( StandardBFTClientInstance.class, invoke.getInvocationWindowSize(),
                                slotno -> new StandardBFTClientInstance( clino, slotno, m_repcertif, m_repconn, invprov, sketcher), 0 );

            connect( clino, invoke.getReplicaGroup(), remaddrnos );
        }

        @Override
        protected FixedSlidingWindow<StandardBFTClientInstance> invocations()
        {
            return m_invwnd;
        }

        private void connect(short clino, ReplicaGroup repgroup, int[] remaddrnos)
        {
            Random rnd = new Random( clino );

            for( byte repno=0; repno<repgroup.size(); repno++ )
            {
                int addrno = remaddrnos[ Math.abs( rnd.nextInt() ) % remaddrnos.length ];
                InetSocketAddress addr = repgroup.getReplica( repno ).getAddressForClients( addrno );

                startConnection( repno, addr );
            }
        }

        @Override
        protected void processNewConnection(StandardHandshakeState hsstate)
        {
            assert isReady();

            m_nconns++;
            m_repconn.getConnection( hsstate.getRemoteEndpoint().getProcessNumber() ).open( hsstate );
        }

        @Override
        public boolean isConnected()
        {
            return m_nconns==m_repconn.size();
        }

         @Override
        protected boolean isConnectionReady()
        {
            return m_repconn.isReady();
        }

        @Override
        protected void processConnection()
        {
            m_repconn.execute();
        }

        @Override
        protected void invocationCompleted(BFTClientInstance inv)
        {
            long invno = inv.getInvocationNumber();

            if( m_contact!=inv.getCurrentContactReplica() && invno>m_contactinv )
            {
                m_contact    = inv.getCurrentContactReplica();
                m_contactinv = invno;

                s_logger.debug( "{} new contact replica {}", this, m_contact );
            }
        }
    }


    //-------------------------------------//
    //              Instance               //
    //-------------------------------------//

    public static class StandardBFTClientInstance extends BFTClientInstance
    {
        private static final Logger s_logger = LoggerFactory.getLogger( StandardBFTClientInstance.class );

        private final MessageMapper                         m_mapper;
        private final MulticastLink<? super NetworkMessage> m_repconn;
        private final Certifier                             m_repcertif;
        private final boolean                               m_rooptenabled;
        private final BFTReplyPhaseClient                   m_replies;

        private final int                                   m_readqs;
        private final int                                   m_writeqs;

        private Request     m_request  = null;
        private boolean     m_useroopt = false;

        private ProphecySketcher sketcher = null;
        private boolean isProphecy     = false;
        private boolean fastread       = false;
        private short   contact        = -1;

        public StandardBFTClientInstance(short clino, int slotno,
                                         GroupConnectionCertifier repcertif,
                                         MulticastLink<? super NetworkMessage> repconn,
                                         BFTClientProvider invprov, ProphecySketcher sketcher)
        {
            super( clino, slotno );

            BFTInvocation invoke   = invprov.getInvocationClient().getInvocation();
            ReplicaGroup  repgroup = invoke.getReplicaGroup();

            m_mapper       = invprov.getMessageMapper();
            m_repconn      = repconn;
            m_repcertif    = repcertif.getCertifier();
            m_rooptenabled = invoke.getUseReadOnlyOptimization();

            m_replies = new BFTReplyPhaseClient( m_mapper, repgroup, repcertif, invoke );

            short nreplicas = repgroup.size();
            short nfaults   = repgroup.getNumberOfTolerableFaults();
            m_readqs  = invoke.readReplyQuorumSize( nreplicas, nfaults );
            m_writeqs = invoke.writeReplyQuorumSize( nreplicas, nfaults );

            isProphecy     = invoke.isProphecy();
            this.sketcher = sketcher;
        }

        @Override
        public void init(long invno, short contactno, ServiceCommand command, boolean resent)
        {
            m_request  = null;
            if (!resent)
                m_useroopt = m_rooptenabled && command.isReadOnly();
            else
                m_useroopt = false;
            fastread   = false;
            contact    = contactno;
            // Remove barrier
            initInvocation( invno, contactno, command, false );

            s_logger.debug( "{} init invocation (ro {})", this, m_useroopt );
        }

        @Override
        public void startInvocation()
        {
            s_logger.debug( "{} start invocation (ro {})", this, m_useroopt );

            m_request = createCertifiedRequest( m_useroopt, false );

            if( isProphecy && m_useroopt )
            {
                if (sketcher.hasRequest(m_request.getCommand()))
                {
                    // Prophecy fast-read
                    fastread = true;
                    m_replies.initInvocation( m_readqs );
                    Random rand = new Random();
                    short r = (short) rand.nextInt(m_repconn.size());
                    m_repconn.enqueueUnicast( r, m_request );
                }
                else
                {
                    // regular process
                    m_replies.initInvocation( m_readqs );
                    m_request = createCertifiedRequest(false, false);
                    m_repconn.enqueueUnicast( initialContact(), m_request );
                }
            }
            else if ( m_useroopt )
            {
                m_replies.initInvocation( m_readqs );
                m_repconn.enqueueMessage( m_request );
            }
            else
            {
                m_replies.initInvocation( m_writeqs );
                m_repconn.enqueueUnicast( initialContact(), m_request );
            }

            commandRequested();
        }

        private Request createCertifiedRequest(boolean useroopt, boolean ispanic)
        {
            Request request = createRequest( useroopt, ispanic );

            m_mapper.certifyAndSerializeMessage( request, m_repcertif );

            return request;
        }

        @Override
        public boolean handleReply(Reply reply)
        {
            if( reply.wasExecutedSpeculatively() && !m_useroopt )
                return false;   // outdated speculative reply
            else if ( fastread ) // use fast-read
            {
                if (sketcher.compare(m_request.getCommand(), reply.getPayload()))
                {
                    resultStable( reply.getResult() );
                    return false;
                }
                else
                {
                    fastread = false;
//                    // The (external) barrier has changed, therefore return true.
                    return true;
                }
            }
            else if( !m_replies.handleReply( reply ) )
                return false;
            else if( m_replies.getResult()==null )
            {
                // The (external) barrier has changed, therefore return true.
                return true;
            }
            else
            {
                s_logger.debug( "{} invocation complete (ro {})", this, m_useroopt );

                if ( isProphecy && m_useroopt )
                    sketcher.update(m_request.getCommand(), reply.getPayload());

                resultStable( m_replies.getResult() );
                return true;
            }
        }

        @Override
        public byte getCurrentContactReplica()
        {
            assert isStable();

            if (fastread)
                return (byte) contact;

            return m_replies.getCurrentContactReplica();
        }

    }

}

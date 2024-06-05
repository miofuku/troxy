package reptor.tbft.invoke;

import java.net.InetSocketAddress;
import java.util.Random;

import reptor.chronos.context.SchedulerContext;
import reptor.chronos.link.MulticastLink;
import reptor.distrbt.certify.suites.AuthorityInstances;
import reptor.distrbt.com.MessageMapper;
import reptor.distrbt.com.NetworkMessage;
import reptor.distrbt.com.connect.NetworkGroupConnection;
import reptor.distrbt.common.data.ImmutableData;
import reptor.distrbt.domains.SelectorDomainContext;
import reptor.jlib.collect.FixedSlidingWindow;
import reptor.replct.RemoteReplica;
import reptor.replct.connect.Handshaking;
import reptor.replct.connect.StandardHandshakeState;
import reptor.replct.invoke.InvocationMessages.Reply;
import reptor.replct.invoke.InvocationMessages.Request;
import reptor.replct.invoke.bft.BFTClientHandler;
import reptor.replct.invoke.bft.BFTClientInstance;
import reptor.replct.invoke.bft.BFTClientProvider;
import reptor.replct.invoke.bft.BFTInvocation;
import reptor.replct.invoke.bft.BFTInvocationClient;
import reptor.replct.invoke.bft.ProphecySketcher;
import reptor.replct.service.ServiceCommand;


public class TransBFTInvocationClient extends BFTInvocationClient
{

    public TransBFTInvocationClient(BFTInvocation invoke, Handshaking<?> handshake, boolean summarize)
    {
        super( invoke, handshake, summarize );
    }


    @Override
    public BFTClientHandler createInvocationHandler(SchedulerContext<? extends SelectorDomainContext> master,
                                                    short clino, byte contact, short remshardno, int[] remaddrnos, AuthorityInstances authinsts,
                                                    BFTClientProvider invprov, ProphecySketcher sketcher)
    {
        return new TransBFTClientHandler( master, clino, contact, remshardno, remaddrnos, authinsts, invprov );
    }


    //-------------------------------------//
    //               Handler               //
    //-------------------------------------//


    public static class TransBFTClientHandler extends BFTClientHandler
    {
        private final NetworkGroupConnection                      m_repconn;
        private final FixedSlidingWindow<TransBFTClientInstance>  m_invwnd;

        private int         m_nconns = 0;

        public TransBFTClientHandler(SchedulerContext<? extends SelectorDomainContext> master, short clino, byte contact,
                                     short remshardno, int[] remaddrnos, AuthorityInstances authinsts,
                                     BFTClientProvider invprov)
        {
            super( master, clino, contact, invprov.getInvocationClient() );

            TransBFTInvocation invoke = (TransBFTInvocation) invprov.getInvocationClient().getInvocation();

            m_repconn = invprov.createReplicaConnection( this, clino );

            m_invwnd = new FixedSlidingWindow<>( TransBFTClientInstance.class, invoke.getInvocationWindowSize(),
                                slotno -> new TransBFTClientInstance( clino, slotno, m_repconn, invprov ), 0 );

            connect( clino, invoke.getReplicaGroup().getReplica( contact ), remaddrnos );
        }

        @Override
        protected FixedSlidingWindow<TransBFTClientInstance> invocations()
        {
            return m_invwnd;
        }

        private void connect(short clino, RemoteReplica contact, int[] remaddrnos)
        {
            int addrno = remaddrnos[ Math.abs( new Random( clino ).nextInt() ) % remaddrnos.length ];

            InetSocketAddress addr = contact.getAddressForClients( addrno );

            startConnection( contact.getReplicaNumber(), addr );
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
            return m_nconns==1;
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
        }
    }


    //-------------------------------------//
    //              Instance               //
    //-------------------------------------//

    public static class TransBFTClientInstance extends BFTClientInstance
    {
        private final MessageMapper                          m_mapper;
        private final MulticastLink<? super NetworkMessage>  m_repconn;
        private final boolean                                m_rooptenabled;

        private Reply       m_reply   = null;
        private boolean     m_useroopt = false;

        public TransBFTClientInstance(short clino, int slotno,
                                      MulticastLink<? super NetworkMessage> repconn,
                                      BFTClientProvider invprov)
        {
            super( clino, slotno );
            BFTInvocation invoke   = invprov.getInvocationClient().getInvocation();

            m_mapper    = invprov.getMessageMapper();
            m_repconn   = repconn;
            m_rooptenabled = invoke.getUseReadOnlyOptimization();
        }

        @Override
        public void init(long invno, short contactno, ServiceCommand command, boolean resent)
        {
            m_reply = null;
            m_useroopt = m_rooptenabled && command.isReadOnly();

            // Remove barrier
            initInvocation( invno, contactno, command, false );
        }

        @Override
        public void startInvocation()
        {
            Request request = createRequest( m_useroopt, false );

            m_mapper.serializeMessage( request );

            m_repconn.enqueueUnicast( initialContact(), request );

            commandRequested();
        }

        @Override
        public boolean handleReply(Reply reply)
        {
            if ( isStable() )
                return false;

            reply.setInnerMessagesValid( true );

            m_reply = reply;

            resultStable( reply.getResult() );
            return true;
        }

        @Override
        public byte getCurrentContactReplica()
        {
            assert isStable();

            return m_reply.getContactReplica();
        }
    }

}

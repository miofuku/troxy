package reptor.tbft.invoke;

import java.util.Arrays;

import com.google.common.base.Preconditions;

import reptor.chronos.com.PushMessageSink;
import reptor.chronos.context.SchedulerContext;
import reptor.chronos.link.MulticastLink;
import reptor.distrbt.certify.suites.AuthorityInstances;
import reptor.distrbt.com.Message;
import reptor.distrbt.com.MessageMapper;
import reptor.distrbt.com.MessageVerifier;
import reptor.distrbt.com.NetworkMessage;
import reptor.distrbt.com.connect.AbstractNetworkTransmissionConnectionConfiguration;
import reptor.distrbt.com.connect.PushNetworkTransmissionConnection;
import reptor.distrbt.com.connect.StandardNetworkConnection;
import reptor.distrbt.domains.SelectorDomainContext;
import reptor.distrbt.io.net.BufferedNetwork;
import reptor.replct.ReplicaPeerGroup;
import reptor.replct.agree.order.OrderMessages.Command;
import reptor.replct.invoke.InvocationMessages;
import reptor.replct.invoke.InvocationMessages.Reply;
import reptor.replct.invoke.InvocationMessages.Request;
import reptor.replct.invoke.InvocationMessages.RequestExecuted;
import reptor.replct.invoke.InvocationReplicaHandler;
import reptor.replct.invoke.InvocationReplicaProvider;
import reptor.replct.invoke.ReplyMode;
import reptor.replct.invoke.bft.BFTInvocationReplica;
import reptor.replct.invoke.bft.BFTReplicaHandler;
import reptor.replct.invoke.bft.BFTReplicaInstance;
import reptor.replct.invoke.bft.BFTReplicaProvider;
import reptor.tbft.Troxy;
import reptor.tbft.TroxyClientResults;
import reptor.tbft.adapt.TroxyClientHandler;
import reptor.tbft.adapt.TroxyHandshaking.TroxyHandshakingProcess;
import reptor.tbft.adapt.TroxyProposalVerifier;


public class TransBFTInvocationReplica extends BFTInvocationReplica
{

    private final TransBFTInvocation    m_invoke;
    private Troxy                       m_troxy;
    private TroxyHandshakingProcess     m_handshake;

    private static boolean              useCache;

    public TransBFTInvocationReplica(TransBFTInvocation invoke, ReplicaPeerGroup repgroup)
    {
        super( repgroup );

        m_invoke = invoke;

        useCache = m_invoke.isUseCache();
    }

    @Override
    public TransBFTInvocation getInvocation()
    {
        return m_invoke;
    }

    public TransBFTInvocationReplica troxy(Troxy troxy)
    {
        Preconditions.checkState( !m_isactive );

        m_troxy = troxy;

        return this;
    }

    public Troxy getTroxy()
    {
        return m_troxy;
    }

    @Override
    public BFTInvocationReplica activate()
    {
        super.activate();

        Preconditions.checkState( m_troxy!=null );

        m_handshake = m_invoke.getReplicaHandshake().createAcceptorProcess( m_repgroup.getReplicaNumber() );
        m_handshake.setTroxy( m_troxy );

        return this;
    }

    @Override
    public TroxyHandshakingProcess getHandshake()
    {
        return m_handshake;
    }

    @Override
    public MessageVerifier<? super Command> createProposalVerifier(short ordershard, AuthorityInstances authinsts, MessageMapper mapper)
    {
        return new TroxyProposalVerifier( m_troxy, ordershard, m_invoke.getOrdering().getMaximumCommandBatchSize() );
    }

    @Override
    public InvocationReplicaProvider createInvocationProvider(short clintshard, MessageMapper mapper,
                                                              MulticastLink<? super NetworkMessage> repconn)
    {
        return new TransBFTReplicaProvider( this, clintshard, mapper, repconn );
    }


    //-------------------------------------//
    //              Provider               //
    //-------------------------------------//

    public static class TransBFTReplicaProvider extends BFTReplicaProvider
    {
        public TransBFTReplicaProvider(TransBFTInvocationReplica invrep, short clintshard, MessageMapper mapper,
                                       MulticastLink<? super NetworkMessage> repconn)
        {
            super( invrep, clintshard, mapper, repconn );
        }

        @Override
        public InvocationReplicaHandler createHandler(
                SchedulerContext<? extends SelectorDomainContext> master, short clintshard, int wrkno)
        {
            assert clintshard==m_clintshard;

            short clino = clientNumber( clintshard, wrkno );

            // Create connection
            AbstractNetworkTransmissionConnectionConfiguration connconf =
                    (AbstractNetworkTransmissionConnectionConfiguration) m_invrep.getClientConnectionConfiguration();

            int sendbufsize = connconf.getNetworkTransmitBufferSize();
            int recvbufsize = connconf.getNetworkReceiveBufferSize();

            BufferedNetwork net = new BufferedNetwork( master.getDomainContext(), connconf.getConnectionObserver(),
                                                       recvbufsize, sendbufsize, connconf::networkBuffer );

            TransBFTInvocationReplica tinvrep = (TransBFTInvocationReplica) m_invrep;
            Troxy              troxy = tinvrep.getTroxy();
            TroxyClientHandler tcli  = new TroxyClientHandler( troxy, clino, m_mapper, tinvrep.getInvocation().createClientHandlerOutboundBuffer() );

            StandardNetworkConnection cliconn = new StandardNetworkConnection( clino, connconf::configureChannel, net, tcli );

            // Create slots
            BFTReplicaInstance[] slots = new BFTReplicaInstance[ m_invrep.getInvocation().getInvocationWindowSize() ];
            Arrays.setAll( slots, slotno -> new TransBFTReplicaInstance( clintshard, clino, slotno, tcli, cliconn.getOutbound(), this ) );

            byte repno = m_invrep.getReplicaGroup().getReplicaNumber();

            return new TransBFTReplicaHandler( master, clintshard, clino, repno, tcli, cliconn, slots );
        }
    }


    //-------------------------------------//
    //              Handler                //
    //-------------------------------------//

    public static class TransBFTReplicaHandler extends BFTReplicaHandler
    {
        private final byte               m_repno;
        private final TroxyClientHandler m_tcli;

        private byte    m_contact = -1;

        public TransBFTReplicaHandler(SchedulerContext<? extends SelectorDomainContext> master, short clintshard, short clino,
                                      byte repno, TroxyClientHandler tcli,
                                      PushNetworkTransmissionConnection cliconn, BFTReplicaInstance[] slots)
        {
            super( master, clintshard, clino, cliconn, slots );

            m_repno = repno;
            m_tcli  = tcli;
        }

        @Override
        public void initContact(byte contact)
        {
            super.initContact( contact );

            m_contact = contact;
        }

        // TODO: PULL and remove.
        @Override
        public void enqueueMessage(Message msg)
        {
            if( msg.getTypeID()==InvocationMessages.REQUEST_ID )
                enqueueRequest( (Request) msg );
            else
                enqueueReply( (Reply) msg );
        }

        @Override
        protected void verifyRequest(Request request)
        {
            if( m_repno==m_contact )
                assert request.isCertificateValid()==Boolean.TRUE;
            else
            {
                assert request.isCertificateValid()==null;

                m_tcli.processForwardedRequest( request );
            }
        }
    }


    //-------------------------------------//
    //              Instance               //
    //-------------------------------------//

    public static class TransBFTReplicaInstance extends BFTReplicaInstance
    {
        private final TroxyClientHandler    m_tcli;

        public TransBFTReplicaInstance(short shardno, short clino, int slotno, TroxyClientHandler tcli,
                                       PushMessageSink<NetworkMessage> cliconn, TransBFTReplicaProvider invprov)
        {
            super( shardno, clino, slotno, cliconn, invprov );

            m_tcli = tcli;
        }

        @Override
        public boolean handleRequest(Request request)
        {
            if (request.useReadOnlyOptimization() && m_repno==m_contact)
            {
                if (useCache)
                    forwardForFastRead(request);
                else
                    forwardToOthers(request);
            }


            checkRequest( request );
            storeRequest( request );
            advanceState( State.REQUESTED );

            return true;
        }

        @Override
        public void handleCommandExecuted(RequestExecuted reqexecd)
        {
            storeResult( reqexecd );

            if( m_state==State.FINISHED )
                return;

            if( reqexecd.getReplyMode()==ReplyMode.None && m_repno!=m_contact )
                advanceState( State.FINISHED );
            else
            {
                TroxyClientResults result = m_tcli.processRequestExecuted( reqexecd );

                if( result.getLastFinishedInvocation()==m_invno )
                    advanceState( reqexecd.wasExecutedSpeculatively() ? State.EXECUTED_SPECULATIVELY : State.FINISHED );
            }
        }

        @Override
        public void handleReply(Reply reply)
        {
            if( m_state==State.FINISHED )
                return;

            if( m_repno==m_contact )
            {
                assert reply.getSender()!=m_repno;

                TroxyClientResults result = m_tcli.processReply( reply );

                if( result.getLastFinishedInvocation()==m_invno )
                    advanceState( State.FINISHED );
            }
            else
            {
                assert reply.getSender()==m_repno;

                storeReply( reply );
                forwardReplyToContact( reply );
            }
        }
    }

}

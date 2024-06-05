package reptor.replct.invoke.bft;

import java.util.Arrays;

import reptor.chronos.com.PushMessageSink;
import reptor.chronos.context.SchedulerContext;
import reptor.chronos.link.MulticastLink;
import reptor.distrbt.certify.CertificationProvider;
import reptor.distrbt.certify.ConnectionCertifier;
import reptor.distrbt.certify.Verifier;
import reptor.distrbt.certify.suites.AuthorityInstances;
import reptor.distrbt.certify.suites.ConnectionKeys;
import reptor.distrbt.com.MessageMapper;
import reptor.distrbt.com.MessageVerifier;
import reptor.distrbt.com.NetworkMessage;
import reptor.distrbt.com.VerificationException;
import reptor.distrbt.com.connect.NetworkConnectionProvider;
import reptor.distrbt.com.connect.PushNetworkTransmissionConnection;
import reptor.distrbt.com.map.MappedNetworkMessageVerifier;
import reptor.distrbt.domains.SelectorDomainContext;
import reptor.replct.ReplicaPeerGroup;
import reptor.replct.agree.order.OrderMessages.Command;
import reptor.replct.connect.HandshakingProcess;
import reptor.replct.invoke.InvocationMessages.Reply;
import reptor.replct.invoke.InvocationMessages.Request;
import reptor.replct.invoke.InvocationMessages.RequestExecuted;
import reptor.replct.invoke.InvocationReplicaHandler;
import reptor.replct.invoke.InvocationReplicaProvider;
import reptor.replct.secure.Cryptography;


public class StandardBFTInvocationReplica extends BFTInvocationReplica
{
    private final StandardBFTInvocation m_invoke;
    private final HandshakingProcess<?> m_handshake;

    public StandardBFTInvocationReplica(StandardBFTInvocation invoke, ReplicaPeerGroup repgroup)
    {
        super( repgroup );

        m_invoke    = invoke;
        m_handshake = invoke.getConnections().getHandshakeForReplicas().createProcess( repgroup.getReplicaNumber() );
    }

    @Override
    public StandardBFTInvocation getInvocation()
    {
        return m_invoke;
    }


    @Override
    public HandshakingProcess<?> getHandshake()
    {
        return m_handshake;
    }


    @Override
    public MessageVerifier<? super Command> createProposalVerifier(short ordershard, AuthorityInstances authinsts, MessageMapper mapper)
    {
        Cryptography crypto    = m_invoke.getCryptography();
        byte         repno     = m_repgroup.getReplicaNumber();
        byte         nreplicas = m_repgroup.size();

        Verifier[] propsverifs = new Verifier[ nreplicas+crypto.getNumberOfClients() ];

        CertificationProvider<? super ConnectionKeys> clicerts =
                m_invoke.getReplicaToClientCertification().createCertificationProvider( authinsts );

        for( short clino=nreplicas; clino<propsverifs.length; clino++ )
            propsverifs[ clino ] = clicerts.createNtoICertifier( repno, nreplicas, crypto.getConnectionKeys( repno, ordershard, clino ) ).getVerifier();

        return new MappedNetworkMessageVerifier<>( mapper, propsverifs );
    }

    @Override
    public InvocationReplicaProvider createInvocationProvider(short clintshard, MessageMapper mapper,
                                                              MulticastLink<? super NetworkMessage> repconn)
    {
        return new StandardBFTReplicaProvider( this, clintshard, mapper, repconn );
    }


    //-------------------------------------//
    //              Provider               //
    //-------------------------------------//

    public static class StandardBFTReplicaProvider extends BFTReplicaProvider
    {
        private final CertificationProvider<? super ConnectionKeys> m_clicerts;
        private final NetworkConnectionProvider                     m_cliconns;

        public StandardBFTReplicaProvider(StandardBFTInvocationReplica invrep, short clintshard, MessageMapper mapper,
                                          MulticastLink<? super NetworkMessage> repconn)
        {
            super( invrep, clintshard, mapper, repconn );

            StandardBFTInvocation invoke = invrep.getInvocation();
            byte repno = invrep.getReplicaGroup().getReplicaNumber();

            m_clicerts = invoke.getReplicaToClientCertification()
                               .createCertificationProvider( invoke.createAuthorityForClientShard( repno, clintshard ) );

            m_cliconns = invrep.getClientConnectionConfiguration().connectionProvider( m_mapper );
        }

        @Override
        public InvocationReplicaHandler createHandler(
                SchedulerContext<? extends SelectorDomainContext> master, short clintshard, int wrkno)
        {
            assert clintshard==m_clintshard;

            short clino = clientNumber( clintshard, wrkno );

            ConnectionCertifier clicertif =
                    m_invrep.getInvocation().createReplicaToClientCertifier( m_invrep.getReplicaGroup(), clintshard, clino, m_clicerts );

            PushNetworkTransmissionConnection cliconn =
                    m_cliconns.connection( master.getDomainContext(), clino, m_invrep.getInvocationExtensions().getRequestContextFactory()::createRequestContext );

            BFTReplicaInstance[] slots = new BFTReplicaInstance[ m_invrep.getInvocation().getInvocationWindowSize() ];
            Arrays.setAll( slots, slotno -> new StandardBFTReplicaInstance( clintshard, clino, slotno, clicertif, cliconn.getOutbound(), this ) );

            return new StandardBFTReplicaHandler( master, clintshard, clino, cliconn, slots, m_mapper, clicertif );
        }
    }


    //-------------------------------------//
    //              Handler                //
    //-------------------------------------//

    public static class StandardBFTReplicaHandler extends BFTReplicaHandler
    {
        private final MessageMapper         m_mapper;
        private final ConnectionCertifier   m_clicertif;

        public StandardBFTReplicaHandler(SchedulerContext<? extends SelectorDomainContext> master, short clintshard, short clino,
                                         PushNetworkTransmissionConnection cliconn, BFTReplicaInstance[] slots,
                                         MessageMapper mapper, ConnectionCertifier clicertif)
        {
            super( master, clintshard, clino, cliconn, slots );

            m_mapper    = mapper;
            m_clicertif = clicertif;
        }

        @Override
        protected void verifyRequest(Request request) throws VerificationException
        {
            m_mapper.verifyMessage( request, m_clicertif.getVerifier() );
        }
    }


    //-------------------------------------//
    //              Instance               //
    //-------------------------------------//

    public static class StandardBFTReplicaInstance extends BFTReplicaInstance
    {
        private final boolean               m_routeovercontact;
        private final ConnectionCertifier   m_clicertif;

        public StandardBFTReplicaInstance(short shardno, short clino, int slotno,
                                          ConnectionCertifier clicertif, PushMessageSink<NetworkMessage> cliconn,
                                          BFTReplicaProvider invprov)
        {
            super( shardno, clino, slotno, cliconn, invprov );

            m_clicertif        = clicertif;
            m_routeovercontact = invprov.getInvocationReplica().getInvocation().getRouteRepliesOverContact();
        }

        @Override
        public boolean handleRequest(Request request)
        {
            checkRequest( request );

            if( m_request==null )
            {
                invocationStarted( request );

                storeRequest( request );

                advanceState( State.REQUESTED );
            }
            else
            {
                storeRequest( request );
            }

            return true;
        }

        @Override
        public void handleCommandExecuted(RequestExecuted reqexecd)
        {
            storeResult( reqexecd );
            advanceState( reqexecd.wasExecutedSpeculatively() ? State.EXECUTED_SPECULATIVELY : State.FINISHED );

            switch( reqexecd.getReplyMode() )
            {
            case None:
                break;
            case Full:
                replyReady( createReply( true, reqexecd.wasExecutedSpeculatively(), m_result ) );
                break;
            case Hashed:
                replyReady( createReply( false, reqexecd.wasExecutedSpeculatively(), m_mapper.digestData( m_result ) ) );
                break;
            }
        }

        protected void replyReady(Reply reply)
        {
            m_mapper.certifyAndSerializeMessage( reply, m_clicertif.getCertifier() );
            storeReply( reply );

            invocationFinished( reply );

            if( !m_routeovercontact || m_repno==m_contact )
                handleReply( reply );
            else
                forwardReplyToContact( reply );
        }

        @Override
        public void handleReply(Reply reply)
        {
            sendReplyToClient( reply );
        }
    }

}

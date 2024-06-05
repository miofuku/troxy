package refit.pbfto;

import refit.pbfto.suite.PBFT;
import reptor.chronos.message.MulticastChannel;
import reptor.distrbt.certify.GroupConnectionCertifier;
import reptor.distrbt.com.MessageMapper;
import reptor.distrbt.com.NetworkMessage;
import reptor.distrbt.com.NetworkMessageRegistry;
import reptor.replct.ProtocolShard;
import reptor.replct.connect.ConnectionCertifierCollection;


public class PBFTProtocolShard implements ProtocolShard
{

    protected final PBFT                m_repprot;
    protected final short               m_shardno;

    protected final GroupConnectionCertifier    m_defcon;
    protected final GroupConnectionCertifier    m_strcon;
    protected final ConnectionCertifierCollection  m_clicons;
    protected final MessageMapper            m_mapper = null;
    protected final MulticastChannel<? super NetworkMessage>      m_reptrans;


    public PBFTProtocolShard(PBFT repprot, short shardno, MulticastChannel<? super NetworkMessage> reptrans)
    {
        m_repprot  = repprot;
        m_shardno  = shardno;
        m_reptrans = reptrans;

        // TODO: All this could could be shared within one domain. There is actually no need for several
        //       independent orphics.
//        m_mapper  = repprot.getMapperFactory().get();

//        ConnectionFactory confac = repprot.connectionFactory();

        m_defcon  = null; //confac.createStandardReplicaGroupCertifier();
        m_strcon  = null; //confac.createStrongReplicaGroupCertifier();
        m_clicons = null; //confac.createReplicaToClientCertifiers();
    }


    public NetworkMessageRegistry getMessageFactory()
    {
        return null;//m_repprot.getMessageRegistry();
    }

    @Override
    public final short getShardNumber()
    {
        return m_shardno;
    }


    public final MulticastChannel<? super NetworkMessage> getReplicaTransmission()
    {
        return m_reptrans;
    }


    public final MessageMapper getMessageMapper()
    {
        return m_mapper;
    }


    // TODO: Get rid of these.
    public final ConnectionCertifierCollection getClientConnections()
    {
        return m_clicons;
    }


    public final GroupConnectionCertifier getDefaultReplicaConnection()
    {
        return m_defcon;
    }


    public final GroupConnectionCertifier getStrongReplicaConnection()
    {
        return m_strcon;
    }

}

package reptor.replct.replicate.pbft;

import java.io.IOException;
import java.util.Collection;

import reptor.chronos.Orphic;
import reptor.distrbt.certify.CertificationMethod;
import reptor.distrbt.certify.CertificationProvider;
import reptor.distrbt.certify.ConnectionCertifier;
import reptor.distrbt.certify.GroupConnectionCertifier;
import reptor.distrbt.certify.suites.AuthorityInstances;
import reptor.distrbt.certify.suites.ConnectionKeys;
import reptor.distrbt.certify.trusted.Trinx;
import reptor.distrbt.certify.trusted.TrinxImplementation;
import reptor.distrbt.com.MessageMapper;
import reptor.distrbt.com.MessageVerifier;
import reptor.replct.ReplicaPeerGroup;
import reptor.replct.agree.order.OrderMessages.Command;
import reptor.replct.connect.ConnectionCertifierCollection;
import reptor.replct.invoke.bft.BFTInvocationReplica;
import reptor.replct.secure.Cryptography;


public class PbftCertifiers implements Orphic
{

    protected final Cryptography                        m_crypto;
    protected final AuthorityInstances                  m_authinsts;

    protected final MessageVerifier<? super Command>    m_propverif;
    protected final GroupConnectionCertifier            m_stdcertif;
    protected final GroupConnectionCertifier            m_strcertif;
    protected final ConnectionCertifierCollection       m_unicertifs;


    public PbftCertifiers(CertificationMethod<? super AuthorityInstances, ? super ConnectionKeys> strcert,
                          CertificationMethod<? super AuthorityInstances, ? super ConnectionKeys> stdcert,
                          ReplicaPeerGroup repgroup, short shardno, TrinxImplementation tss, int ncounters,
                          MessageMapper mapper, Cryptography crypto, BFTInvocationReplica invrep)
                                      throws IOException
    {
        m_crypto = crypto;

        // Authority instances
        byte repno     = repgroup.getReplicaNumber();
        byte nreplicas = repgroup.size();

        Trinx m_tssinst = tss==null ? null : tss.createTrinx( crypto.getTssID( repno, shardno ), ncounters );
        m_authinsts = crypto.createAuthorityInstances( repno, m_tssinst );

        // Providers
        CertificationProvider<? super ConnectionKeys> strprov, stdprov;
        strprov = strcert.createCertificationProvider( m_authinsts );
        stdprov = stdcert==strcert ? strprov : stdcert.createCertificationProvider( m_authinsts );

        // Replica certifiers
        Collection<ConnectionKeys> repkeys = crypto.getReplicaKeysForReplica( repno, shardno );
        m_stdcertif = stdprov.createNtoNGroupCertifier( repno, repkeys );
        m_strcertif = strprov.createNtoNGroupCertifier( repno, repkeys );

        ConnectionCertifier[] unicertifs = new ConnectionCertifier[ nreplicas ];

        for( short procno=0; procno<nreplicas; procno++ )
            if( procno!=repno )
                unicertifs[ procno ] = stdprov.createUnicastCertifier( crypto.getConnectionKeys( repno, shardno, procno ) );

        m_unicertifs = new ConnectionCertifierCollection( unicertifs );

        m_propverif = invrep.createProposalVerifier( shardno, m_authinsts, mapper );
    }


    public Cryptography getCryptography()
    {
        return m_crypto;
    }


    public AuthorityInstances getAuthorityInstances()
    {
        return m_authinsts;
    }


    public MessageVerifier<? super Command> getProposalVerifier()
    {
        return m_propverif;
    }


    public GroupConnectionCertifier getStandardCertifier()
    {
        return m_stdcertif;
    }


    public GroupConnectionCertifier getStrongCertifier()
    {
        return m_strcertif;
    }


    public ConnectionCertifierCollection getUnicastCertifiers()
    {
        return m_unicertifs;
    }

}

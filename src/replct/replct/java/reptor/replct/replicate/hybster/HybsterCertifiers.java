package reptor.replct.replicate.hybster;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;

import reptor.distrbt.certify.CertificationMethod;
import reptor.distrbt.certify.suites.AuthorityInstances;
import reptor.distrbt.certify.suites.ConnectionKeys;
import reptor.distrbt.certify.trusted.PlainSingleTrustedMacFormat;
import reptor.distrbt.certify.trusted.Trinx;
import reptor.distrbt.certify.trusted.TrinxImplementation;
import reptor.distrbt.certify.trusted.TrustedCertifying;
import reptor.distrbt.certify.trusted.TrustedCounterGroupCertifier;
import reptor.distrbt.certify.trusted.TrustedCounterProvider;
import reptor.distrbt.certify.trusted.TrustedMacCertification;
import reptor.distrbt.com.MessageMapper;
import reptor.replct.ReplicaPeerGroup;
import reptor.replct.invoke.bft.BFTInvocationReplica;
import reptor.replct.replicate.pbft.PbftCertifiers;
import reptor.replct.secure.Cryptography;


public class HybsterCertifiers extends PbftCertifiers
{

    private final TrustedCounterProvider[]       m_ctrprovs;
    private final TrustedCounterGroupCertifier[] m_ctrcertifs;


    public HybsterCertifiers(TrustedMacCertification strcert,
                             CertificationMethod<? super AuthorityInstances, ? super ConnectionKeys> stdcert,
                             ReplicaPeerGroup repgroup, short shardno, TrinxImplementation tss, int ncounters,
                             MessageMapper mapper, Cryptography crypto, BFTInvocationReplica invrep)
                                      throws IOException
    {
        super( strcert, stdcert, repgroup, shardno, tss, ncounters, mapper, crypto, invrep );

        // Providers
        PlainSingleTrustedMacFormat ctrformat =
                new PlainSingleTrustedMacFormat( TrustedCertifying.TCTR_HMAC_SHA256, strcert.getCertificateFormat().getDigestAlgorithm() );
        m_ctrprovs = new TrustedCounterProvider[ ncounters ];

        Trinx tssinst = (Trinx) m_authinsts.getTrustedAuthorityInstance();
        Arrays.setAll( m_ctrprovs, i -> new TrustedCounterProvider( tssinst, ctrformat, i ) );

        // Certifiers
        Collection<ConnectionKeys> repkeys = crypto.getReplicaKeysForReplica( repgroup.getReplicaNumber(), shardno );

        short repno = repgroup.getReplicaNumber();

        m_ctrcertifs = new TrustedCounterGroupCertifier[ ncounters ];
        Arrays.setAll( m_ctrcertifs, i -> m_ctrprovs[ i ].createNtoNGroupCertifier( repno, repkeys ) );
    }


    public TrustedCounterProvider getCounterCertification(int ctrno)
    {
        return m_ctrprovs[ ctrno ];
    }


    public TrustedCounterGroupCertifier[] getCounterCertifiers()
    {
        return m_ctrcertifs;
    }

}

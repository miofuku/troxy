package reptor.test.distrbt.trusted;

import java.nio.ByteBuffer;
import java.util.List;

import reptor.distrbt.certify.CertificationMethod;
import reptor.distrbt.certify.ConnectionCertifier;
import reptor.distrbt.certify.GroupConnectionCertifier;
import reptor.distrbt.certify.Certifier;
import reptor.distrbt.certify.Verifier;
import reptor.distrbt.certify.suites.AuthorityInstances;
import reptor.distrbt.certify.suites.ConnectionKeys;
import reptor.distrbt.common.data.Data;
import reptor.distrbt.common.data.DataBuffer;
import reptor.jlib.hash.HashAlgorithm;
import reptor.test.bench.MultiCoreTestObject;


public class CertificationBenchmark extends MessageBenchmark
{

    private final AuthorityInstances              m_auths;
    private final List<? extends ConnectionKeys>  m_keys;
    private final CertificationMethod<? super AuthorityInstances, ? super ConnectionKeys> m_certmethod;


    public CertificationBenchmark(AuthorityInstances auths, List<? extends ConnectionKeys> keys,
                                  CertificationMethod<? super AuthorityInstances, ? super ConnectionKeys> certmethod)
    {
        m_auths      = auths;
        m_keys       = keys;
        m_certmethod = certmethod;
    }


    @Override
    public MultiCoreTestObject apply(int value)
    {
        Certifier certifier;
        Verifier  verifier;

        if( m_keys.size()==1 )
        {
            ConnectionCertifier concert = m_certmethod.createCertificationProvider( m_auths ).createUnicastCertifier( m_keys.get( 0 ) );
            certifier = concert.getCertifier();
            verifier  = concert.getVerifier();
        }
        else
        {
            GroupConnectionCertifier concert = m_certmethod.createCertificationProvider( m_auths ).createItoNGroupCertifier( m_keys );
            certifier = concert.getCertifier();
            verifier  = concert.getVerifier( 0 );
        }

        Data msgdata = new DataBuffer( getMessageSize() );

        if( getCertificationMode()==CertificationMode.CERTIFY )
        {
            return new Certify( certifier, msgdata, getPreHashing() );
        }
        else
        {
            DataBuffer crtbuf = new DataBuffer( certifier.getCertificateSize() );

            if( getCertificationMode()==CertificationMode.VERIFY )
                certifier.createCertificate( msgdata, crtbuf );

            return new Verify( verifier, msgdata, crtbuf, getPreHashing() );
        }
    }


    private static class Certify extends AbstractCertification
    {
        private final Certifier  m_certifier;
        private final DataBuffer m_crtbuf;

        public Certify(Certifier certifier, Data msgdata, HashAlgorithm prehash)
        {
            super( msgdata, prehash );

            m_certifier = certifier;
            m_crtbuf    = new DataBuffer( certifier.getCertificateSize() );
        }

        @Override
        public void invoke()
        {
            m_certifier.createCertificate( data(), m_crtbuf );
            m_crtbuf.resetSlice();
        }
    }


    private static class Verify extends AbstractCertification
    {
        private final Verifier  m_verifier;
        private final Data             m_crtdata;

        public Verify(Verifier verifier, Data msgdata, Data crtdata, HashAlgorithm prehash)
        {
            super( msgdata, prehash );

            m_verifier = verifier;
            m_crtdata  = crtdata;
        }

        @Override
        public void invoke()
        {
            m_verifier.verifyCertificate( data(), m_crtdata );
        }
    }

}

package reptor.test.distrbt.trusted;

import java.security.MessageDigest;
import java.util.function.IntFunction;

import reptor.distrbt.common.data.Data;
import reptor.distrbt.common.data.ImmutableData;
import reptor.jlib.hash.HashAlgorithm;
import reptor.test.bench.MultiCoreTestObject;


public abstract class MessageBenchmark implements IntFunction<MultiCoreTestObject>
{

    public enum CertificationMode
    {
        CERTIFY,
        VERIFY,
        VERIFY_DUMMY,
        BATCH_CERTIFY
    }

    private CertificationMode m_certmode = CertificationMode.CERTIFY;
    private HashAlgorithm     m_prehash  = null;
    private int               m_msgsize  = 32;


    public void setMessageSize(int msgsize)
    {
        m_msgsize = msgsize;
    }

    public int getMessageSize()
    {
        return m_msgsize;
    }


    public void setPreHashing(HashAlgorithm prehash)
    {
        m_prehash = prehash;
    }

    public HashAlgorithm getPreHashing()
    {
        return m_prehash;
    }


    public void setCertificationMode(CertificationMode certmode)
    {
        m_certmode = certmode;
    }

    public CertificationMode getCertificationMode()
    {
        return m_certmode;
    }


    protected static abstract class AbstractCertification implements MultiCoreTestObject
    {
        private final Data          m_msgdata;
        private final MessageDigest m_digester;

        public AbstractCertification(Data msgdata, HashAlgorithm prehash)
        {
            m_msgdata  = msgdata;
            m_digester = prehash==null ? null : prehash.digester();
        }

        protected Data data()
        {
            if( m_digester==null )
                return m_msgdata;
            else
            {
                m_msgdata.writeTo( m_digester );
                return ImmutableData.wrap( m_digester.digest() );
            }
        }
    }
}

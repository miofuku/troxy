package reptor.distrbt.certify.trusted;

import java.util.Objects;

import com.google.common.base.Preconditions;

import reptor.distrbt.certify.Certifier;
import reptor.distrbt.certify.Certifiying;
import reptor.distrbt.common.data.MutableData;
import reptor.jlib.hash.HashAlgorithm;


public class IntegerCounterFormat implements SingleCounterFormat
{

    private final TrustedAlgorithm m_proofalgo;
    private final HashAlgorithm    m_digalgo;


    public IntegerCounterFormat(TrustedAlgorithm proofalgo, HashAlgorithm digalgo)
    {
        Preconditions.checkArgument( proofalgo.getProofType()==TrustedCertifying.TRUSTED_COUNTER );

        m_proofalgo = Objects.requireNonNull( proofalgo );
        m_digalgo   = digalgo;
    }


    @Override
    public String toString()
    {
        return Certifiying.formatName( m_proofalgo, m_digalgo );
    }


    @Override
    public HashAlgorithm getDigestAlgorithm()
    {
        return m_digalgo;
    }


    @Override
    public int getCertificateSize()
    {
        return m_proofalgo.getMaximumProofSize() + Integer.BYTES;
    }


    @Override
    public int getMaximumProofSize()
    {
        return m_proofalgo.getMaximumProofSize();
    }


    @Override
    public int getProofOffset()
    {
        return 0;
    }


    @Override
    public int getCounterOffset()
    {
        return m_proofalgo.getMaximumProofSize();
    }


    @Override
    public TrustedAlgorithm getProofAlgorithm()
    {
        return m_proofalgo;
    }


    @Override
    public void writeCertificateTo(MutableData out, Certifier certifier, byte[] proofdata, int proofoffset, int proofsize)
    {
        out.readFrom( proofdata, proofoffset, getMaximumProofSize(), 0 );
    }


    @Override
    public boolean equals(Object obj)
    {
        if( obj==this )
            return true;

        if( obj==null || !( obj instanceof IntegerCounterFormat) )
            return false;

        IntegerCounterFormat other = (IntegerCounterFormat) obj;

        return other.m_proofalgo.equals( m_proofalgo ) && Objects.equals( other.m_digalgo, m_digalgo );
    }


    @Override
    public int hashCode()
    {
        return m_digalgo==null ? m_proofalgo.hashCode() : Objects.hash( m_proofalgo, m_digalgo );
    }

}

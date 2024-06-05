package reptor.distrbt.certify;

import java.util.Objects;

import reptor.distrbt.common.data.MutableData;
import reptor.jlib.hash.HashAlgorithm;


public class PlainSingleProofFormat implements SingleProofFormat
{

    private final ProofAlgorithm m_proofalgo;
    private final HashAlgorithm  m_digalgo;


    public PlainSingleProofFormat(ProofAlgorithm proofalgo, HashAlgorithm digalgo)
    {
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
        return m_proofalgo.getMaximumProofSize();
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
    public ProofAlgorithm getProofAlgorithm()
    {
        return m_proofalgo;
    }


    @Override
    public void writeCertificateTo(MutableData out, Certifier certifier, byte[] proofdata, int proofoffset, int proofsize)
    {
        assert proofsize<=getMaximumProofSize();

        out.readFrom( proofdata, proofoffset, proofsize, 0 );

        out.adaptSlice( proofsize );
    }


    @Override
    public boolean equals(Object obj)
    {
        if( obj==this )
            return true;

        if( obj==null || !( obj instanceof PlainSingleProofFormat) )
            return false;

        PlainSingleProofFormat other = (PlainSingleProofFormat) obj;

        return other.m_proofalgo.equals( m_proofalgo ) && Objects.equals( other.m_digalgo, m_digalgo );
    }


    @Override
    public int hashCode()
    {
        return m_digalgo==null ? m_proofalgo.hashCode() : Objects.hash( m_proofalgo, m_digalgo );
    }

}

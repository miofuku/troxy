package reptor.distrbt.certify.signature;

import java.security.KeyFactory;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.util.Objects;

import reptor.distrbt.certify.KeyType;
import reptor.jlib.hash.HashAlgorithm;


public class SignatureType implements KeyType
{

    private final SignatureBaseAlgorithm    m_basealgo;
    private final int                       m_keysize;


    public SignatureType(SignatureBaseAlgorithm basealgo, int keysize)
    {
        m_basealgo = Objects.requireNonNull( basealgo );
        m_keysize  = keysize;
    }


    @Override
    public String toString()
    {
        return Signing.algorithmName( m_basealgo.toString(), Integer.toString( m_keysize ) );
    }


    public int getKeySize()
    {
        return m_keysize;
    }


    public SignatureBaseAlgorithm getBaseAlgorithm()
    {
        return m_basealgo;
    }


    public int getMaximumProofSize()
    {
        return m_basealgo.getMaximumProofSize( m_keysize );
    }


    public GenericSignatureAlgorithm algorithm(HashAlgorithm hashalgo)
    {
        return new GenericSignatureAlgorithm( this, hashalgo );
    }


    public KeyFactory keyFactory()
    {
        return m_basealgo.keyFactory();
    }


    public KeyPairGenerator keyGenerator()
    {
        try
        {
            KeyPairGenerator keygen = KeyPairGenerator.getInstance( m_basealgo.getInternalName() );
            keygen.initialize( m_keysize );

            return keygen;
        }
        catch( NoSuchAlgorithmException e )
        {
            throw new UnsupportedOperationException( e );
        }
    }


    @Override
    public boolean equals(Object obj)
    {
        if( obj==this )
            return true;

        if( obj==null || !( obj instanceof SignatureType ) )
            return false;

        SignatureType other = (SignatureType) obj;

        return other.getBaseAlgorithm().equals( getBaseAlgorithm() ) && other.getKeySize()==getKeySize();
    }


    @Override
    public int hashCode()
    {
        return Objects.hash( getBaseAlgorithm(), getKeySize() );
    }

}

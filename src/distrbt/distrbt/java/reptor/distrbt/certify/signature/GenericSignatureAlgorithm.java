package reptor.distrbt.certify.signature;

import java.security.NoSuchAlgorithmException;
import java.security.Signature;
import java.util.Collections;
import java.util.Objects;
import java.util.Set;

import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;

import reptor.distrbt.certify.KeyType;
import reptor.distrbt.certify.ProofType;
import reptor.jlib.hash.HashAlgorithm;


public class GenericSignatureAlgorithm implements SignatureAlgorithm
{

    private final SignatureType     m_sigtype;
    private final HashAlgorithm     m_hashalgo;
    private final String            m_signame;


    public GenericSignatureAlgorithm(SignatureType sigtype, HashAlgorithm hashalgo)
    {
        m_sigtype  = sigtype;
        m_hashalgo = hashalgo;
        m_signame  = m_hashalgo.getName() + "with" + sigtype.getBaseAlgorithm();
    }


    @Override
    public String toString()
    {
        return getName();
    }


    @Override
    public String getName()
    {
        return Signing.algorithmName( m_sigtype.toString(), m_hashalgo.getName() );
    }


    @Override
    public SignatureType getKeyType()
    {
        return m_sigtype;
    }


    @Override
    public ProofType getProofType()
    {
        return Signing.SIGNATURE;
    }


    @Override
    public HashAlgorithm getHashAlgorithm()
    {
        return m_hashalgo;
    }


    @Override
    public int getMaximumProofSize()
    {
        return m_sigtype.getMaximumProofSize();
    }


    @Override
    public Set<KeyType> getRequiredKeyTypes()
    {
        return Collections.singleton( m_sigtype );
    }


    @Override
    public Signature signer()
    {
        try
        {
            return Signature.getInstance( m_signame );
        }
        catch( NoSuchAlgorithmException e )
        {
            throw new UnsupportedOperationException( e );
        }
    }


    @Override
    public JcaContentSignerBuilder signerBuilder()
    {
        return new JcaContentSignerBuilder( m_signame + "Encryption" )
                        .setProvider( BouncyCastleProvider.PROVIDER_NAME );
    }


    @Override
    public boolean equals(Object obj)
    {
        if( obj==this )
            return true;

        if( obj==null || !( obj instanceof SignatureAlgorithm ) )
            return false;

        SignatureAlgorithm other = (SignatureAlgorithm) obj;

        return other.getKeyType().equals( getKeyType() ) &&
               other.getHashAlgorithm().equals( getHashAlgorithm() );
    }


    @Override
    public int hashCode()
    {
        return Objects.hash( getKeyType(), getHashAlgorithm() );
    }

}
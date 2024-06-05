package reptor.distrbt.certify.signature;

import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;


// TODO: Does Bouncy Castle already have such classes?
public abstract class SignatureBaseAlgorithm
{

    public static final SignatureBaseAlgorithm DSA =
            new SignatureBaseAlgorithm()
            {
                @Override
                public String toString()
                {
                    return "DSA";
                }

                @Override
                protected String getInternalName()
                {
                    return "DSA";
                }

                @Override
                public int getMaximumProofSize(int keysize)
                {
                    return (keysize==2048 ? 56 : 40) + 8; // 2x160b|2x224b+8 ASN
                }
            };

    public static final SignatureBaseAlgorithm RSA =
            new SignatureBaseAlgorithm()
            {
                @Override
                public String toString()
                {
                    return "RSA";
                }

                @Override
                protected String getInternalName()
                {
                    return "RSA";
                }


                @Override
                public int getMaximumProofSize(int keysize)
                {
                    return keysize/8;
                }
            };


    public static final SignatureBaseAlgorithm ECDSA =
            new SignatureBaseAlgorithm()
            {
                @Override
                public String toString()
                {
                    return "ECDSA";
                }

                @Override
                protected String getInternalName()
                {
                    return "EC";
                }

                @Override
                public int getMaximumProofSize(int keysize)
                {
                    return (keysize+3)/4+8;
                }
            };


    protected SignatureBaseAlgorithm()
    {
    }


    protected abstract String getInternalName();


    public abstract int getMaximumProofSize(int keysize);


    public SignatureType keyType(int keysize)
    {
        return new SignatureType( this, keysize );
    }


    public KeyFactory keyFactory()
    {
        try
        {
            return KeyFactory.getInstance( getInternalName() );
        }
        catch( NoSuchAlgorithmException e )
        {
            throw new UnsupportedOperationException( e );
        }
    }

}

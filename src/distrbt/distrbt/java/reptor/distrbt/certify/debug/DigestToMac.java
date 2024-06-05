package reptor.distrbt.certify.debug;

import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.Key;
import java.security.MessageDigest;
import java.security.spec.AlgorithmParameterSpec;
import java.util.Objects;

import javax.crypto.Mac;
import javax.crypto.MacSpi;


public class DigestToMac extends Mac
{

    public DigestToMac(MessageDigest digester)
    {
        super( new DigestSpi( digester ), null, "Hello" );

        try
        {
            super.init( null );
        }
        catch( InvalidKeyException e )
        {
            throw new IllegalStateException( e );
        }
    }


    private static class DigestSpi extends MacSpi
    {

        private final MessageDigest m_digester;

        public DigestSpi(MessageDigest digester)
        {
            m_digester = Objects.requireNonNull( digester );
        }

        @Override
        protected byte[] engineDoFinal()
        {
            return m_digester.digest();
        }

        @Override
        protected int engineGetMacLength()
        {
            return m_digester.getDigestLength();
        }

        @Override
        protected void engineInit(Key key, AlgorithmParameterSpec params)
                                throws InvalidKeyException, InvalidAlgorithmParameterException
        {

        }

        @Override
        protected void engineReset()
        {
            m_digester.reset();
        }

        @Override
        protected void engineUpdate(byte input)
        {
            m_digester.update( input );
        }

        @Override
        protected void engineUpdate(byte[] input, int offset, int len)
        {
            m_digester.update( input, offset, len );
        }

        @Override
        public Object clone() throws CloneNotSupportedException
        {
            return new DigestSpi( (MessageDigest) m_digester.clone() );
        }
    }

}

package reptor.distrbt.certify.debug;

import java.security.MessageDigest;


public class DummyDigest extends MessageDigest
{

    private final byte[] m_data;


    public DummyDigest(int digsize)
    {
        super( "Dummy" );

        m_data = new byte[ digsize ];
    }


    @Override
    protected int engineGetDigestLength()
    {
        return m_data.length;
    }


    @Override
    protected void engineUpdate(byte input)
    {
    }


    @Override
    protected void engineUpdate(byte[] input, int offset, int len)
    {
    }


    @Override
    protected byte[] engineDigest()
    {
        return m_data;
    }


    @Override
    protected void engineReset()
    {
    }

}

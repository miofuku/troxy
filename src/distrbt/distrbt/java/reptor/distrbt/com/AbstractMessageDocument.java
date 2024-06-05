package reptor.distrbt.com;

import reptor.distrbt.common.data.ImmutableData;


public abstract class AbstractMessageDocument implements MessageDocument
{

    protected int   m_presize   = -1;
    protected int   m_cntsize   = -1;
    protected short m_certsize  = -1;

    protected ImmutableData m_cntdig;
    protected ImmutableData m_msgdig;

    protected Boolean m_certvalid;
    protected Boolean m_chldvalid;


    public AbstractMessageDocument()
    {

    }


    @Override
    public boolean hasMessageSize()
    {
        return hasContentSize() && hasCertificateSize();
    }


    @Override
    public int getMessageSize()
    {
        assert hasMessageSize();

        return m_cntsize + m_certsize;
    }


    @Override
    public boolean hasPlainPrefixSize()
    {
        return m_presize!=-1;
    }


    @Override
    public int getPlainPrefixSize()
    {
        assert hasPlainPrefixSize();

        return m_presize;
    }


    @Override
    public boolean hasContentSize()
    {
        return m_cntsize!=-1;
    }


    @Override
    public int getContentSize()
    {
        assert hasContentSize();

        return m_cntsize;
    }


    @Override
    public boolean hasCertificateSize()
    {
        return m_certsize!=-1;
    }


    @Override
    public short getCertificateSize()
    {
        assert hasCertificateSize();

        return m_certsize;
    }


    @Override
    public ImmutableData getContentDigest()
    {
        return m_cntdig;
    }


    @Override
    public ImmutableData getMessageDigest()
    {
        return m_msgdig;
    }


    @Override
    public Boolean isCertificateValid()
    {
        return m_certvalid;
    }


    @Override
    public Boolean areInnerMessagesValid()
    {
        return m_chldvalid;
    }

}

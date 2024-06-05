package reptor.distrbt.com;

import reptor.distrbt.common.data.Data;
import reptor.distrbt.common.data.ImmutableData;


public abstract class AbstractMessageRecord extends AbstractMessageDocument implements MessageRecord //Orphic
{

    protected Data m_msgdata;


    @Override
    public void setContentSizes(int presize, int cntsize)
    {
        assert presize!=-1;
        assert cntsize!=-1;
        assert !hasContentSize();

        m_presize = presize;
        m_cntsize = cntsize;
    }


    @Override
    public void setCertificateSize(int certsize)
    {
        assert certsize!=-1;
        assert !hasCertificateSize();

        m_certsize = (short) certsize;
    }


    @Override
    public void setContentDigest(ImmutableData cntdig)
    {
        assert cntdig!=null;
        assert m_cntdig==null;

        m_cntdig = cntdig;
    }


    @Override
    public void setMessageDigest(ImmutableData msgdig)
    {
        assert msgdig!=null;
        assert m_msgdig==null;

        m_msgdig = msgdig;
    }


    @Override
    public void setMessageData(Data msgdata)
    {
        assert msgdata!=null;
        assert m_msgdata==null;

        m_msgdata = msgdata;
    }


    @Override
    public Data getMessageData()
    {
        return m_msgdata;
    }


    @Override
    public Data getContentData()
    {
        return m_msgdata==null || !hasContentSize() ?
                    null : m_msgdata.slice( 0, m_cntsize );
    }


    @Override
    public Data getCertificateData()
    {
        return m_msgdata==null || !hasContentSize()  || !hasCertificateSize() ?
                    null : m_msgdata.slice( m_cntsize, m_certsize );
    }


    @Override
    public void setCertificateValid(boolean iscrtval)
    {
        assert m_certvalid==null;

        m_certvalid = iscrtval;
    }


    @Override
    public void setInnerMessagesValid(boolean chldvalid)
    {
        assert m_chldvalid==null;

        m_chldvalid = chldvalid;
    }


    @Override
    public void setValid()
    {
        assert m_certvalid==null && m_chldvalid==null;

        m_certvalid = m_chldvalid = true;
    }


    @Override
    public void clearValid()
    {
        m_certvalid = m_chldvalid = null;
    }

}

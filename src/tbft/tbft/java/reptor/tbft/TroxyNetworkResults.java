package reptor.tbft;

import reptor.distrbt.io.UnbufferedDataSinkStatus;
import reptor.distrbt.io.UnbufferedDataSource;


public class TroxyNetworkResults
{

    private UnbufferedDataSinkStatus    m_instatus      = UnbufferedDataSinkStatus.BLOCKED;
    private int                         m_reqoutbufsize = UnbufferedDataSource.NO_PENDING_DATA;


    public UnbufferedDataSinkStatus canProcessInboundData()
    {
        return m_instatus;
    }


    public void canProcessInboundData(UnbufferedDataSinkStatus instatus)
    {
        m_instatus = instatus;
    }

    public void canProcessInboundData(int instatus)
    {
        if (instatus < 0)
            m_instatus = null;
        else
            m_instatus = UnbufferedDataSinkStatus.values()[instatus];
    }

    public int getRequiredOutboundBufferSize()
    {
        return m_reqoutbufsize;
    }


    public void setRequiredOutboundBufferSize(int reqoutbufsize)
    {
        m_reqoutbufsize = reqoutbufsize;
    }

    @Override
    public String toString()
    {
        return String.format("{ instatus: %s, reqout: %d }", m_instatus != null ? m_instatus.toString() : "null", m_reqoutbufsize);
    }

}
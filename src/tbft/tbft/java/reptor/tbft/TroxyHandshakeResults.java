package reptor.tbft;

import reptor.replct.connect.RemoteEndpoint;

public class TroxyHandshakeResults extends TroxyNetworkResults
{

    private RemoteEndpoint  m_remep;
    private boolean         m_isfinished;


    public RemoteEndpoint getRemoteEndpoint()
    {
        return m_remep;
    }


    public void setRemoteEndpoint(RemoteEndpoint remep)
    {
        m_remep = remep;
    }


    public void setRemoteEndpoint(short remno, short netno)
    {
        m_remep = new RemoteEndpoint(remno, netno);
    }


    public boolean isFinished()
    {
        return m_isfinished;
    }


    public void isFinished(boolean isfinished)
    {
        m_isfinished = isfinished;
    }


    @Override
    public String toString()
    {
        return String.format("{ remno: %d, netno: %d, fin: %s, net: %s }", m_remep.getProcessNumber(), m_remep.getNetworkNumber(), m_isfinished, super.toString());
    }

}

package reptor.replct.connect;


public class RemoteEndpoint
{

    private final short   m_remno;
    private final short   m_netno;


    public RemoteEndpoint(short remno, short netno)
    {
        m_remno = remno;
        m_netno = netno;
    }


    public short getProcessNumber()
    {
        return m_remno;
    }


    public short getNetworkNumber()
    {
        return m_netno;
    }

}

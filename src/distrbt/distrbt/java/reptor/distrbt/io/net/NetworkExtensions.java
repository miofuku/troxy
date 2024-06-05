package reptor.distrbt.io.net;

import java.nio.channels.SocketChannel;


public class NetworkExtensions
{

    public interface DataChannelObserver
    {
        void dataSent(int nbytes);
        void dataReceived(int nbytes);
    }


    public interface ConnectionObserver extends DataChannelObserver
    {
        void connectionInitialised(SocketChannel channel);

        static final ConnectionObserver EMPTY = new ConnectionObserver()
        {
            @Override
            public void connectionInitialised(SocketChannel channel)
            {
            }

            @Override
            public void dataSent(int nbytes)
            {
            }

            @Override
            public void dataReceived(int nbytes)
            {
            }
        };
    }


    public interface ConnectionExtension
    {
        ConnectionObserver getConnectionObserver();
    }


    private final ConnectionExtension m_connext;


    public NetworkExtensions(ConnectionExtension conext)
    {
        m_connext = conext;
    }


    public ConnectionObserver getConnectionObserver()
    {
        return m_connext!=null ? m_connext.getConnectionObserver() : ConnectionObserver.EMPTY;
    }

}

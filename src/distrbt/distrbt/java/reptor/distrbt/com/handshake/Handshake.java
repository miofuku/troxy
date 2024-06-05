package reptor.distrbt.com.handshake;

import java.io.IOException;
import java.net.InetSocketAddress;

import reptor.chronos.com.CommunicationLayerElement;
import reptor.chronos.com.ConnectorEndpoint;
import reptor.distrbt.io.UnbufferedDataSink;
import reptor.distrbt.io.UnbufferedDataSource;
import reptor.distrbt.io.net.BufferedNetworkState;
import reptor.distrbt.io.ssl.SslState;


public interface Handshake<R> extends ConnectorEndpoint<UnbufferedDataSink, UnbufferedDataSource>, CommunicationLayerElement

{

    public interface LogChannel
    {
        void log(String action, String msg, Object arg);
    }

    void            initLogChannel(LogChannel logchannel);

    @Override
    void            activate();
    @Override
    void            deactivate();
    @Override
    boolean         isActivated();

    void            reset(boolean clear);

    void            initConnection(Object args);
    void            connect(InetSocketAddress remaddr) throws IOException;
    void            accept(InetSocketAddress remaddr) throws IOException;

    boolean         needsReconfiguraiton();
    void            reconfigure();

    R               getRemote();

    boolean         isFinished();
    // TODO: Turn into a layer-independent variant
    void            saveState(BufferedNetworkState netstate, SslState sslstate);
    HandshakeState  getState();

    String          getConnectionDescription();

}

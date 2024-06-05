package reptor.distrbt.com.handshake;

import java.io.IOException;
import java.nio.channels.SocketChannel;

import reptor.chronos.ChronosTask;


public interface HandshakeHandler extends ChronosTask
{

    short           getHandlerNumber();
    boolean         isUnused();
    boolean         isConnector();
    boolean         isFinished();
    boolean         hasError();
    IOException     getError();
    HandshakeState  getState();

    boolean connect(Object args);
    boolean retryConnectNow();
    boolean retryConnect(long delay);
    boolean accept(SocketChannel channel);
    void    clear(boolean close);

}

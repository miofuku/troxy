package reptor.replct.connect;


public interface Handshaking<R>
{
    Handshaking<R>                   activate();

    HandshakingProcess<? extends R>  createProcess(short procno);
    HandshakingProcess<? extends R>  createConnectorProcess(short procno);
    HandshakingProcess<? extends R>  createAcceptorProcess(short procno);
}

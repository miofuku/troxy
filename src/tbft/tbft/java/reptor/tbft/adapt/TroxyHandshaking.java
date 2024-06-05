package reptor.tbft.adapt;

import java.util.Objects;

import reptor.distrbt.domains.SelectorDomainContext;
import reptor.replct.connect.AbstractHandshakingProcess;
import reptor.replct.connect.Handshaking;
import reptor.replct.connect.HandshakingProcess;
import reptor.replct.connect.RemoteEndpoint;
import reptor.tbft.Troxy;


public class TroxyHandshaking implements Handshaking<RemoteEndpoint>
{

    @FunctionalInterface
    public interface HandshakeHandlerMapping
    {
        short   handlerID(short epno, short handlno);
    }


    private final HandshakeHandlerMapping                m_handlmap;
    private final Handshaking<? extends RemoteEndpoint>  m_base;


    public TroxyHandshaking(HandshakeHandlerMapping handlmap, Handshaking<? extends RemoteEndpoint> base)
    {
        m_handlmap = Objects.requireNonNull( handlmap );
        m_base     = Objects.requireNonNull( base );
    }


    @Override
    public String toString()
    {
        return m_base + "<>TROXY";
    }


    public Handshaking<? extends RemoteEndpoint> getBase()
    {
        return m_base;
    }

    @Override
    public TroxyHandshaking activate()
    {
        m_base.activate();

        return this;
    }


    @Override
    public HandshakingProcess<? extends RemoteEndpoint> createProcess(short procno)
    {
        throw new UnsupportedOperationException();
    }


    @Override
    public HandshakingProcess<? extends RemoteEndpoint> createConnectorProcess(short procno)
    {
        return m_base.createProcess( procno );
    }


    @Override
    public TroxyHandshakingProcess createAcceptorProcess(short procno)
    {
        return new TroxyHandshakingProcess( procno, m_handlmap );
    }


    public static class TroxyHandshakingProcess extends AbstractHandshakingProcess<RemoteEndpoint>
    {
        private final HandshakeHandlerMapping   m_handlmap;
        private Troxy                           m_troxy;

        public TroxyHandshakingProcess(short procno, HandshakeHandlerMapping handlmap)
        {
            super( procno );

            m_handlmap = handlmap;
        }

        public void setTroxy(Troxy troxy)
        {
            m_troxy = troxy;
        }

        @Override
        public TroxyHandshakeConnector createHandshake(SelectorDomainContext domcntxt, short epno, short hsno)
        {
            return new TroxyHandshakeConnector( m_troxy, m_procno, m_handlmap.handlerID( epno, hsno ) );
        }
    }

}

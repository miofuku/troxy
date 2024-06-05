package reptor.replct.connect;

import java.util.Objects;
import java.util.function.IntFunction;

import reptor.distrbt.com.handshake.Handshake;
import reptor.distrbt.com.handshake.SslHandshake;
import reptor.distrbt.domains.SelectorDomainContext;
import reptor.distrbt.io.ssl.SslConfiguration;


public class SslHandshaking<R> implements Handshaking<R>
{

    private final Handshaking<? extends R>      m_next;
    private final IntFunction<SslConfiguration> m_sslconffac;


    public SslHandshaking(Handshaking<? extends R> next, IntFunction<SslConfiguration> sslconffac)
    {
        m_next       = Objects.requireNonNull( next );
        m_sslconffac = sslconffac;
    }


    @Override
    public String toString()
    {
        return m_next.toString() + "<SSL";
    }


    @Override
    public SslHandshaking<R> activate()
    {
        m_next.activate();

        return this;
    }


    @Override
    public SslHandshakingProcess<? extends R> createProcess(short procno)
    {
        return createProcess( procno, m_next.createProcess( procno ) );
    }


    @Override
    public SslHandshakingProcess<? extends R> createConnectorProcess(short procno)
    {
        return createProcess( procno, m_next.createConnectorProcess( procno ) );
    }


    @Override
    public SslHandshakingProcess<? extends R> createAcceptorProcess(short procno)
    {
        return createProcess( procno, m_next.createAcceptorProcess( procno ) );
    }


    private SslHandshakingProcess<? extends R> createProcess(short procno, HandshakingProcess<? extends R> next)
    {
        return new SslHandshakingProcess<>( procno, next, m_sslconffac.apply( procno ) );
    }


    public static class SslHandshakingProcess<R> extends AbstractHandshakingProcess<R>
    {
        private final SslConfiguration                  m_sslconf;
        private final HandshakingProcess<? extends R>   m_next;

        public SslHandshakingProcess(short procno, HandshakingProcess<? extends R> next, SslConfiguration sslconf)
        {
            super( procno );

            m_sslconf = sslconf;
            m_next    = Objects.requireNonNull( next );
        }

        @Override
        public Handshake<? extends R> createHandshake(SelectorDomainContext domcntxt, short epno, short hsno)
        {
            Handshake<? extends R> handshake = m_next.createHandshake( domcntxt, epno, hsno );

            handshake = new SslHandshake<>( domcntxt, handshake, m_sslconf.sslContext() );

            return handshake;
        }
    }

}

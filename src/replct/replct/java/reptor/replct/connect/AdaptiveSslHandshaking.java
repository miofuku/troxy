package reptor.replct.connect;

import java.util.Objects;
import java.util.function.IntFunction;

import reptor.distrbt.com.handshake.AdaptiveSslHandshake;
import reptor.distrbt.com.handshake.AdaptiveSslHandshake.SslMode;
import reptor.distrbt.com.handshake.Handshake;
import reptor.distrbt.domains.SelectorDomainContext;
import reptor.distrbt.io.ssl.SslConfiguration;


public class AdaptiveSslHandshaking<R> implements Handshaking<R>
{

    private final Handshaking<? extends R>      m_next;
    private final IntFunction<SslConfiguration> m_sslconffac;
    private final SslMode                       m_sslmode;


    public AdaptiveSslHandshaking(Handshaking<? extends R> next, IntFunction<SslConfiguration> sslconffac, SslMode sslmode)
    {
        checkArguments( sslconffac, sslmode );

        m_next       = Objects.requireNonNull( next );
        m_sslconffac = sslconffac;
        m_sslmode    = sslmode;
    }


    private static void checkArguments(Object sslconf, SslMode sslmode)
    {
        if( ( sslmode==SslMode.DEACTIVATED )!=( sslconf==null ) )
            throw new IllegalArgumentException();
    }


    @Override
    public String toString()
    {
        return m_next.toString() + "<SSL[" + m_sslmode + "]";
    }


    @Override
    public AdaptiveSslHandshaking<R> activate()
    {
        m_next.activate();

        return this;
    }


    @Override
    public AdaptiveSslHandshakingProcess<? extends R> createProcess(short procno)
    {
        return createProcess( procno, m_next.createProcess( procno ) );
    }


    @Override
    public AdaptiveSslHandshakingProcess<? extends R> createConnectorProcess(short procno)
    {
        return createProcess( procno, m_next.createConnectorProcess( procno ) );
    }


    @Override
    public AdaptiveSslHandshakingProcess<? extends R> createAcceptorProcess(short procno)
    {
        return createProcess( procno, m_next.createAcceptorProcess( procno ) );
    }


    private AdaptiveSslHandshakingProcess<? extends R> createProcess(short procno, HandshakingProcess<? extends R> next)
    {
        return new AdaptiveSslHandshakingProcess<>( procno, next, m_sslconffac.apply( procno ), m_sslmode );
    }


    public static class AdaptiveSslHandshakingProcess<R> extends AbstractHandshakingProcess<R>
    {
        private final HandshakingProcess<? extends R> m_next;
        private final SslConfiguration               m_sslconf;
        private final SslMode                        m_sslmode;

        public AdaptiveSslHandshakingProcess(short procno, HandshakingProcess<? extends R> next, SslConfiguration sslconf, SslMode sslmode)
        {
            super( procno );

            checkArguments( sslconf, sslmode );

            m_next    = Objects.requireNonNull( next );
            m_sslconf = sslconf;
            m_sslmode = sslmode;
        }

        @Override
        public Handshake<? extends R> createHandshake(SelectorDomainContext domcntxt, short epno, short hsno)
        {
            Handshake<? extends R> handshake = m_next.createHandshake( domcntxt, epno, hsno );

            if( m_sslconf!=null )
                handshake = new AdaptiveSslHandshake<>( domcntxt, handshake, m_sslconf.sslContext(), m_sslmode );

            return handshake;
        }
    }

}

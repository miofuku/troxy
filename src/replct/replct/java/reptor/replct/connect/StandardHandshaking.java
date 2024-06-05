package reptor.replct.connect;

import reptor.distrbt.domains.SelectorDomainContext;


public class StandardHandshaking implements Handshaking<RemoteEndpoint>
{

    @Override
    public String toString()
    {
        return "STD";
    }


    @Override
    public StandardHandshaking activate()
    {
        return this;
    }


    @Override
    public StandardHandshakingProcess createProcess(short procno)
    {
        return new StandardHandshakingProcess( procno );
    }


    @Override
    public StandardHandshakingProcess createConnectorProcess(short procno)
    {
        return createProcess( procno );
    }


    @Override
    public StandardHandshakingProcess createAcceptorProcess(short procno)
    {
        return createProcess( procno );
    }


    public static class StandardHandshakingProcess extends AbstractHandshakingProcess<RemoteEndpoint>
    {
        public StandardHandshakingProcess(short procno)
        {
            super( procno );
        }

        @Override
        public StandardHandshake createHandshake(SelectorDomainContext domcntxt, short epno, short hsno)
        {
            return new StandardHandshake( m_procno );
        }
    }

}

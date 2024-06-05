package reptor.tbft;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;

import reptor.distrbt.domains.SelectorDomainContext;
import reptor.replct.connect.AbstractHandshakingProcess;
import reptor.replct.connect.Handshaking;
import reptor.replct.connect.RemoteEndpoint;
import reptor.replct.invoke.ClientToWorkerAssignment;


public class ClientAssigning implements Handshaking<RemoteEndpoint>
{

    private final ClientToWorkerAssignment  m_clitowrk;


    public ClientAssigning(ClientToWorkerAssignment clitowrk)
    {
        m_clitowrk = Objects.requireNonNull( clitowrk );
    }


    @Override
    public String toString()
    {
        return "CAS";
    }


    @Override
    public ClientAssigning activate()
    {
        return this;
    }


    @Override
    public ClientAssigningProcess createProcess(short procno)
    {
        return new ClientAssigningProcess( procno, m_clitowrk );
    }


    @Override
    public ClientAssigningProcess createConnectorProcess(short procno)
    {
        return createProcess( procno );
    }


    @Override
    public ClientAssigningProcess createAcceptorProcess(short procno)
    {
        return createProcess( procno );
    }


    public static class ClientAssigningProcess extends AbstractHandshakingProcess<RemoteEndpoint>
    {
        private final ClientToWorkerAssignment  m_clitowrk;
        private final AtomicInteger             m_clictr = new AtomicInteger();

        public ClientAssigningProcess(short locno, ClientToWorkerAssignment clitowrk)
        {
            super( locno );

            m_clitowrk = Objects.requireNonNull( clitowrk );
        }

        @Override
        public ClientAssignment createHandshake(SelectorDomainContext domcntxt, short epno, short hsno)
        {
            return new ClientAssignment( (byte) m_procno, m_clictr, m_clitowrk );
        }
    }

}

package reptor.chronos.domains;

import java.util.Objects;

import reptor.chronos.ChronosDomain;
import reptor.jlib.threading.InitializableThread;


public class DomainThread extends InitializableThread
{

    private final ChronosDomain m_domain;


    public DomainThread(ChronosDomain domain, int[] affinity)
    {
        super( domain, domain.toString(), affinity );

        m_domain = Objects.requireNonNull( domain );
    }


    public ChronosDomain getDomain()
    {
        return m_domain;
    }


    @Override
    protected void initContext()
    {
        super.initContext();

        m_domain.initContext();
    }


    public void shutdown()
    {
        m_domain.shutdown();
    }
}

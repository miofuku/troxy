package reptor.chronos.domains;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

import reptor.chronos.ChronosDomain;
import reptor.chronos.context.ChronosSystemContext;


// Time-transcendent controller for a group of domains
public class DomainController implements ChronosSystemContext
{

    private final List<? extends DomainThread> m_threads;


    public DomainController(List<? extends DomainThread> threads)
    {
        m_threads = Objects.requireNonNull( threads );
    }


    public static DomainController createForThreads(List<? extends DomainThread> threads)
    {
        return new DomainController( threads );
    }


    public static DomainController createForDomains(Collection<? extends ChronosDomain> domains)
    {
        List<DomainThread> threads = new ArrayList<>( domains.size() );

        for( ChronosDomain dom : domains )
            threads.add( new DomainThread( dom, null ) );

        return new DomainController( threads );
    }


    public List<? extends DomainThread> getDomains()
    {
        return m_threads;
    }


    @Override
    public void shutdownDomains()
    {
        for( DomainThread dom : m_threads )
            dom.shutdown();
    }


    public void executeDomains() throws InterruptedException
    {
        initDomains().startDomains().joinDomains();
    }


    public DomainController initDomains()
    {
        for( DomainThread dom : m_threads )
            dom.init();

        return this;
    }


    public DomainController startDomains()
    {
        for( DomainThread dom : m_threads )
            dom.start();

        return this;
    }


    public void joinDomains() throws InterruptedException
    {
        for( DomainThread dom : m_threads )
            dom.join();
    }

}

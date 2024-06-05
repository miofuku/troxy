package reptor.bench.compose;

import reptor.chronos.domains.DomainThread;
import reptor.distrbt.domains.SelectorDomain;


public class SelectorDomainBuilder
{

    private final String m_name;
    private final int[]  m_affinity;


    public SelectorDomainBuilder(String name, int[] affinity)
    {
        m_name     = name;
        m_affinity = affinity;
    }


    public String getName()
    {
        return m_name;
    }


    public int[] getAffinity()
    {
        return m_affinity;
    }


    public DomainThread createDomain()
    {
        return new DomainThread( new SelectorDomain( m_name ), m_affinity );
    }

}

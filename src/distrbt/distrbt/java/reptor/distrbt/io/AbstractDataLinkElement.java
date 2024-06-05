package reptor.distrbt.io;

import reptor.chronos.com.SynchronousLinkElement;
import reptor.distrbt.domains.SelectorDomainContext;


public abstract class AbstractDataLinkElement implements SynchronousLinkElement
{

    private boolean m_isready = false;


    public AbstractDataLinkElement()
    {

    }


    protected abstract DataChannelContext<? extends SelectorDomainContext> master();


    public SelectorDomainContext getDomainContext()
    {
        return master().getDomainContext();
    }


    public String getChannelName()
    {
        return master().getChannelName();
    }


    @Override
    public boolean isReady()
    {
        return m_isready;
    }


    protected void markReady()
    {
        m_isready = true;
    }


    protected void notifyReady()
    {
        if( !m_isready )
        {
            markReady();
            master().dataReady( this );
        }
    }


    protected void clearReady()
    {
        m_isready = false;
    }


    protected boolean isDone(boolean isdone)
    {
        if( isdone )
            clearReady();

        return isdone;
    }

}

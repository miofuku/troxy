package reptor.chronos.domains;

import java.io.IOException;
import java.nio.channels.Selector;

import reptor.chronos.ChronosAddress;
import reptor.chronos.context.ChronosDomainContext;
import reptor.chronos.orphics.AddressName;


public class SimpleDomain extends AbstractGenericDomain<ChronosDomainContext>
{

    private Selector m_selector;


    public SimpleDomain(String name)
    {
        this( new AddressName( name ) );
    }


    public SimpleDomain(ChronosAddress addr)
    {
        this( addr, new ChronosAddress[] { addr } );
    }


    public SimpleDomain(ChronosAddress addr, ChronosAddress[] remdoms)
    {
        super( addr, remdoms );

        try
        {
            m_selector = Selector.open();
        }
        catch( IOException e )
        {
            handleIOException( e );
        }
    }


    @Override
    public ChronosDomainContext getDomainContext()
    {
        return this;
    }


    @Override
    protected void processEvents()
    {
        // Do not reset wake-up signal. It's optimistic anyways.
    }


    @Override
    protected void waitForEvents(long timeout)
    {
        try
        {
            m_selector.select( timeout );
        }
        catch( IOException e )
        {
            handleIOException( e );
        }
    }


    @Override
    protected void wakeup()
    {
        m_selector.wakeup();
    }


    private RuntimeException handleIOException(IOException e)
    {
        e.printStackTrace();
        return new IllegalStateException( e );
    }


}
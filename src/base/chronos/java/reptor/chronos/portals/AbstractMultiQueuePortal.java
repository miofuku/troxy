package reptor.chronos.portals;

import java.util.Arrays;
import java.util.NoSuchElementException;

import reptor.chronos.ChronosAddress;
import reptor.chronos.com.Portal;
import reptor.chronos.com.PushMessageSink;
import reptor.chronos.context.ChronosDomainContext;
import reptor.chronos.orphics.MessageQueueHandler;
import reptor.jlib.collect.Slots;
import reptor.jlib.strings.ArrayFormat;


public abstract class AbstractMultiQueuePortal<M> implements Portal<M>, MessageQueueHandler<Portal<M>>
{

    private final ChronosDomainContext  m_domcntxt;
    private final ChronosAddress[]      m_remdoms;
    private final Slots<Portal<M>>      m_portals;

    private boolean                     m_isready  = false;
    private boolean                     m_extready = false;
    private int                         m_curportalno;
    private Portal<M>                   m_curportal;


    public AbstractMultiQueuePortal(ChronosDomainContext domcntxt, ChronosAddress[] remdoms)
    {
        m_domcntxt = domcntxt;
        m_remdoms  = remdoms;
        m_portals  = new Slots<>( remdoms.length, portno -> createRemotePortal( portno, remdoms[ portno ] ) );

        m_curportalno = -1;
    }


    @Override
    public ChronosDomainContext getDomainContext()
    {
        return m_domcntxt;
    }


    protected abstract Portal<M> createRemotePortal(int portno, ChronosAddress remdom);


    public PushMessageSink<M> createRemoteChannel(ChronosAddress origin)
    {
        int key = Arrays.asList( m_remdoms ).indexOf( origin );

        if( key<0 )
            throw new NoSuchElementException( "Contained: " + ArrayFormat.DEFAULT.valuesToString( m_remdoms ) +
                                              " requested: " + origin );

        return m_portals.get( key ).createChannel( origin );
    }


    @Override
    public void messagesReady(Portal<M> queue)
    {
        m_extready = true;

        messagesReady();
    }


    @Override
    public void messagesReady()
    {
        if( !m_isready )
        {
            m_isready = true;
            notifyHandler();
        }
    }


    protected abstract void notifyHandler();


    @Override
    public boolean isReady()
    {
        return m_isready;
    }


    protected void clearReady()
    {
        m_isready = false;
    }


    protected void retrieveMessagesIfReady()
    {
        if( !m_extready )
            return;

        doRetrieveMessages();

        m_extready = false;
    }


    protected void doRetrieveMessages()
    {
        for( int i=0; i<m_portals.size(); i++ )
            m_portals.get( i ).retrieveMessages();

        m_curportal   = m_portals.get( 0 );
        m_curportalno = 0;
    }


    protected boolean isPortalReady()
    {
        return m_curportalno!=-1;
    }


    protected M pollPortals()
    {
        while( true )
        {
            M msg = m_curportal.poll();

            if( msg!=null )
                return msg;
            else if( ++m_curportalno<m_portals.size() )
                m_curportal = m_portals.get( m_curportalno );
            else
            {
                m_curportalno = -1;
                m_isready     = false;

                return null;
            }
        }
    }

}
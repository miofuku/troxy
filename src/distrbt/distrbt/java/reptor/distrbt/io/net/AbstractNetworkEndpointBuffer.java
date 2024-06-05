package reptor.distrbt.io.net;

import java.nio.ByteBuffer;
import java.util.Objects;
import java.util.function.IntFunction;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Preconditions;

import reptor.chronos.Notifying;
import reptor.chronos.com.SynchronousLinkElement;
import reptor.distrbt.domains.SelectorDomainContext;
import reptor.distrbt.io.AbstractDataLinkElement;
import reptor.distrbt.io.DataChannelContext;


public abstract class AbstractNetworkEndpointBuffer
        extends AbstractDataLinkElement
        implements NetworkEndpointBuffer, NetworkEndpointContext<SelectorDomainContext>
{

    private static final Logger s_logger = LoggerFactory.getLogger( AbstractNetworkEndpointBuffer.class );

    protected DataChannelContext<? extends SelectorDomainContext>  m_master;

    protected ByteBuffer                m_buffer;
    protected IntFunction<ByteBuffer>   m_buffac;
    protected boolean                   m_isactivated = false;


    public AbstractNetworkEndpointBuffer(IntFunction<ByteBuffer> buffac, int minbufsize)
    {
        this( buffac.apply( minbufsize ), buffac );
    }


    public AbstractNetworkEndpointBuffer(ByteBuffer buffer, IntFunction<ByteBuffer> buffac)
    {
        m_buffer = Objects.requireNonNull( buffer );
        m_buffac = buffac;
    }


    protected abstract NetworkEndpoint endpoint();


    protected abstract void setDefaults();


    @Override
    public void bindToMaster(DataChannelContext<? extends SelectorDomainContext> master)
    {
        Preconditions.checkState( m_master==null );
        Preconditions.checkState( !m_isactivated );

        m_master = master;

        endpoint().bindToMaster( this );
    }


    @Override
    public void unbindFromMaster(DataChannelContext<? extends SelectorDomainContext> master)
    {
        Preconditions.checkArgument( Objects.requireNonNull( master )==m_master );
        Preconditions.checkState( !m_isactivated );

        clearReady();
        endpoint().unbindFromMaster( this );

        m_master = null;
    }


    @Override
    protected DataChannelContext<? extends SelectorDomainContext> master()
    {
        return m_master;
    }


    @Override
    @Notifying
    public void activate()
    {
        if( m_isactivated )
            return;

        Preconditions.checkState( m_master!=null );

        m_isactivated = true;

        endpoint().activate();
    }


    @Override
    public void deactivate()
    {
        if( !m_isactivated )
            return;

        Preconditions.checkState( m_master!=null );

        endpoint().deactivate();

        m_isactivated = false;
    }


    @Override
    public boolean isActivated()
    {
        return m_isactivated;
    }


    @Override
    public void endpointActivated(NetworkEndpoint elem)
    {
        setDefaults();
    }


    @Override
    public void clear()
    {
        if( endpoint().isActivated() )
            setDefaults();

        clearReady();
        m_isactivated = false;
    }


    public void adjustBuffer(int minbufsize)
    {
        if( m_buffac==null )
            throw new UnsupportedOperationException();

        if( m_buffer.capacity()<minbufsize )
            replaceBuffer( m_buffac.apply( minbufsize ) );
    }


    protected void bufferReplaced()
    {
        s_logger.debug( "{} set network buffer to {} bytes", this, m_buffer.capacity() );
    }


    @Override
    public int getCapacity()
    {
        return m_buffer.capacity();
    }


    @Override
    public void dataReady(SynchronousLinkElement elem)
    {
        notifyReady();
    }

}

package reptor.distrbt.io.net;

import java.io.EOFException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Objects;
import java.util.function.IntFunction;

import reptor.distrbt.io.BufferedDataSource;
import reptor.distrbt.io.net.Network.NetworkSource;


public abstract class AbstractNetworkSourceBuffer extends AbstractNetworkEndpointBuffer
                                                  implements BufferedDataSource
{

    protected NetworkSource             m_source;
    protected boolean                   m_enabled;
    protected boolean                   m_unprocessed = false;


    public AbstractNetworkSourceBuffer(NetworkSource source, IntFunction<ByteBuffer> buffac, int minbufsize)
    {
        this( source, buffac.apply( minbufsize ), buffac );
    }


    public AbstractNetworkSourceBuffer(NetworkSource source, ByteBuffer buffer, IntFunction<ByteBuffer> buffac)
    {
        super( buffer, buffac );

        m_source = Objects.requireNonNull( source );
    }


    @Override
    protected NetworkEndpoint endpoint()
    {
        return m_source;
    }


    @Override
    protected void setDefaults()
    {
        enable();
    }


    @Override
    public void clear()
    {
        super.clear();

        m_buffer.clear();
    }


    @Override
    public String toString()
    {
        return getChannelName() + "[NET(I)]";
    }


    protected void retrieveData() throws IOException
    {
        int nbytes = m_source.retrieveData( m_buffer );

        if( nbytes<0 )
            throw new EOFException();

        m_unprocessed = nbytes>0;

        clearReady();
    }


    @Override
    public boolean hasUnprocessedData()
    {
        return m_unprocessed;
    }


    protected void clearUnprocessed()
    {
        m_unprocessed = false;
    }


    protected void enable()
    {
        m_source.enable();
        m_enabled = true;
    }


    protected void disable()
    {
        m_source.disable();
        m_enabled = false;
    }

}
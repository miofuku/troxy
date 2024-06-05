package reptor.distrbt.io.net;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Objects;
import java.util.function.IntFunction;

import com.google.common.base.Preconditions;

import reptor.distrbt.io.AdaptiveDataSink;
import reptor.distrbt.io.CompactingSourceBuffers;
import reptor.distrbt.io.UnbufferedDataSinkStatus;
import reptor.distrbt.io.net.Network.NetworkSink;


public class NetworkSinkBuffer extends AbstractNetworkEndpointBuffer implements AdaptiveDataSink
{

    private ByteBuffer              m_srcbuf;

    private NetworkSink             m_sink;
    private boolean                 m_enabled;


    public NetworkSinkBuffer(NetworkSink sink, IntFunction<ByteBuffer> buffac, int minbufsize)
    {
        this( sink, buffac.apply( minbufsize ), buffac );
    }

    public NetworkSinkBuffer(NetworkSink sink, ByteBuffer buffer, IntFunction<ByteBuffer> buffac)
    {
        super( buffer, buffac );

        m_sink   = Objects.requireNonNull( sink );
        m_srcbuf = m_buffer;
        CompactingSourceBuffers.clear( m_srcbuf );
    }


    @Override
    protected NetworkEndpoint endpoint()
    {
        return m_sink;
    }


    @Override
    protected void setDefaults()
    {
        disable();
    }


    @Override
    public void clear()
    {
        super.clear();

        m_srcbuf = m_buffer;
        CompactingSourceBuffers.clear( m_srcbuf );
    }


    @Override
    public String toString()
    {
        return getChannelName() + "[NET(O)]";
    }


    @Override
    public int getStateSize()
    {
        return CompactingSourceBuffers.getStateSize( m_srcbuf );
    }


    @Override
    public void saveState(ByteBuffer dst)
    {
        CompactingSourceBuffers.saveState( m_srcbuf, dst );
    }


    @Override
    public void installState(ByteBuffer src)
    {
        Preconditions.checkState( !isActivated() );
        Preconditions.checkState( canPrepare() );

        CompactingSourceBuffers.installState( m_srcbuf, src );
    }


    @Override
    public ByteBuffer replaceBuffer(ByteBuffer newbuffer)
    {
        ByteBuffer oldbuffer = m_buffer;

        CompactingSourceBuffers.replaceBuffer( oldbuffer, newbuffer );

        m_buffer = newbuffer;
        if( m_srcbuf==oldbuffer )
            m_srcbuf = newbuffer;

        if( !m_enabled && m_srcbuf.hasRemaining() )
            enable();
        else if( m_enabled && !m_srcbuf.hasRemaining() )
            disable();

        bufferReplaced();

        // Neither canPrepare() nor canProcessData() changes, thus the master does not need to be informed.

        return oldbuffer;
    }

    @Override
    public int getMinimumBufferSize()
    {
        return 0;
    }

    @Override
    public boolean hasRemaining()
    {
        return m_srcbuf.hasRemaining();
    }


     @Override
    public boolean canPrepare()
    {
        return m_srcbuf==m_buffer;
    }


     @Override
    public int getAvailableBufferSize()
    {
        return CompactingSourceBuffers.getAvailableBufferSize( m_buffer );
    }


    @Override
    public ByteBuffer startPreparation()
    {
        return CompactingSourceBuffers.startPreparation( m_buffer );
    }


    @Override
    public void finishPreparation()
    {
        finishPreparation( m_buffer );
    }


    @Override
    public void finishPreparation(ByteBuffer src)
    {
        CompactingSourceBuffers.finishPreparation( m_buffer );

        m_srcbuf = src;

        // We are optimistic that the following holds:
        //   m_srcbuf.hasRemaining() && ( !m_enabled || m_sink.canProcessData() )
        markReady();
    }


    @Override
    public void execute() throws IOException
    {
        m_sink.processData( m_srcbuf );

        if( m_srcbuf.hasRemaining() )
        {
            if( !m_enabled )
                enable();
        }
        else
        {
            if( m_enabled )
                disable();

            if( m_srcbuf!=m_buffer )
                m_srcbuf = m_buffer;
        }

        clearReady();
    }


    @Override
    public UnbufferedDataSinkStatus canProcessData()
    {
        return hasRemaining() ? UnbufferedDataSinkStatus.BLOCKED : UnbufferedDataSinkStatus.CAN_PROCESS;
    }


    @Override
    public void processData(ByteBuffer src) throws IOException
    {
        if( hasRemaining() )
            return;

        m_sink.processData( src );
    }


    private void enable()
    {
        m_sink.enable();
        m_enabled = true;
    }


    private void disable()
    {
        m_sink.disable();
        m_enabled = false;
    }

}
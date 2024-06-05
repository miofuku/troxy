package reptor.distrbt.io.net;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.function.IntFunction;

import com.google.common.base.Preconditions;

import reptor.distrbt.io.net.Network.NetworkSource;


public class AccumulatingNetworkSourceBuffer extends AbstractNetworkSourceBuffer
{

    private final int m_compactthreshold;
    private int       m_mark;


    public AccumulatingNetworkSourceBuffer(NetworkSource source, int compactthreshold,
                                           IntFunction<ByteBuffer> buffac, int minbufsize)
    {
        this( source, compactthreshold, buffac.apply( minbufsize ), buffac );
    }


    public AccumulatingNetworkSourceBuffer(NetworkSource source, int compactthreshold,
                                           ByteBuffer buffer, IntFunction<ByteBuffer> buffac)
    {
        super( source, buffer, buffac );

        Preconditions.checkArgument( compactthreshold>0 );

        m_compactthreshold = compactthreshold;
    }


    @Override
    public void clear()
    {
        super.clear();

        m_mark = 0;
    }


    @Override
    public int getStateSize()
    {
        return m_buffer.position()-m_mark;
    }


    @Override
    public void saveState(ByteBuffer dst)
    {
        flip();
        dst.put( m_buffer );
        m_buffer.limit( m_buffer.capacity() );
    }


    @Override
    public void installState(ByteBuffer src)
    {
        Preconditions.checkState( !isActivated() );

        m_buffer.put( src );
    }


    @Override
    public ByteBuffer replaceBuffer(ByteBuffer newbuffer)
    {
        ByteBuffer oldbuffer = m_buffer;

        flip();
        newbuffer.put( oldbuffer );

        m_mark   = 0;
        m_buffer = newbuffer;

        if( !m_enabled && m_buffer.hasRemaining() )
            enable();
        else if( m_enabled && !m_buffer.hasRemaining() )
            disable();

        bufferReplaced();

        // Ready state and data do no change, thus the master does not need to be informed.

        return oldbuffer;
    }


    @Override
    public void execute() throws IOException
    {
        if( m_buffer.position()>m_compactthreshold )
        {
            flip();
            m_buffer.compact();
            m_mark = 0;
        }

        retrieveData();

        if( m_enabled && !m_buffer.hasRemaining() && m_mark==0 )
            disable();
    }


    private void flip()
    {
        m_buffer.limit( m_buffer.position() );
        m_buffer.position( m_mark );
    }


    @Override
    public boolean hasData()
    {
        return m_buffer.position()>m_mark;
    }


    @Override
    public ByteBuffer startDataProcessing()
    {
        flip();

        return m_buffer;
    }


    @Override
    public void finishDataProcessing()
    {
        m_mark = m_buffer.position();
        m_buffer.position( m_buffer.limit() );
        m_buffer.limit( m_buffer.capacity() );

        clearUnprocessed();

        if( !m_enabled && ( m_buffer.hasRemaining() || m_mark>0 ) )
            enable();
    }

}
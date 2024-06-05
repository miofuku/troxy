package reptor.distrbt.io.net;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.function.IntFunction;

import com.google.common.base.Preconditions;

import reptor.distrbt.io.CompactingSinkBuffers;
import reptor.distrbt.io.net.Network.NetworkSource;


public class NetworkSourceBuffer extends AbstractNetworkSourceBuffer
{

    public NetworkSourceBuffer(NetworkSource source, IntFunction<ByteBuffer> buffac, int minbufsize)
    {
        this( source, buffac.apply( minbufsize ), buffac );
    }


    public NetworkSourceBuffer(NetworkSource source, ByteBuffer buffer, IntFunction<ByteBuffer> buffac)
    {
        super( source, buffer, buffac );

        CompactingSinkBuffers.clear( m_buffer );
    }


    @Override
    public ByteBuffer replaceBuffer(ByteBuffer newbuffer)
    {
        ByteBuffer oldbuffer = m_buffer;

        CompactingSinkBuffers.replaceBuffer( m_buffer, newbuffer );

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
    public int getStateSize()
    {
        return CompactingSinkBuffers.getStateSize( m_buffer );
    }


    @Override
    public void saveState(ByteBuffer dst)
    {
        CompactingSinkBuffers.saveState( m_buffer, dst );
    }


    @Override
    public void installState(ByteBuffer src)
    {
        Preconditions.checkState( !isActivated() );

        CompactingSinkBuffers.installState( m_buffer, src );
    }


    @Override
    public void execute() throws IOException
    {
        retrieveData();

        if( m_enabled && !m_buffer.hasRemaining() )
            disable();
    }


    @Override
    public boolean hasData()
    {
        return CompactingSinkBuffers.hasData( m_buffer );
    }


    @Override
    public ByteBuffer startDataProcessing()
    {
        return CompactingSinkBuffers.startDataProcessing( m_buffer );
    }


    @Override
    public void finishDataProcessing()
    {
        CompactingSinkBuffers.finishDataProcessing( m_buffer );

        clearUnprocessed();

        if( !m_enabled && m_buffer.hasRemaining() )
            enable();
    }

}
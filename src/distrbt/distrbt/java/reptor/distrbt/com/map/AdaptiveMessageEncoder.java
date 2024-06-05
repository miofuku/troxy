package reptor.distrbt.com.map;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayDeque;
import java.util.Queue;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import reptor.distrbt.com.MessageMapper;
import reptor.distrbt.com.NetworkMessage;
import reptor.distrbt.io.AdaptiveDataSource;


public class AdaptiveMessageEncoder extends AbstractMessageConverter<NetworkMessageSink, AdaptiveDataSource>
                                    implements NetworkMessageSink, AdaptiveDataSource
{

    private static final Logger s_logger = LoggerFactory.getLogger( AdaptiveMessageEncoder.class );

    private final Queue<NetworkMessage> m_inqueue    = new ArrayDeque<>();
    private int                         m_reqbufsize = NO_PENDING_DATA;


    public AdaptiveMessageEncoder(MessageMapper mapper)
    {
        super( mapper );
    }


    @Override
    public String toString()
    {
        return channelName() + "[MSG(O)]";
    }


    @Override
    public AdaptiveMessageEncoder getSink()
    {
        return this;
    }


    @Override
    public AdaptiveMessageEncoder getSource()
    {
        return this;
    }


    @Override
    public void enqueueMessage(NetworkMessage msg)
    {
        m_inqueue.add( msg );

        if( m_inqueue.size()==1 )
        {
            // This assumes that the message has been already serialized. However, it would be also possible to
            // set this not until the first call to m_mapper.writeMessageTo(), for instance.
            updateRequiredBufferSize( msg );

            if( m_isactivated )
                m_master.dataReady( this );
        }
    }


    @Override
    public void activate()
    {
        super.activate();

        if( !m_inqueue.isEmpty() )
        {
            updateRequiredBufferSize( m_inqueue.peek() );
            m_master.dataReady( this );
        }
    }


    @Override
    public boolean isReady()
    {
        return false;
    }


    @Override
    public void execute() throws IOException
    {
    }


    @Override
    public int getMinimumBufferSize()
    {
        return 0;
    }


    @Override
    public int getRequiredBufferSize()
    {
        return m_isactivated ? m_reqbufsize : NO_PENDING_DATA;
    }


    @Override
    public boolean hasData()
    {
        return m_isactivated && m_reqbufsize!=NO_PENDING_DATA;
    }


    @Override
    public boolean hasUnprocessedData()
    {
        return hasData();
    }


    @Override
    public ByteBuffer startDataProcessing()
    {
        if( !m_isactivated )
            return null;

        NetworkMessage msg = m_inqueue.poll();

        if( s_logger.isDebugEnabled() )
            s_logger.debug( "{} send message {} unbuffered", this, msg );

        updateRequiredBufferSize( m_inqueue.peek() );

        return m_mapper.outputBuffer( msg );
    }


    private void updateRequiredBufferSize(NetworkMessage next)
    {
        m_reqbufsize = next==null ? NO_PENDING_DATA : next.getMessageSize();
    }


    @Override
    public void finishDataProcessing()
    {
    }


    @Override
    public boolean canRetrieveData(boolean hasremaining, int bufsize)
    {
        return m_isactivated && bufsize>=m_reqbufsize;
    }


    @Override
    public void retrieveData(ByteBuffer dst) throws IOException
    {
        if( !m_isactivated )
            return;

        NetworkMessage msg;

        while( ( msg = m_inqueue.peek() )!=null )
        {
            if( !m_mapper.writeMessageTo( dst, msg ) )
                break;

            if( s_logger.isDebugEnabled() )
                s_logger.debug( "{} send message {} buffered (remaining {})", this, msg, dst.remaining() );

            m_inqueue.poll();
        }

        updateRequiredBufferSize( msg );
    }


    @Override
    public boolean canProcessData(boolean hasremaining, int bufsize)
    {
        return m_isactivated && ( !hasremaining && m_inqueue.size()==1 || canRetrieveData( hasremaining, bufsize ) );
    }


    @Override
    public ByteBuffer startDataProcessing(ByteBuffer dst) throws IOException
    {
        if( dst.position()==0 && m_inqueue.size()==1 )
            return startDataProcessing();
        else
        {
            retrieveData( dst );

            return dst;
        }
    }


    @Override
    public void adjustBuffer(int minbufsize)
    {
        throw new UnsupportedOperationException();
    }

}

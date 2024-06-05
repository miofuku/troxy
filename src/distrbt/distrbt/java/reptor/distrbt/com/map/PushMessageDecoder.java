package reptor.distrbt.com.map;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Preconditions;

import reptor.chronos.com.PushMessageSink;
import reptor.chronos.com.PushMessageSource;
import reptor.distrbt.com.MessageMapper;
import reptor.distrbt.com.NetworkMessage;
import reptor.distrbt.io.UnbufferedDataSink;
import reptor.distrbt.io.UnbufferedDataSinkStatus;


public class PushMessageDecoder extends AbstractMessageConverter<UnbufferedDataSink, PushMessageSource<NetworkMessage>>
                                implements UnbufferedDataSink, PushMessageSource<NetworkMessage>
{

    private static final Logger s_logger = LoggerFactory.getLogger( PushMessageDecoder.class );

    private final Object                m_srccntxt;

    private PushMessageSink<? super NetworkMessage> m_receiver;
    private UnbufferedDataSinkStatus                m_state = UnbufferedDataSinkStatus.BLOCKED;


    public PushMessageDecoder(MessageMapper mapper, Object srccntxt)
    {
        super( mapper );

        m_srccntxt = Objects.requireNonNull( srccntxt );
    }


    @Override
    public String toString()
    {
        return channelName() + "[MSG(I)]";
    }


    @Override
    public PushMessageDecoder getSink()
    {
        return this;
    }


    @Override
    public PushMessageDecoder getSource()
    {
        return this;
    }


    @Override
    public void initReceiver(PushMessageSink<? super NetworkMessage> receiver)
    {
        Preconditions.checkState( m_master!=null && m_receiver==null );

        m_receiver = Objects.requireNonNull( receiver );
    }


    @Override
    public void activate()
    {
        super.activate();

        if( m_state==UnbufferedDataSinkStatus.BLOCKED )
        {
            m_state = UnbufferedDataSinkStatus.CAN_PROCESS;

            m_master.dataReady( this );
        }
    }


    @Override
    public void deactivate()
    {
        super.deactivate();

        m_state = UnbufferedDataSinkStatus.BLOCKED;
    }


    @Override
    public boolean isReady()
    {
        return m_isactivated;
    }


    @Override
    public int getMinimumBufferSize()
    {
        return 0;
    }


    @Override
    public UnbufferedDataSinkStatus canProcessData()
    {
        return m_state;
    }


    @Override
    public void processData(ByteBuffer src) throws IOException
    {
        if( !m_isactivated )
            return;

        try
        {
            while( true )
            {
                NetworkMessage msg = m_mapper.tryReadMessageFrom( src, m_srccntxt );

                if( msg==null )
                    break;

                if( s_logger.isDebugEnabled() )
                    s_logger.debug( "{} received message {}", this, msg );

                m_receiver.enqueueMessage( msg );
            }

            m_state = src.hasRemaining() ? UnbufferedDataSinkStatus.WAIT_FOR_DATA : UnbufferedDataSinkStatus.CAN_PROCESS;
        }
        catch( IOException e )
        {
            // TODO: NetworkConnection looks for exception causes to distinguish 'regularly' closed connections
            //       from real errors. Therefore, a cause has to be set.
            throw new IOException( e );
        }
    }

}

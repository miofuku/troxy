package reptor.replct.agree.order;

import java.io.IOException;
import java.nio.ByteBuffer;

import reptor.distrbt.com.MessageDigestSink;
import reptor.distrbt.com.MessageDigester;
import reptor.distrbt.com.MessageMapper;
import reptor.distrbt.common.data.Data;
import reptor.distrbt.common.data.ImmutableData;
import reptor.replct.agree.order.OrderMessages.CommandContainer;


public abstract class AbstractOrderHolderMessage extends AbstractOrderMessage
{

    public enum OrderMessageVariant
    {
        COMMAND,
        DATA
    }


    protected final CommandContainer m_command;

    // TODO: ExternalMessageElement - getSize(), writeTo()
    protected final Data             m_data;


    protected AbstractOrderHolderMessage(short sender, long orderno, int viewno, CommandContainer command)
    {
        super( sender, orderno, viewno );

        assert command!=null;

        m_command = command;
        m_data    = null;
    }


    protected AbstractOrderHolderMessage(short sender, long orderno, int viewno, Data data)
    {
        super( sender, orderno, viewno );

        assert data!=null;

        m_command = null;
        m_data    = data;
    }


    @Override
    public void writeTypeContentTo(ByteBuffer out, MessageMapper mapper)
    {
        super.writeTypeContentTo( out, mapper );

        if( m_command!=null )
            mapper.writeMessageTo( out, m_command );
        else
        {
            out.putInt( m_data.size() );
            m_data.writeTo( out );
        }
    }


    @Override
    public int calculateTypePlainPrefixSize(MessageMapper mapper)
    {
        int size = super.calculateTypePlainPrefixSize( mapper );

        if( m_command==null )
            size += Integer.BYTES + m_data.size();

        return size;
    }


    @Override
    public int calculateTypeContentSize(MessageMapper mapper)
    {
        return m_command==null ? 0 : mapper.calculateMessageSize( m_command );
    }


    protected AbstractOrderHolderMessage(OrderMessageVariant variant, ByteBuffer in, MessageMapper mapper) throws IOException
    {
        super( in );

        if( variant==OrderMessageVariant.COMMAND )
        {
            m_data    = null;
            m_command = (CommandContainer) mapper.readMessageFrom( in );
        }
        else
        {
            m_data    = ImmutableData.readFrom( in, in.getInt() );
            m_command = null;
        }
    }


    @Override
    public void prepareDigestion(MessageDigester digester)
    {
        super.prepareDigestion( digester );

        if( m_command!=null )
            digester.prepareInnerMessageForDigestion( m_command );
    }


    @Override
    public void digestTypeContentTo(MessageDigestSink sink)
    {
        if( m_command!=null )
            sink.putInnerMessage( m_command );
    }


    public CommandContainer getCommand()
    {
        if( m_command==null )
            throw new UnsupportedOperationException();

        return m_command;
    }


    public Data getData()
    {
        if( m_data==null )
            throw new UnsupportedOperationException();

        return m_data;
    }


    @Override
    public String toString()
    {
        return String.format( "{%s|%s}", idString(), m_command==null ? m_data.size() : m_command );
    }

}

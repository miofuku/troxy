package reptor.replct.agree.order;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Objects;

import reptor.distrbt.com.AbstractNetworkMessage;
import reptor.distrbt.com.MessageMapper;


public abstract class AbstractOrderMessage extends AbstractNetworkMessage implements OrderNetworkMessage
{

    protected final short           m_sender;
    protected final long            m_orderno;
    protected final int             m_viewno;


    protected AbstractOrderMessage(short sender, long orderno, int viewno)
    {
        m_sender  = sender;
        m_orderno = orderno;
        m_viewno  = viewno;
    }


    @Override
    public void writeTypeContentTo(ByteBuffer out, MessageMapper mapper)
    {
        super.writeTypeContentTo( out, mapper );

        out.putShort( m_sender );
        out.putLong( m_orderno );
        out.putInt( m_viewno );
    }


    @Override
    public int calculateTypePlainPrefixSize(MessageMapper mapper)
    {
        return super.calculateTypePlainPrefixSize( mapper ) + Short.BYTES + Long.BYTES + Integer.BYTES;
    }


    protected AbstractOrderMessage(ByteBuffer in) throws IOException
    {
        super( in );

        m_sender  = in.getShort();
        m_orderno = in.getLong();
        m_viewno  = in.getInt();
    }


    @Override
    public short getSender()
    {
        return m_sender;
    }


    @Override
    public final long getOrderNumber()
    {
        return m_orderno;
    }


    @Override
    public final int getViewNumber()
    {
        return m_viewno;
    }


    @Override
    public boolean equals(Object obj)
    {
        if( obj==this )
            return true;

        if( obj==null || !( obj instanceof AbstractOrderMessage ) )
            return false;

        // TODO: Include payload.
        AbstractOrderMessage other = (AbstractOrderMessage) obj;

        return other.getTypeID()==getTypeID() && other.m_sender==m_sender &&
               other.m_orderno==m_orderno && other.m_viewno==m_viewno;
    }


    @Override
    public int hashCode()
    {
        return Objects.hash( getTypeID(), m_sender, m_orderno, m_viewno );
    }


    @Override
    public String toString()
    {
        return String.format( "{%s}", idString() );
    }


    protected String idString()
    {
        return String.format( "%s|%d:%d-%d", getTypeName(), m_sender, m_orderno, m_viewno );

    }


    protected String getTypeName()
    {
        return getClass().getSimpleName().toUpperCase();
    }

}

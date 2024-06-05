package reptor.replct.agree.view;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Objects;

import reptor.distrbt.com.AbstractNetworkMessage;
import reptor.distrbt.com.MessageMapper;


public abstract class AbstractViewChangeMessage extends AbstractNetworkMessage implements ViewChangeNetworkMessage
{

    protected final short           m_sender;
    protected final short           m_shardno;
    protected final int             m_viewno;


    protected AbstractViewChangeMessage(short sender, short shardno, int viewno)
    {
        m_sender  = sender;
        m_shardno = shardno;
        m_viewno  = viewno;
    }


    @Override
    public void writeTypeContentTo(ByteBuffer out, MessageMapper mapper)
    {
        super.writeTypeContentTo( out, mapper );

        out.putShort( m_sender );
        out.putShort( m_shardno );
        out.putInt( m_viewno );
    }


    @Override
    public int calculateTypePlainPrefixSize(MessageMapper mapper)
    {
        return super.calculateTypePlainPrefixSize( mapper ) + Short.BYTES + Short.BYTES + Integer.BYTES;
    }


    protected AbstractViewChangeMessage(ByteBuffer in) throws IOException
    {
        super( in );

        m_sender  = in.getShort();
        m_shardno = in.getShort();
        m_viewno  = in.getInt();
    }


    @Override
    public short getSender()
    {
        return m_sender;
    }


    @Override
    public short getShardNumber()
    {
        return m_shardno;
    }


    @Override
    public int getViewNumber()
    {
        return m_viewno;
    }


    @Override
    public boolean equals(Object obj)
    {
        if( obj==this )
            return true;

        if( obj==null || !( obj instanceof AbstractViewChangeMessage ) )
            return false;

        // TODO: Include payload.
        AbstractViewChangeMessage other = (AbstractViewChangeMessage) obj;

        return other.getTypeID()==getTypeID() && other.m_sender==m_sender &&
               other.m_shardno==m_shardno && other.m_viewno==m_viewno;
    }


    @Override
    public int hashCode()
    {
        return Objects.hash( getTypeID(), m_sender, m_shardno, m_viewno );
    }


    @Override
    public String toString()
    {
        return String.format( "{%s}", idString() );
    }


    protected String idString()
    {
        return String.format( "%s|%d-%d:%d", getTypeName(), m_sender, m_shardno, m_viewno );
    }


    protected String getTypeName()
    {
        return getClass().getSimpleName().toUpperCase();
    }

}

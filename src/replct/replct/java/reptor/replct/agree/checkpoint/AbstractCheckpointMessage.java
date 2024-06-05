package reptor.replct.agree.checkpoint;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Objects;

import reptor.distrbt.com.AbstractNetworkMessage;
import reptor.distrbt.com.MessageMapper;


public abstract class AbstractCheckpointMessage extends AbstractNetworkMessage implements CheckpointNetworkMessage
{

    protected final short m_sender;
    protected final short m_shardno;
    protected final long  m_orderno;


    protected AbstractCheckpointMessage(short sender, short shardno, long orderno)
    {
        m_sender  = sender;
        m_shardno = shardno;
        m_orderno = orderno;
    }


    @Override
    public void writeTypeContentTo(ByteBuffer out, MessageMapper mapper)
    {
        super.writeTypeContentTo( out, mapper );

        out.putShort( m_sender );
        out.putShort( m_shardno );
        out.putLong( m_orderno );
    }


    @Override
    public int calculateTypePlainPrefixSize(MessageMapper mapper)
    {
        return super.calculateTypePlainPrefixSize( mapper ) + Short.BYTES*2 + Long.BYTES;
    }


    protected AbstractCheckpointMessage(ByteBuffer in) throws IOException
    {
        super( in );

        m_sender  = in.getShort();
        m_shardno = in.getShort();
        m_orderno = in.getLong();
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
    public final long getOrderNumber()
    {
        return m_orderno;
    }


    @Override
    public boolean equals(Object obj)
    {
        if( obj==this )
            return true;

        if( obj==null || !( obj instanceof AbstractCheckpointMessage ) )
            return false;

        // TODO: Include payload.
        AbstractCheckpointMessage other = (AbstractCheckpointMessage) obj;

        return other.getTypeID()==getTypeID() && other.m_sender==m_sender &&
               other.m_shardno==m_shardno && other.m_orderno==m_orderno;
    }


    @Override
    public int hashCode()
    {
        return Objects.hash( getTypeID(), m_sender, m_shardno, m_orderno );
    }


    @Override
    public String toString()
    {
        return String.format( "{%s}", idString() );
    }


    protected abstract String getTypeName();


    protected String idString()
    {
        return String.format( "%s|%d-%d:%d", getTypeName(), m_sender, m_shardno, m_orderno );
    }

}

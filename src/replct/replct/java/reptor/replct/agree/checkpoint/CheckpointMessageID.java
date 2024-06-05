package reptor.replct.agree.checkpoint;

import java.nio.ByteBuffer;
import java.util.Objects;


public class CheckpointMessageID
{

    private final int   m_typeid;
    private final short m_sender;
    private final short m_shardno;
    private final long  m_orderno;


    public static CheckpointMessageID createFor(CheckpointNetworkMessage msg)
    {
        return new CheckpointMessageID( msg.getMessageTypeID(), msg.getSender(), msg.getShardNumber(), msg.getOrderNumber() );
    }


    public CheckpointMessageID(int typeid, short sender, short shardno, long orderno)
    {
        m_typeid  = typeid;
        m_sender  = sender;
        m_shardno = shardno;
        m_orderno = orderno;
    }


    public int getMessageTypeID()
    {
        return m_typeid;
    }


    public short getSender()
    {
        return m_sender;
    }


    public short getShardNumber()
    {
        return m_shardno;
    }


    public long getOrderNumber()
    {
        return m_orderno;
    }


    public int getSize()
    {
        return Short.BYTES + Short.BYTES + Long.BYTES;
    }


    public void writeTo(ByteBuffer out)
    {
        out.putShort( m_sender );
        out.putShort( m_shardno );
        out.putLong( m_orderno );
    }


    public static CheckpointMessageID readFrom(int typeid, ByteBuffer in)
    {
        return new CheckpointMessageID( typeid, in.getShort(), in.getShort(), in.getLong() );
    }


    @Override
    public boolean equals(Object obj)
    {
        if( obj==this )
            return true;

        if( obj==null || obj.getClass()!=getClass() )
            return false;

        CheckpointMessageID other = (CheckpointMessageID) obj;

        return other.m_typeid==m_typeid && other.m_sender==m_sender &&
               other.m_shardno==m_shardno && other.m_orderno==m_orderno;
    }


    @Override
    public int hashCode()
    {
        return Objects.hash( m_typeid, m_sender, m_shardno, m_orderno );
    }


    public static int hashCodeFor(CheckpointNetworkMessage msg)
    {
        return Objects.hash( msg.getTypeID(), msg.getSender(), msg.getShardNumber(), msg.getOrderNumber() );
    }


    @Override
    public String toString()
    {
        return String.format( "%08X|%d-%d:%d", m_typeid, m_sender, m_shardno, m_orderno );
    }


    public static String toStringFor(CheckpointNetworkMessage msg)
    {
        return String.format( "%08X|%d-%d:%d", msg.getTypeID(), msg.getSender(), msg.getShardNumber(), msg.getOrderNumber() );
    }

}

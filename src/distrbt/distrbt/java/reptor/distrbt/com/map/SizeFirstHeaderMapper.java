package reptor.distrbt.com.map;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Objects;

import reptor.chronos.Orphic;
import reptor.distrbt.com.NetworkMessage;
import reptor.distrbt.com.NetworkMessageRegistry;


public class SizeFirstHeaderMapper implements Orphic, CommonHeaderMapper
{

    // msgsize : int, typemagic : byte
    private static final int HEADER_SIZE = Integer.BYTES + Byte.BYTES;

    private final NetworkMessageRegistry m_msgreg;


    public SizeFirstHeaderMapper(NetworkMessageRegistry msgreg)
    {
        m_msgreg = Objects.requireNonNull( msgreg );
    }


    @Override
    public void writeCommonHeaderTo(ByteBuffer out, NetworkMessage msg)
    {
        out.putInt( msg.getMessageSize() );
        out.put( m_msgreg.magic( msg.getTypeID() ) );
    }


    @Override
    public int calculateCommonHeaderSize(NetworkMessage msg)
    {
        return HEADER_SIZE;
    }


    @Override
    public int getMaximumHeaderSize()
    {
        return HEADER_SIZE;
    }


    @Override
    public boolean tryReadCommonHeader(ByteBuffer in, CommonHeaderHolder holder) throws IOException
    {
        if( in.remaining()<HEADER_SIZE )
            return false;

        int  msgsize = in.getInt( in.position() );
        byte magic   = in.get( in.position()+Integer.BYTES );

        if( msgsize<HEADER_SIZE )
            throw new IOException();

        holder.setHeaderInformation( m_msgreg.typeDeserializer( magic ), msgsize, HEADER_SIZE );

        return true;
    }

}

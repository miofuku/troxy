package reptor.start;

import java.io.IOException;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.util.Date;


public class ControlPort
{

    private final SocketAddress m_addr;


    public ControlPort(SocketAddress addr)
    {
        m_addr = addr;
    }


    public void waitForGo() throws IOException
    {
        try( DatagramChannel channel = DatagramChannel.open() )
        {
            channel.socket().bind( m_addr );

            System.out.println( "<<READY>>" );

            channel.receive( ByteBuffer.allocate( 64 ) );

            System.out.println( "Start at " + new Date() );
        }
    }

}

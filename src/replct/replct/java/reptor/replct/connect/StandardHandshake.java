package reptor.replct.connect;

import java.nio.ByteBuffer;

import reptor.distrbt.com.handshake.HandshakeMagic;
import reptor.distrbt.com.handshake.HandshakeRole;
import reptor.distrbt.com.handshake.TwoPhaseHandshake;
import reptor.distrbt.io.net.BufferedNetworkState;
import reptor.distrbt.io.ssl.SslState;


public class StandardHandshake extends TwoPhaseHandshake<RemoteEndpoint>
{

    //-------------------------------------//
    //         Members and Subjects        //
    //-------------------------------------//

    private final short             m_locno;

    private RemoteEndpoint          m_remote;
    private StandardHandshakeState  m_hsstate;


    public StandardHandshake(short locno)
    {
        m_locno = locno;
    }


    //-------------------------------------//
    //              Properties             //
    //-------------------------------------//

    @Override
    protected int getMinimumBufferSize()
    {
        return Math.max( sizeAnnouncement(), sizeAcknowledgement() );
    }


    @Override
    public RemoteEndpoint getRemote()
    {
        return m_remote;
    }


    @Override
    public String getConnectionDescription()
    {
        short remno = m_remote==null ? -1 : m_remote.getProcessNumber();
        short netno = m_remote==null ? -1 : m_remote.getNetworkNumber();

        return role()==HandshakeRole.CONNECTOR ? String.format( "%d -> %d (%d)", m_locno, remno, netno ) :
                                                 String.format( "%d <- %d (%d)", remno, m_locno, netno );
    }


    @Override
    public StandardHandshakeState getState()
    {
        return m_hsstate;
    }



    //-------------------------------------//
    //            Configuration            //
    //-------------------------------------//

    @Override
    public void reset(boolean clear)
    {
        super.reset( clear );

        m_hsstate = null;

        if( clear )
            m_remote = null;
    }


    @Override
    public void initConnection(Object args)
    {
        super.initConnection( args );

        StandardConnectionArguments ca = (StandardConnectionArguments) args;

        m_remote = ca.getRemoteEndpoint();
    }


    @Override
    public void saveState(BufferedNetworkState netstate, SslState sslstate)
    {
        super.saveState( netstate, sslstate );

        m_hsstate = new StandardHandshakeState( m_remote, role()==HandshakeRole.CONNECTOR, netstate, sslstate );
    }


    //-------------------------------------//
    //               Protocol              //
    //-------------------------------------//

    @Override
    protected int sizeAnnouncement()
    {
        return Byte.BYTES + Short.BYTES*2;
    }


    @Override
    protected void writeAnnouncement(ByteBuffer buffer)
    {
        buffer.put( HandshakeMagic.STD );
        buffer.putShort( m_locno );
        buffer.putShort( m_remote.getNetworkNumber() );
    }


    @Override
    protected void readAnnouncement(ByteBuffer buffer)
    {
        byte magic = buffer.get();

        if( magic!=HandshakeMagic.STD )
            throw new UnsupportedOperationException( String.valueOf( magic ) );

        m_remote = new RemoteEndpoint( buffer.getShort(), buffer.getShort() );
    }


    @Override
    protected int sizeAcknowledgement()
    {
        return Byte.BYTES;
    }


    @Override
    protected void writeAcknowledgement(ByteBuffer buffer)
    {
        buffer.put( (byte) 0 );
    }


    @Override
    protected void readAcknowledgement(ByteBuffer buffer)
    {
        buffer.get();
    }

}

package reptor.distrbt.com.handshake;

import java.io.IOException;
import java.nio.ByteBuffer;


public abstract class TwoPhaseHandshake<R> extends AbstractHandshake<R>
{

    //-------------------------------------//
    //                Types                //
    //-------------------------------------//

    public enum Status
    {
        INITIATED,
        ANNOUNCE,
        ACKNOWLEDGE,
    }


    //-------------------------------------//
    //         Members and Subjects        //
    //-------------------------------------//

    private Status   m_status;


    //-------------------------------------//
    //            Configuration            //
    //-------------------------------------//

    @Override
    public void reset(boolean clear)
    {
        super.reset( clear );

        m_status = Status.INITIATED;
    }


    //-------------------------------------//
    //               Protocol              //
    //-------------------------------------//

    @Override
    protected boolean executeConnectorPhases(ByteBuffer buffer) throws IOException
    {
        switch( m_status )
        {
        case INITIATED:
            prepareOutgoing( sizeAnnouncement() );

            advanceStatus( Status.ANNOUNCE );
            log( "announce", "", null );

            return false;

        case ANNOUNCE:
            writeAnnouncement( buffer );

            prepareIncoming( sizeAcknowledgement() );

            advanceStatus( Status.ACKNOWLEDGE );
            log( "await acknowledgement", "", null );

            return false;

        case ACKNOWLEDGE:
            writeAcknowledgement( buffer );

            return true;

        default:
            throw new IllegalStateException();
        }
    }


    @Override
    protected boolean executeAcceptorPhases(ByteBuffer buffer) throws IOException
    {
        switch( m_status )
        {
        case INITIATED:
            prepareIncoming( sizeAnnouncement() );

            advanceStatus( Status.ANNOUNCE );
            log( "await announcement", "", null );

            return false;

        case ANNOUNCE:
            readAnnouncement( buffer );

            prepareOutgoing( sizeAcknowledgement() );

            advanceStatus( Status.ACKNOWLEDGE );
            log( "acknowledge", "", null );

            return false;

        case ACKNOWLEDGE:
            writeAcknowledgement( buffer );

            return true;

        default:
            throw new IllegalStateException();
        }
    }


    private void advanceStatus(TwoPhaseHandshake.Status status)
    {
        m_status = status;
    }


    protected abstract int      sizeAnnouncement();

    protected abstract void     writeAnnouncement(ByteBuffer buffer);

    protected abstract void     readAnnouncement(ByteBuffer buffer);

    protected abstract int      sizeAcknowledgement();

    protected abstract void     writeAcknowledgement(ByteBuffer buffer);

    protected abstract void     readAcknowledgement(ByteBuffer buffer);

}

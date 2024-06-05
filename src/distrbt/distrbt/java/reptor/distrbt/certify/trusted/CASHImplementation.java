package reptor.distrbt.certify.trusted;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;

import com.google.common.base.Preconditions;

import reptor.distrbt.certify.trusted.TrinxCommands.TrinxCertificationCommand;
import reptor.distrbt.com.Message;
import reptor.distrbt.common.data.Data;
import reptor.distrbt.common.data.MutableData;


public class CASHImplementation implements TrinxImplementation
{

    private Object  m_sync = new Object();
    private boolean m_isconnected;


    @Override
    public DeviceCASH createTrinx(short tssid, int ncounters) throws IOException
    {
        if( ncounters!=1 )
            throw new UnsupportedOperationException();

        synchronized( m_sync )
        {
            Preconditions.checkState( !m_isconnected );

            DeviceCASH cash = new DeviceCASH( tssid );
            cash.open();

            m_isconnected = true;

            return cash;
        }
    }


    protected void closeConnection()
    {
        synchronized( m_sync )
        {
            m_isconnected = false;
        }
    }


    @Override
    public String toString()
    {
        return "CASH";
    }


    @Override
    public int getMacCertificateSize()
    {
        return -1;
    }


    @Override
    public int getCounterCertificateSize()
    {
        return DeviceCASH.CERTIFICATESIZE;
    }


    public class DeviceCASH implements Trinx
    {

        private static final int    CERTIFICATESIZE = 32;

        private static final String DEVICE          = "/dev/counter";

        private final short m_tssid;

        private ByteBuffer  m_signwritebuf;
        private ByteBuffer  m_signreadbuf;
        private ByteBuffer  m_verifywritebuf;
        private ByteBuffer  m_verifyreadbuf;
        private FileChannel m_cash;


        public DeviceCASH(short tssid)
        {
            m_tssid = tssid;

            // TODO: Use direct buffer?
            m_signwritebuf   = ByteBuffer.allocate( 36 );
            m_signreadbuf    = ByteBuffer.allocate( 40 );
            m_verifywritebuf = ByteBuffer.allocate( 80 );
            m_verifyreadbuf  = ByteBuffer.allocate( 4 );
        }


        @SuppressWarnings("resource")
        public void open() throws IOException
        {
            m_cash = new RandomAccessFile( DEVICE, "rw" ).getChannel();
        }


        @Override
        public void close() throws IOException
        {
            try
            {
                m_cash.close();
            }
            finally
            {
                closeConnection();
            }
        }


        @Override
        public String getImplementationName()
        {
            return CASHImplementation.this.toString();
        }


        @Override
        public short getID()
        {
            return m_tssid;
        }


        @Override
        public int getMacCertificateSize()
        {
            return -1;
        }


        @Override
        public int getCounterCertificateSize()
        {
            return CERTIFICATESIZE;
        }


        @Override
        public int getNumberOfCounters()
        {
            return 1;
        }


        @Override
        public TrustedCounterValue counterValue(int ctrno)
        {
            throw new UnsupportedOperationException();
        }



        public void createIndependentCertificate(Data msgdata, int ctrno, long highval, long lowval, MutableData out)
        {
            if( ctrno!=0 )
                throw new IllegalArgumentException();
            if( highval!=0 )
                throw new UnsupportedOperationException();
            if( lowval>Integer.MAX_VALUE )
                throw new UnsupportedOperationException();

            try
            {
                /* -- WRITE -- */
                m_signwritebuf.clear();
                m_signwritebuf.putInt( 0 );
                msgdata.writeTo( m_signwritebuf );

                m_signwritebuf.flip();
                m_cash.write( m_signwritebuf );

                /* -- READ -- */
                m_signreadbuf.clear();
                m_cash.read( m_signreadbuf );

                m_signreadbuf.flip();
                m_signreadbuf.limit( CERTIFICATESIZE );
                out.readFrom( m_signreadbuf );
                out.adaptSlice( CERTIFICATESIZE );
            }
            catch( IOException e )
            {
                throw new IllegalStateException( e );
            }
        }


        public boolean verifyIndependentCertificate(Data msgdata, Data certdata, int certoffset,
                                                    short tmid, int ctrno, long highval, long lowval)
        {
            if( ctrno!=0 )
                throw new IllegalArgumentException();
            if( highval!=0 )
                throw new UnsupportedOperationException();
            if( lowval>Integer.MAX_VALUE )
                throw new UnsupportedOperationException();

            try
            {
                m_verifywritebuf.clear();
                m_verifywritebuf.putInt( 1 );
                msgdata.writeTo( m_verifywritebuf );
                certdata.writeTo( m_verifywritebuf, certoffset, CERTIFICATESIZE );
                m_verifywritebuf.putInt( (int) lowval );
                m_verifywritebuf.putInt( 0 );
                m_verifywritebuf.putInt( tmid );

                m_verifywritebuf.flip();
                m_cash.write( m_verifywritebuf );

                m_verifyreadbuf.clear();
                m_cash.read( m_verifyreadbuf );

                m_verifyreadbuf.flip();

                return m_verifyreadbuf.getInt() == 1;
            }
            catch( IOException e )
            {
                throw new IllegalStateException( e );
            }
        }

        @Override
        public void executeCommand(Message msg)
        {
            TrinxCertificationCommand cmd = (TrinxCertificationCommand) msg;

            switch( cmd.getTypeID() )
            {
            case TrinxCommands.CREATE_INDEPENDENT_CERTIFICATE_ID:
                createIndependentCertificate( cmd.getMessageData(), cmd.getCounterNumber(), cmd.getHighValue(), cmd.getLowValue(),
                                              cmd.getCertificateBuffer() );
                break;
            case TrinxCommands.VERIFY_INDEPENDENT_CERTIFICATE_ID:
                boolean res = verifyIndependentCertificate( cmd.getMessageData(), cmd.getCertificateBuffer(),
                                                            0, cmd.getTssID(), cmd.getCounterNumber(), cmd.getHighValue(), cmd.getLowValue() );
                cmd.result( res ? TrinxCommands.CERTIFICATE_VALID : TrinxCommands.CERTIFICATE_INVALID );
                break;
            default:
                throw new UnsupportedOperationException();
            }
        }


        @Override
        public void touch()
        {
        }

    }

}

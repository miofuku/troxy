package reptor.distrbt.certify.trusted;

import java.nio.ByteBuffer;

import reptor.distrbt.certify.mac.Authenticating;
import reptor.distrbt.certify.trusted.TrinxCommands.TrinxCertificationCommand;
import reptor.distrbt.certify.trusted.TrinxCommands.TrinxCommandBatch;
import reptor.distrbt.com.Message;
import reptor.distrbt.common.data.Data;
import reptor.distrbt.common.data.MutableData;


public class JniTrinxImplementation implements TrinxImplementation
{

    private final String m_libname;
    private final String m_enclavepath;
    private final byte[] m_sharedkey;
    private final int    m_tmcertsize;
    private final int    m_tccertsize;

    private static native long    nativeCreate(String path, short tmid, int ncounters, byte[] key);
    private static native void    nativeTerminate(long eid);

    private static native int     nativeNumberOfCounters(long eid);
    private static native int     nativeCounterCertificateSize(long eid);
    private static native int     nativeMacCertificateSize(long eid);

    private static native void    enclaveTouch(long eid);

    private static native byte nativeExecuteCommand(
            long eid, int cmdid, byte[] msg, int msgoff, int msgsize, byte[] cert, int certoff,
            short tssid, int ctrno, long highval, long lowval, long prevhigh, long prevlow);

    private static native void nativeExecuteSerializedCommand(long eid, byte[] buf, int bufoff, byte[] msg, byte[] cert);

    private static native void nativeExecuteBatch(long eid, byte[] buf, int bufoff, int ndata, byte[][] data);


    public JniTrinxImplementation(String libname, String enclavepath, String secret)
    {
        System.loadLibrary( libname );

        m_libname     = libname;
        m_enclavepath = enclavepath;
        m_sharedkey   = Authenticating.createKey( secret ).getEncoded();

        long e0id = nativeCreate( enclavepath, (short) 0, 1, m_sharedkey );

        m_tmcertsize = nativeMacCertificateSize( e0id );
        m_tccertsize = nativeCounterCertificateSize( e0id );

        nativeTerminate( e0id );
    }


    @Override
    public JniTrinx createTrinx(short tssid, int ncounters)
    {
        return new JniTrinx( tssid, nativeCreate( m_enclavepath, tssid, ncounters, m_sharedkey ) );
    }


    @Override
    public String toString()
    {
        return String.format( "JniTrinx(%s)", m_libname );
    }


    @Override
    public int getMacCertificateSize()
    {
        return m_tmcertsize;
    }


    @Override
    public int getCounterCertificateSize()
    {
        return m_tccertsize;
    }


    public class JniTrinx implements Trinx
    {

        private final short m_tssid;
        private final long  m_eid;

        public JniTrinx(short tssid, long eid)
        {
            m_tssid = tssid;
            m_eid   = eid;
        }


        @Override
        public void close() throws Exception
        {
            nativeTerminate( m_eid );
        }


        @Override
        public String getImplementationName()
        {
            return JniTrinxImplementation.this.toString();
        }


        @Override
        public short getID()
        {
            return m_tssid;
        }


        @Override
        public int getMacCertificateSize()
        {
            return m_tmcertsize;
        }


        @Override
        public int getCounterCertificateSize()
        {
            return m_tccertsize;
        }


        @Override
        public int getNumberOfCounters()
        {
            return nativeNumberOfCounters( m_eid );
        }


        @Override
        public TrustedCounterValue counterValue(int ctrno)
        {
            throw new UnsupportedOperationException();
        }


        @Override
        public void executeCommand(Message msg)
        {
            switch( msg.getTypeID() )
            {
            case TrinxCommands.CREATE_TGROUP_CERTIFICATE_ID:
            case TrinxCommands.VERIFY_TGROUP_CERTIFICATE_ID:
            case TrinxCommands.CREATE_TMAC_CERTIFICATE_ID:
            case TrinxCommands.VERIFY_TMAC_CERTIFICATE_ID:
            case TrinxCommands.CREATE_INDEPENDENT_CERTIFICATE_ID:
            case TrinxCommands.VERIFY_INDEPENDENT_CERTIFICATE_ID:
            case TrinxCommands.CREATE_CONTINUING_CERTIFICATE_ID:
            case TrinxCommands.VERIFY_CONTINUING_CERTIFICATE_ID:
                executeUnserializedCommand( (TrinxCertificationCommand) msg );
                break;
            case TrinxCommands.BATCH_ID:
                executeBatch( (TrinxCommandBatch) msg );
                break;
            default:
                throw new UnsupportedOperationException( msg.toString() );
            }
        }


        public void executeUnserializedCommand(TrinxCertificationCommand cmd)
        {
            Data        msgdata = cmd.getMessageData();
            MutableData certbuf = cmd.getCertificateBuffer();

            int cmdid = cmd.getTypeID();
            short tssid = 0; int ctrno = 0;
            long highval = 0, lowval = 0, prevhigh = 0, prevlow = 0;

            switch( cmdid )
            {
            case TrinxCommands.VERIFY_CONTINUING_CERTIFICATE_ID:
                prevhigh = cmd.getPreviousHighValue();
                prevlow  = cmd.getPreviousLowValue();
            case TrinxCommands.CREATE_CONTINUING_CERTIFICATE_ID:
            case TrinxCommands.VERIFY_INDEPENDENT_CERTIFICATE_ID:
            case TrinxCommands.CREATE_INDEPENDENT_CERTIFICATE_ID:
                highval = cmd.getHighValue();
                lowval  = cmd.getLowValue();
                ctrno   = cmd.getCounterNumber();
            case TrinxCommands.VERIFY_TMAC_CERTIFICATE_ID:
            case TrinxCommands.CREATE_TMAC_CERTIFICATE_ID:
                tssid = cmd.getTssID();
            }

            byte res = nativeExecuteCommand( m_eid, cmdid, msgdata.array(), msgdata.arrayOffset(), msgdata.size(),
                                             certbuf.array()==msgdata.array() ? null : certbuf.array(), certbuf.arrayOffset(),
                                             tssid, ctrno, highval, lowval, prevhigh, prevlow );

            if( TrinxCommands.isVerification( cmdid ) )
                cmd.result( res );
        }


        public void executeSerializedCommand(TrinxCertificationCommand cmd)
        {
            ByteBuffer  buffer  = cmd.getBuffer();
            Data        msgdata = cmd.getMessageData();
            MutableData certbuf = cmd.getCertificateBuffer();

            nativeExecuteSerializedCommand( m_eid, buffer.array(), buffer.arrayOffset()+buffer.position(),
                                            msgdata.array(), certbuf.array()==msgdata.array() ? null : certbuf.array() );

            if( cmd.getType().isVerification() )
                cmd.updateResult( buffer );
        }


        public void executeBatch(TrinxCommandBatch batch)
        {
            ByteBuffer buffer = batch.getBuffer();
            int        ndata  = batch.getNumberOfDataFields();
            byte[][]   data   = batch.getDataFields();

            nativeExecuteBatch( m_eid, buffer.array(), buffer.arrayOffset()+buffer.position(), ndata, data );
        }


        @Override
        public void touch()
        {
            enclaveTouch( m_eid );
        }

    }

}

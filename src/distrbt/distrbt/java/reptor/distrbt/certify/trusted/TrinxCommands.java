package reptor.distrbt.certify.trusted;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import reptor.distrbt.com.Message;
import reptor.distrbt.common.data.Data;
import reptor.distrbt.common.data.MutableData;


public class TrinxCommands
{

    public static final byte
        EMPTY_ID                            = (byte) 0,
        CREATE_TGROUP_CERTIFICATE_ID        = (byte) 1,
        VERIFY_TGROUP_CERTIFICATE_ID        = (byte) 2,
        CREATE_TMAC_CERTIFICATE_ID          = (byte) 3,
        VERIFY_TMAC_CERTIFICATE_ID          = (byte) 4,
        CREATE_INDEPENDENT_CERTIFICATE_ID   = (byte) 5,
        VERIFY_INDEPENDENT_CERTIFICATE_ID   = (byte) 6,
        CREATE_CONTINUING_CERTIFICATE_ID    = (byte) 7,
        VERIFY_CONTINUING_CERTIFICATE_ID    = (byte) 8,
        BATCH_ID                            = (byte) 100;


    public static final boolean isVerification(int cmdid)
    {
        return ( cmdid & 0x01 )==0;
    }


    public static final byte
        NO_RESULT           = (byte) 0,
        CERTIFICATE_INVALID = (byte) 1,
        CERTIFICATE_VALID   = (byte) 2;


    public static enum TrinxCommandType
    {
        CREATE_TRUSTED_GROUP       ( CREATE_TGROUP_CERTIFICATE_ID, 0 ),
        VERIFY_TRUSTED_GROUP       ( VERIFY_TGROUP_CERTIFICATE_ID, 1 ),
        CREATE_TRUSTED_MAC         ( CREATE_TMAC_CERTIFICATE_ID, END_TSS-END_HEADER ),
        VERIFY_TRUSTED_MAC         ( VERIFY_TMAC_CERTIFICATE_ID, END_TSS-END_HEADER+1 ),
        CREATE_INDEPENDENT_COUNTER ( CREATE_INDEPENDENT_CERTIFICATE_ID, END_LOW-END_HEADER ),
        VERIFY_INDEPENDENT_COUNTER ( VERIFY_INDEPENDENT_CERTIFICATE_ID, END_LOW-END_HEADER+1 ),
        CREATE_CONTINUING_COUNTER  ( CREATE_CONTINUING_CERTIFICATE_ID, END_PREV_LOW-END_HEADER ),
        VERIFY_CONTINUING_COUNTER  ( VERIFY_CONTINUING_CERTIFICATE_ID, END_PREV_LOW-END_HEADER+1 );

        private final byte      m_id;
        private final int       m_bodysize;
        private final boolean   m_isverif;

        private TrinxCommandType(byte id, int bodysize)
        {
            m_id = id;
            m_bodysize = bodysize;
            m_isverif  = TrinxCommands.isVerification( id );
        }

        public byte getID()
        {
            return m_id;
        }

        public int getBodySize()
        {
            return m_bodysize;
        }

        public boolean isVerification()
        {
            return m_isverif;
        }
    }


    public static final int POINTER_BYTES = 8;


    public static final int
        POS_CMDID       = 0,
        END_CMDID       = POS_CMDID + Byte.BYTES,
        POS_MSGSIZE     = END_CMDID,
        END_MSGSIZE     = POS_MSGSIZE + Integer.BYTES,
        POS_MSGOFF      = END_MSGSIZE,
        END_MSGOFF      = POS_MSGOFF + Integer.BYTES,
        POS_CERTOFF     = END_MSGOFF,
        END_CERTOFF     = POS_CERTOFF + Integer.BYTES,
        POS_BODYSIZE    = END_CERTOFF,
        END_BODYSIZE    = POS_BODYSIZE + Integer.BYTES,
        POS_BODYOFF     = END_BODYSIZE,
        END_BODYOFF     = POS_BODYOFF + Integer.BYTES,
        POS_MSGPTR      = END_BODYOFF,
        END_MSGPTR      = POS_MSGPTR + POINTER_BYTES,
        POS_CERTPTR     = END_MSGPTR,
        END_CERTPTR     = POS_CERTPTR + POINTER_BYTES,
        POS_BODYPTR     = END_CERTPTR,
        END_BODYPTR     = POS_BODYPTR + POINTER_BYTES,
        END_HEADER      = END_BODYPTR,
        POS_TSS         = END_BODYPTR,
        END_TSS         = POS_TSS + Short.BYTES,
        POS_COUNTER     = END_TSS,
        END_COUNTER     = POS_COUNTER + Integer.BYTES,
        POS_HIGH        = END_COUNTER,
        END_HIGH        = POS_HIGH + Long.BYTES,
        POS_LOW         = END_HIGH,
        END_LOW         = POS_LOW + Long.BYTES,
        POS_PREV_HIGH   = END_LOW,
        END_PREV_HIGH   = POS_PREV_HIGH + Long.BYTES,
        POS_PREV_LOW    = END_PREV_HIGH,
        END_PREV_LOW    = POS_PREV_LOW + Long.BYTES;


    public interface TrinxCertificationCommand extends Message
    {
        TrinxCommandType            getType();
        Data                        getMessageData();
        MutableData                 getCertificateBuffer();

        ByteBuffer                  getBuffer();
        TrinxCertificationCommand   updateResult(ByteBuffer buffer);

        short                       getTssID();
        int                         getCounterNumber();
        long                        getHighValue();
        long                        getLowValue();
        long                        getPreviousHighValue();
        long                        getPreviousLowValue();
        byte                        getResult();

        TrinxCertificationCommand   result(byte result);
    }


    public interface TrinxCommandBuffer extends TrinxCertificationCommand
    {
        boolean isCertificateValid();

        TrinxCommandBuffer createTrustedGroup();
        TrinxCommandBuffer verifyTrustedGroup();
        TrinxCommandBuffer createTrustedMac();
        TrinxCommandBuffer verifyTrustedMac();
        TrinxCommandBuffer createIndependent();
        TrinxCommandBuffer verifyIndependent();
        TrinxCommandBuffer createContinuing();
        TrinxCommandBuffer verifyContinuing();
        TrinxCommandBuffer tss(short tssid);
        TrinxCommandBuffer counter(int ctrno);
        TrinxCommandBuffer counter(short tssid, int ctrno);
        TrinxCommandBuffer values(long highval, long lowval, long prevhigh, long prevlow);
        TrinxCommandBuffer value(long highval, long lowval);
        TrinxCommandBuffer value(long lowval);
        TrinxCommandBuffer message(Data msgdata, Data certdata);
        TrinxCommandBuffer message(Data msgdata, MutableData certbuf);
        TrinxCommandBuffer execute(Trinx trinx);
    }


    public static abstract class AbstractTrinxCommandBuffer implements TrinxCommandBuffer
    {
        protected TrinxCommandType  m_type;
        protected Data              m_msgdata;
        protected MutableData       m_certbuf;

        protected void writeHeaderTo(ByteBuffer out)
        {
            out.put( POS_CMDID, m_type.getID() );
            out.putInt( POS_MSGSIZE, m_msgdata.size() );
            out.putInt( POS_MSGOFF, m_msgdata.arrayOffset() );
            out.putInt( POS_CERTOFF, m_certbuf.arrayOffset() );
            out.putInt( POS_BODYSIZE, m_type.getBodySize() );
            out.putInt( POS_BODYOFF, 0 );
        }

        protected int resultPos()
        {
            return END_HEADER + m_type.getBodySize()-1;
        }

        @Override
        public int getTypeID()
        {
            return m_type.getID();
        }

        @Override
        public TrinxCommandType getType()
        {
            return m_type;
        }

        @Override
        public Data getMessageData()
        {
            return m_msgdata;
        }

        @Override
        public MutableData getCertificateBuffer()
        {
            return m_certbuf;
        }

        @Override
        public boolean isCertificateValid()
        {
            return getResult()==CERTIFICATE_VALID;
        }

        public TrinxCommandBuffer type(TrinxCommandType type)
        {
            m_type = type;
            return this;
        }

        @Override
        public TrinxCommandBuffer createTrustedGroup()
        {
            return type( TrinxCommandType.CREATE_TRUSTED_GROUP );
        }

        @Override
        public TrinxCommandBuffer verifyTrustedGroup()
        {
            return type( TrinxCommandType.VERIFY_TRUSTED_GROUP );
        }

        @Override
        public TrinxCommandBuffer createTrustedMac()
        {
            return type( TrinxCommandType.CREATE_TRUSTED_MAC );
        }

        @Override
        public TrinxCommandBuffer verifyTrustedMac()
        {
            return type( TrinxCommandType.VERIFY_TRUSTED_MAC );
        }

        @Override
        public TrinxCommandBuffer createIndependent()
        {
            return type( TrinxCommandType.CREATE_INDEPENDENT_COUNTER );
        }

        @Override
        public TrinxCommandBuffer verifyIndependent()
        {
            return type( TrinxCommandType.VERIFY_INDEPENDENT_COUNTER );
        }

        @Override
        public TrinxCommandBuffer createContinuing()
        {
            return type( TrinxCommandType.CREATE_CONTINUING_COUNTER );
        }

        @Override
        public TrinxCommandBuffer verifyContinuing()
        {
            return type( TrinxCommandType.VERIFY_CONTINUING_COUNTER );
        }

        @Override
        public TrinxCommandBuffer message(Data msgdata, Data certdata)
        {
            return message( msgdata, certdata.mutable() );
        }

        @Override
        public TrinxCommandBuffer message(Data msgdata, MutableData certbuf)
        {
            m_msgdata = msgdata;
            m_certbuf = certbuf;
            return this;
        }

        @Override
        public TrinxCommandBuffer execute(Trinx trinx)
        {
            trinx.executeCommand( this );
            return this;
        }
    }


    public static class UnserializedTrinxCommand extends AbstractTrinxCommandBuffer
    {
        protected short m_tssid;
        protected int   m_ctrno;
        protected long  m_highval;
        protected long  m_lowval;
        protected long  m_prevhigh;
        protected long  m_prevlow;
        protected byte  m_result;

        @Override
        public ByteBuffer getBuffer()
        {
            ByteBuffer buffer = ByteBuffer.allocate( END_HEADER + m_type.getBodySize() ).order( ByteOrder.nativeOrder() );

            writeHeaderTo( buffer );

            switch( getTypeID() )
            {
            case TrinxCommands.VERIFY_CONTINUING_CERTIFICATE_ID:
                buffer.putLong( POS_PREV_HIGH, m_prevhigh );
                buffer.putLong( POS_PREV_LOW, m_prevlow );
            case TrinxCommands.CREATE_CONTINUING_CERTIFICATE_ID:
            case TrinxCommands.VERIFY_INDEPENDENT_CERTIFICATE_ID:
            case TrinxCommands.CREATE_INDEPENDENT_CERTIFICATE_ID:
                buffer.putLong( POS_HIGH, m_highval );
                buffer.putLong( POS_LOW, m_lowval );
                buffer.putInt( POS_COUNTER, m_ctrno );
            case TrinxCommands.VERIFY_TMAC_CERTIFICATE_ID:
            case TrinxCommands.CREATE_TMAC_CERTIFICATE_ID:
                buffer.putShort( POS_TSS, m_tssid );
            }

            return buffer;
        }

        @Override
        public TrinxCommandBuffer updateResult(ByteBuffer buffer)
        {
            result( buffer.get( resultPos() ) );
            return this;
        }

        @Override
        public short getTssID()
        {
            return m_tssid;
        }

        @Override
        public int getCounterNumber()
        {
            return m_ctrno;
        }

        @Override
        public long getHighValue()
        {
            return m_highval;
        }

        @Override
        public long getLowValue()
        {
            return m_lowval;
        }

        @Override
        public long getPreviousHighValue()
        {
            return m_prevhigh;
        }

        @Override
        public long getPreviousLowValue()
        {
            return m_prevlow;
        }

        @Override
        public byte getResult()
        {
            return m_result;
        }

        @Override
        public TrinxCommandBuffer tss(short tssid)
        {
            m_tssid = tssid;
            return this;
        }

        @Override
        public TrinxCommandBuffer counter(int ctrno)
        {
            m_ctrno = ctrno;
            return this;
        }

        @Override
        public TrinxCommandBuffer counter(short tssid, int ctrno)
        {
            return tss( tssid ).counter( ctrno );
        }

        @Override
        public TrinxCommandBuffer values(long highval, long lowval, long prevhigh, long prevlow)
        {
            m_prevhigh = prevhigh;
            m_prevlow  = prevlow;
            return value( highval, lowval );
        }

        @Override
        public TrinxCommandBuffer value(long highval, long lowval)
        {
            m_highval = highval;
            return value( lowval );
        }

        @Override
        public TrinxCommandBuffer value(long lowval)
        {
            m_lowval = lowval;
            return this;
        }

        @Override
        public TrinxCertificationCommand result(byte result)
        {
            m_result = result;
            return this;
        }

    }


    public static class SerializedTrinxCommand extends AbstractTrinxCommandBuffer
    {
        protected ByteBuffer    m_buffer;
        protected int           m_offset;


        public SerializedTrinxCommand(ByteBuffer buffer)
        {
            m_buffer = buffer;
            offset( 0 );
        }

        public SerializedTrinxCommand(TrinxCommandType type)
        {
            m_buffer = ByteBuffer.allocate( END_HEADER + type.getBodySize() ).order( ByteOrder.nativeOrder() );
            offset( 0 );
            type( type );
        }

        public SerializedTrinxCommand offset(int offset)
        {
            m_buffer.clear();
            m_buffer.position( offset );
            m_buffer.putInt( m_offset+POS_BODYOFF, 0 );
            m_offset = offset;
            return this;
        }

        @Override
        public ByteBuffer getBuffer()
        {
            return m_buffer;
        }

        @Override
        public SerializedTrinxCommand updateResult(ByteBuffer buffer)
        {
            return this;
        }

        @Override
        public short getTssID()
        {
            return m_buffer.getShort( m_offset+POS_TSS );
        }

        @Override
        public int getCounterNumber()
        {
            return m_buffer.getInt( m_offset+POS_COUNTER );
        }

        @Override
        public long getHighValue()
        {
            return m_buffer.getLong( m_offset+POS_HIGH );
        }

        @Override
        public long getLowValue()
        {
            return m_buffer.getLong( m_offset+POS_LOW );
        }

        @Override
        public long getPreviousHighValue()
        {
            return m_buffer.getLong( m_offset+POS_PREV_HIGH );
        }

        @Override
        public long getPreviousLowValue()
        {
            return m_buffer.getLong( m_offset+POS_PREV_LOW );
        }

        @Override
        public byte getResult()
        {
            return m_buffer.get( m_offset+resultPos() );
        }

        @Override
        public SerializedTrinxCommand type(TrinxCommandType type)
        {
            super.type( type );
            m_buffer.limit( m_offset + END_HEADER + type.getBodySize() );
            m_buffer.put( m_offset+POS_CMDID, m_type.getID() );
            m_buffer.putInt( m_offset+POS_BODYSIZE, m_type.getBodySize() );
            return this;
        }

        @Override
        public SerializedTrinxCommand tss(short tssid)
        {
            m_buffer.putShort( m_offset+POS_TSS, tssid );
            return this;
        }

        @Override
        public SerializedTrinxCommand counter(int ctrno)
        {
            m_buffer.putInt( m_offset+POS_COUNTER, ctrno );
            return this;
        }

        @Override
        public SerializedTrinxCommand counter(short tssid, int ctrno)
        {
            return tss( tssid ).counter( ctrno );
        }

        @Override
        public SerializedTrinxCommand values(long highval, long lowval, long prevhigh, long prevlow)
        {
            value( highval, lowval );
            m_buffer.putLong( m_offset+POS_PREV_HIGH, prevhigh );
            m_buffer.putLong( m_offset+POS_PREV_LOW, prevlow );
            return this;
        }

        @Override
        public SerializedTrinxCommand value(long highval, long lowval)
        {
            value( lowval );
            m_buffer.putLong( m_offset+POS_HIGH, highval );
            return this;
        }

        @Override
        public SerializedTrinxCommand value(long lowval)
        {
            m_buffer.putLong( m_offset+POS_LOW, lowval );
            return this;
        }

        @Override
        public SerializedTrinxCommand message(Data msgdata, MutableData certbuf)
        {
            m_buffer.putInt( m_offset+POS_MSGSIZE, msgdata.size() );
            m_buffer.putInt( m_offset+POS_MSGOFF, msgdata.arrayOffset() );
            m_buffer.putInt( m_offset+POS_CERTOFF, certbuf.arrayOffset() );
            super.message( msgdata, certbuf );
            return this;
        }

        public SerializedTrinxCommand pointer(long msgptr, long certptr)
        {
            m_buffer.putLong( m_offset+POS_MSGPTR, msgptr );
            m_buffer.putLong( m_offset+POS_CERTPTR, certptr );
            return this;
        }


        @Override
        public SerializedTrinxCommand result(byte result)
        {
            m_buffer.put( m_offset+resultPos(), result );
            return this;
        }
    }


    public static class TrinxCommandBatch implements Message
    {
        private final ByteBuffer                m_buffer;
        private final ByteBuffer[]              m_cmdbufs;
        private final SerializedTrinxCommand[]  m_cmds;
        private final int                       m_bufpercmd;
        private final byte[][]                  m_data;

        private int m_used;
        private int m_pos;
        private int m_ndata;

        public TrinxCommandBatch(int nmaxcmds)
        {
            m_bufpercmd = END_HEADER+TrinxCommandType.VERIFY_CONTINUING_COUNTER.getBodySize();
            m_buffer    = ByteBuffer.allocate( Byte.BYTES*2 + m_bufpercmd*nmaxcmds ).order( ByteOrder.nativeOrder() );

            m_data = new byte[ nmaxcmds*2 ][];

            m_cmdbufs = new ByteBuffer[ nmaxcmds ];
            m_cmds = new SerializedTrinxCommand[ nmaxcmds ];

            for( int cmdno=0; cmdno<nmaxcmds; cmdno++ )
            {
                m_cmdbufs[ cmdno ] = m_buffer.duplicate().order( m_buffer.order() );
                m_cmds[ cmdno ]    = new SerializedTrinxCommand( m_cmdbufs[ cmdno ] );
            }

            m_buffer.put( 0, BATCH_ID );

            clear();
        }

        public ByteBuffer getBuffer()
        {
            return m_buffer;
        }

        public int getNumberOfDataFields()
        {
            return m_ndata;
        }

        public byte[][] getDataFields()
        {
            return m_data;
        }

        @Override
        public int getTypeID()
        {
            return BATCH_ID;
        }

        public TrinxCommandBuffer initNext()
        {
            if( m_used>0 )
                fixCommand( m_used-1 );

            m_cmds[ m_used ].offset( m_pos );

            return m_cmds[ m_used++ ];
        }

        private void fixCommand(int cmdno)
        {
            m_pos += m_cmdbufs[ cmdno ].remaining();
        }

        public TrinxCommandBatch fix()
        {
            fixCommand( m_used-1 );
            m_buffer.put( m_pos, TrinxCommands.EMPTY_ID );
            m_buffer.limit( m_pos+Byte.BYTES );
            return this;
        }

        public TrinxCommandBatch finish()
        {
            m_ndata = 0;
            for( int cmdno=0; cmdno<m_used; cmdno++ )
            {
                SerializedTrinxCommand cmd = m_cmds[ cmdno ];
                m_data[ m_ndata ] = cmd.getMessageData().array();

                if( cmd.getCertificateBuffer().array()==cmd.getMessageData().array() )
                {
                    cmd.pointer( m_ndata, m_ndata );
                    m_ndata++;
                }
                else
                {
                    m_data[ m_ndata+1 ] = cmd.getCertificateBuffer().array();
                    cmd.pointer( m_ndata, m_ndata+1 );
                    m_ndata += 2;
                }
            }

            return this;
        }

        public int size()
        {
            return m_used;
        }

        public TrinxCommandBuffer get(int cmdno)
        {
            assert cmdno<m_used;

            return m_cmds[ cmdno ];
        }

        public void clear()
        {
            m_used    = 0;
            m_pos     = Byte.BYTES;
            m_ndata = 0;
            m_buffer.clear();
        }
    }

}

package reptor.distrbt.certify.trusted;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;

import javax.crypto.Mac;

import com.google.common.base.Preconditions;

import reptor.distrbt.certify.mac.Authenticating;
import reptor.distrbt.certify.trusted.TrinxCommands.TrinxCertificationCommand;
import reptor.distrbt.certify.trusted.TrinxCommands.TrinxCommandBatch;
import reptor.distrbt.com.Message;
import reptor.distrbt.common.data.Data;
import reptor.distrbt.common.data.MutableData;


public class JavaTrinxImplementation implements TrinxImplementation
{

    private final Mac m_macproto;
    private final int m_certsize;


    public JavaTrinxImplementation(Mac macproto)
    {
        m_macproto = macproto;
        m_certsize = m_macproto.getMacLength();
    }


    public JavaTrinxImplementation(String hmacalgo, String secret)
    {
        try
        {
            m_macproto = Mac.getInstance( hmacalgo );
            m_macproto.init( Authenticating.createKey( secret ) );
        }
        catch( NoSuchAlgorithmException | InvalidKeyException e )
        {
            throw new IllegalArgumentException( e );
        }

        m_certsize = m_macproto.getMacLength();
    }


    @Override
    public JavaTrinx createTrinx(short tssid, int ncounters)
    {
        try
        {
            return new JavaTrinx( tssid, ncounters, (Mac) m_macproto.clone() );
        }
        catch( CloneNotSupportedException e )
        {
            throw new IllegalStateException( e );
        }
    }


    @Override
    public String toString()
    {
        return String.format( "JavaTrinx(%s)", m_macproto.getAlgorithm() );
    }


    @Override
    public int getMacCertificateSize()
    {
        return m_certsize;
    }


    @Override
    public int getCounterCertificateSize()
    {
        return m_certsize;
    }


    public class JavaTrinx implements Trinx
    {

        private final ByteBuffer m_createbuf;

        private final short      m_tssid;
        private final Mac        m_mac;
        private final long[]     m_highvals;
        private final long[]     m_lowvals;


        public JavaTrinx(short tssid, int ncounters, Mac mac)
        {
            m_tssid     = tssid;
            m_highvals  = new long[ ncounters ];
            m_lowvals   = new long[ ncounters ];
            m_mac       = mac;

            m_createbuf = ByteBuffer.allocate( Short.BYTES + ( Integer.BYTES + Long.BYTES*4 )*ncounters );
            m_createbuf.order( ByteOrder.nativeOrder() );
        }


        @Override
        public void close()
        {
        }


        @Override
        public String getImplementationName()
        {
            return JavaTrinxImplementation.this.toString();
        }


        @Override
        public short getID()
        {
            return m_tssid;
        }


        @Override
        public int getCounterCertificateSize()
        {
            return m_certsize;
        }


        @Override
        public int getNumberOfCounters()
        {
            return m_highvals.length;
        }


        @Override
        public TrustedCounterValue counterValue(int ctrno)
        {
            long highval = m_highvals[ ctrno ];
            long lowval  = m_lowvals[ ctrno ];

            return new TrustedCounterValue()
            {
                @Override
                public long getHighValue()
                {
                    return highval;
                }

                @Override
                public long getLowValue()
                {
                    return lowval;
                }

                @Override
                public String toString()
                {
                    return String.format( "%d-%d", highval, lowval );
                }
            };
        }


        @Override
        public int getMacCertificateSize()
        {
            return m_certsize;
        }


        @Override
        public void touch()
        {
        }


        @Override
        public void executeCommand(Message msg)
        {
            switch( msg.getTypeID() )
            {
            case TrinxCommands.CREATE_TGROUP_CERTIFICATE_ID:
                createTrustedGroupCertificate( (TrinxCertificationCommand) msg );
                return;
            case TrinxCommands.CREATE_TMAC_CERTIFICATE_ID:
                createTrustedMacCertificate( (TrinxCertificationCommand) msg );
                return;
            case TrinxCommands.CREATE_INDEPENDENT_CERTIFICATE_ID:
                createIndependentCounterCertificate( (TrinxCertificationCommand) msg );
                return;
            case TrinxCommands.CREATE_CONTINUING_CERTIFICATE_ID:
                createContinuingCounterCertificate( (TrinxCertificationCommand) msg );
                return;
            case TrinxCommands.VERIFY_TGROUP_CERTIFICATE_ID:
            case TrinxCommands.VERIFY_TMAC_CERTIFICATE_ID:
            case TrinxCommands.VERIFY_INDEPENDENT_CERTIFICATE_ID:
            case TrinxCommands.VERIFY_CONTINUING_CERTIFICATE_ID:
                verifyCertificate( (TrinxCertificationCommand) msg );
                return;
            case TrinxCommands.BATCH_ID:
                processBatch( (TrinxCommandBatch) msg );
                return;
            default:
                throw new UnsupportedOperationException( msg.toString() );
            }
        }


        private void processBatch(TrinxCommandBatch batch)
        {
            for( int cmdno=0; cmdno<batch.size(); cmdno++ )
                executeCommand( batch.get( cmdno ) );
        }


        private void verifyCertificate(TrinxCertificationCommand cmd)
        {
            startMac( cmd.getMessageData() );
            boolean res = verifyMac( bodyBuffer( cmd ), cmd.getCertificateBuffer() );

            cmd.result( res ? TrinxCommands.CERTIFICATE_VALID : TrinxCommands.CERTIFICATE_INVALID );
        }


        private void createTrustedGroupCertificate(TrinxCertificationCommand cmd)
        {
            startMac( cmd.getMessageData() );

            createMac( cmd.getCertificateBuffer() );
        }


        private void createTrustedMacCertificate(TrinxCertificationCommand cmd)
        {
            ByteBuffer certin = bodyBuffer( cmd );

            startMac( cmd.getMessageData(), certin );

            checkTssID( certin.getShort() );

            createMac( certin, cmd.getCertificateBuffer() );
        }


        private void createIndependentCounterCertificate(TrinxCertificationCommand cmd)
        {
            ByteBuffer certin = bodyBuffer( cmd );

            startMac( cmd.getMessageData(), certin );

            checkTssID( certin.getShort() );
            updateIndependentCounter( certin );

            createMac( certin, cmd.getCertificateBuffer() );
        }


        private void createContinuingCounterCertificate(TrinxCertificationCommand cmd)
        {
            ByteBuffer certin = bodyBuffer( cmd );

            startMac( cmd.getMessageData(), certin );

            checkTssID( certin.getShort() );
            updateContinuingCounter( certin );

            createMac( certin, cmd.getCertificateBuffer() );
        }


        protected ByteBuffer bodyBuffer(TrinxCertificationCommand cmd)
        {
            ByteBuffer cmdbuf  = cmd.getBuffer();
            ByteBuffer bodybuf = cmdbuf.duplicate().order( cmdbuf.order() );

            bodybuf.position( bodybuf.position()+TrinxCommands.END_HEADER );

            return bodybuf;
        }


        private void startMac(Data msgdata, ByteBuffer buffer)
        {
            startMac( msgdata );
            buffer.mark();
        }


        private void startMac(Data msgdata)
        {
            msgdata.writeTo( m_mac );
        }


        private void updateIndependentCounter(ByteBuffer certin)
        {
            int ctrno = certin.getInt();
            checkCounter( ctrno );

            long highval  = certin.getLong();
            long lowval   = certin.getLong();

            long prevhigh = m_highvals[ ctrno ];
            long prevlow  = m_lowvals[ ctrno ];

            if( highval<prevhigh || highval==prevhigh && lowval<=prevlow )
                throw new IllegalArgumentException( String.format( "Counter %d: current %d-%d, requested %d-%d", ctrno, prevhigh, prevlow, highval, lowval ) );

            m_highvals[ ctrno ] = highval;
            m_lowvals[ ctrno ]  = lowval;
        }


        private void updateContinuingCounter(ByteBuffer certin)
        {
            int ctrno = certin.getInt();
            checkCounter( ctrno );

            long highval  = certin.getLong();
            long lowval   = certin.getLong();

            long prevhigh = m_highvals[ ctrno ];
            long prevlow  = m_lowvals[ ctrno ];

            if( highval<prevhigh || highval==prevhigh && lowval<prevlow )
                throw new IllegalArgumentException( String.format( "Counter %d: current %d-%d, requested %d-%d", ctrno, prevhigh, prevlow, highval, lowval ) );

            m_highvals[ ctrno ] = highval;
            m_lowvals[ ctrno ]  = lowval;

            certin.putLong( prevhigh );
            certin.putLong( prevlow );
        }


        private void createMac(ByteBuffer certin, MutableData certout)
        {
            certin.reset();
            m_mac.update( certin );
            certin.reset();

            createMac( certout );
        }


        private void createMac(MutableData certout)
        {
            certout.readFrom( finishMac() );
            certout.adaptSlice( m_certsize );
        }


        private boolean verifyMac(ByteBuffer buffer, Data certdata)
        {
            buffer.mark();
            buffer.limit( buffer.limit()-1 );
            m_mac.update( buffer );
            buffer.limit( buffer.limit()+1 );

            byte[] calcproof = finishMac();

            boolean res = certdata.matches( calcproof, 0, calcproof.length, 0 );

            buffer.reset();

            return res;
        }


        private byte[] finishMac()
        {
            return m_mac.doFinal();
        }


//      private void checkCounters(int[] ctrnos)
//      {
//          if( ctrnos.length==0 )
//              throw new IllegalArgumentException();
//
//          int last = ctrnos[ 0 ];
//          checkCounter( last );
//
//          for( int i=1; i<ctrnos.length; i++ )
//          {
//              int cur = ctrnos[ i ];
//
//              checkCounter( cur );
//
//              if( cur<=last )
//                  throw new IllegalArgumentException();
//
//              last = cur;
//          }
//      }


      private void checkTssID(short tssid)
      {
          Preconditions.checkArgument( tssid==m_tssid );
      }


      private void checkCounter(int ctrno)
      {
          Preconditions.checkElementIndex( ctrno, m_highvals.length );
      }

    }

}

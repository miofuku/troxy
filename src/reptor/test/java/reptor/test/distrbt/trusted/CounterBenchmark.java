package reptor.test.distrbt.trusted;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.util.function.Function;

import reptor.distrbt.certify.trusted.Trinx;
import reptor.distrbt.certify.trusted.TrinxCommands.SerializedTrinxCommand;
import reptor.distrbt.certify.trusted.TrinxCommands.TrinxCommandBatch;
import reptor.distrbt.certify.trusted.TrinxCommands.TrinxCommandBuffer;
import reptor.distrbt.certify.trusted.TrinxCommands.TrinxCommandType;
import reptor.distrbt.certify.trusted.TrinxCommands.UnserializedTrinxCommand;
import reptor.distrbt.certify.trusted.TrinxImplementation;
import reptor.distrbt.common.data.Data;
import reptor.distrbt.common.data.DataBuffer;
import reptor.distrbt.common.data.ImmutableData;
import reptor.distrbt.common.data.MutableData;
import reptor.jlib.hash.HashAlgorithm;
import reptor.test.bench.MultiCoreTestObject;


public class CounterBenchmark extends MessageBenchmark
{

    private final TrinxImplementation     m_tm;

    private boolean m_touchonly = false;
    private int     m_batchsize = 1;
    private Function<TrinxCommandType, TrinxCommandBuffer> m_cmdfac;


    public CounterBenchmark(TrinxImplementation tm)
    {
        m_tm = tm;
    }


    public void setTouchOnly(boolean to)
    {
        m_touchonly = to;
    }


    public void setCommandFactory(Function<TrinxCommandType, TrinxCommandBuffer> cmdfac)
    {
        m_cmdfac = cmdfac;
    }


    public void setBatchSize(int batchsize)
    {
        m_batchsize = batchsize;
    }


    @Override
    public MultiCoreTestObject apply(int value)
    {
        try
        {
            Trinx tm = m_tm.createTrinx( (short) value, 1 );

            if( m_touchonly )
                return new Touch( tm );
            else
            {
                MutableData msgdata = new DataBuffer( getMessageSize()+tm.getCounterCertificateSize() ).slice( 0, getMessageSize() );
                MutableData crtdata = msgdata.slice( msgdata.size(), tm.getCounterCertificateSize() );

                switch( getCertificationMode() )
                {
                case CERTIFY:
                    return new Certify( tm, msgdata, crtdata, getPreHashing(), m_cmdfac );
                case VERIFY:
                    {
                        new UnserializedTrinxCommand().type( TrinxCommandType.CREATE_INDEPENDENT_COUNTER )
                                .counter( (short) 0, 0 ).value( 0, 1 )
                                .message( msgdata, crtdata )
                                .execute( tm );
                        crtdata.resetSlice();

                        return new Verify( tm, msgdata, crtdata, getPreHashing(), m_cmdfac );
                    }
                case VERIFY_DUMMY:
                    return new Verify( tm, msgdata, new DataBuffer( tm.getCounterCertificateSize() ), getPreHashing(), m_cmdfac );
                case BATCH_CERTIFY:
                    return new BatchCertify( tm, getPreHashing(), getMessageSize(), m_batchsize );
                default:
                    throw new IllegalStateException();
                }
            }
        }
        catch( IOException e )
        {
            throw new IllegalStateException( e );
        }
    }


    private static class Touch implements MultiCoreTestObject
    {
        private final Trinx m_tm;


        public Touch(Trinx tm)
        {
            m_tm = tm;
        }

        @Override
        public void invoke()
        {
            m_tm.touch();
        }
    }


    private static class Certify extends AbstractCertification
    {
        private final Trinx              m_tm;
        private final MutableData        m_crtbuf;
        private final TrinxCommandBuffer m_cmd;

        private long m_ctrval = 1;

        public Certify(Trinx tm, Data msgdata, MutableData crtbuf, HashAlgorithm prehash, Function<TrinxCommandType, TrinxCommandBuffer> cmdfac)
        {
            super( msgdata, prehash );

            m_tm      = tm;
            m_crtbuf  = crtbuf;
            m_cmd     = cmdfac.apply( TrinxCommandType.CREATE_INDEPENDENT_COUNTER );

            m_cmd.counter( tm.getID(), 0 ).value( 0, 0 );
        }

        @Override
        public void invoke()
        {
            m_cmd.value( m_ctrval++ ).message( data(), m_crtbuf );
            m_tm.executeCommand( m_cmd );
            m_crtbuf.resetSlice();
        }
    }


    private static class Verify extends AbstractCertification
    {
        private final Trinx              m_tm;
        private final Data               m_crtdata;
        private final TrinxCommandBuffer m_cmd;


        public Verify(Trinx tm, Data msgdata, Data crtdata, HashAlgorithm prehash, Function<TrinxCommandType, TrinxCommandBuffer> cmdfac)
        {
            super( msgdata, prehash );

            m_tm      = tm;
            m_crtdata = crtdata;
            m_cmd     = cmdfac.apply( TrinxCommandType.VERIFY_INDEPENDENT_COUNTER );
            m_cmd.counter( m_tm.getID(), 0 );
        }

        @Override
        public void invoke()
        {
            m_cmd.value( 0, 1 ).message( data(), m_crtdata );
            m_tm.executeCommand( m_cmd );
        }
    }


    private static class BatchCertify implements MultiCoreTestObject
    {
        private final Trinx             m_tm;
        private final MessageDigest     m_digester;

        private final MutableData[]     m_msgs;
        private final MutableData[]     m_certs;

        private final TrinxCommandBatch m_batch;

        private long m_ctrval = 1;

        public BatchCertify(Trinx tm, HashAlgorithm prehash, int msgsize, int batchsize)
        {
            m_tm       = tm;
            m_digester = prehash==null ? null : prehash.digester();

            m_msgs  = new MutableData[ batchsize ];
            m_certs = new MutableData[ batchsize ];
            m_batch = new TrinxCommandBatch( batchsize );

            for( int msgno=0; msgno<batchsize; msgno++ )
            {
                MutableData msgbuf = new DataBuffer( msgsize+tm.getCounterCertificateSize() );
                m_msgs[ msgno ]  = msgbuf.slice( 0, msgsize );
                m_certs[ msgno ] = msgbuf.slice( msgsize, tm.getCounterCertificateSize() );

                TrinxCommandBuffer cmd = m_batch.initNext();
                cmd.createIndependent().counter( tm.getID(), 0 ).value( 0, 0 );
            }

            m_batch.fix();
        }

        protected Data data(int msgno)
        {
            if( m_digester==null )
                return m_msgs[ msgno ];
            else
            {
                m_msgs[ msgno ].writeTo( m_digester );
                return ImmutableData.wrap( m_digester.digest() );
            }
        }

        @Override
        public void invoke()
        {
            for( int msgno=0; msgno<m_msgs.length; msgno++ )
                m_batch.get( msgno ).value( m_ctrval++ ).message( data( msgno ), m_certs[ msgno ] );
            m_batch.finish();

            m_tm.executeCommand( m_batch );

            for( int msgno=0; msgno<m_msgs.length; msgno++ )
                m_certs[ msgno ].resetSlice();
        }
    }
}

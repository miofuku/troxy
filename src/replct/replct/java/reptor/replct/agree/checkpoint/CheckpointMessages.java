package reptor.replct.agree.checkpoint;

import java.io.IOException;
import java.nio.ByteBuffer;

import reptor.distrbt.com.Message;
import reptor.distrbt.com.MessageDigestSink;
import reptor.distrbt.com.MessageDigester;
import reptor.distrbt.com.MessageMapper;
import reptor.distrbt.common.data.ImmutableData;
import reptor.distrbt.common.data.ImmutableDataBuffer;
import reptor.replct.MessageCategoryID;
import reptor.replct.ProtocolID;


public class CheckpointMessages
{

    private static final int CHECKPOINT_BASE       = ProtocolID.COMMON | MessageCategoryID.CHECKPOINT;
    public  static final int CHECKPOINT_CREATED_ID = CHECKPOINT_BASE + 1;
    public  static final int CHECKPOINT_ID         = CHECKPOINT_BASE + 2;
    public  static final int CHECKPOINT_STABLE_ID  = CHECKPOINT_BASE + 3;
    public  static final int SNAPSHOT_ID           = CHECKPOINT_BASE + 4;


    public static class CheckpointCreated implements CheckpointMessage
    {
        private final int           m_partno;
        private final long          m_orderno;
        private final ImmutableData m_svcstate;
        private final long[]        m_resmap;
        private final boolean       m_isfull;

        public CheckpointCreated(int partno, long orderno, ImmutableData svcstate, long[] resmap, boolean isfull)
        {
            m_orderno  = orderno;
            m_partno   = partno;
            m_svcstate = svcstate;
            m_resmap   = resmap;
            m_isfull   = isfull;
        }

        @Override
        public int getTypeID()
        {
            return CHECKPOINT_CREATED_ID;
        }

        @Override
        public String toString()
        {
            return "{CHECKPOINT_CREATED}";
        }

        @Override
        public long getOrderNumber()
        {
            return m_orderno;
        }

        public int getPartitionNumber()
        {
            return m_partno;
        }

        public ImmutableData getServiceState()
        {
            return m_svcstate;
        }

        // FIXME: (Hashes of) return values are required as well.
        public long[] getResultMap()
        {
            return m_resmap;
        }

        public boolean isFullServiceState()
        {
            return m_isfull;
        }
    }


    // TODO: Full checkpoints should be a separate message since they are not very usefull outside the world
    //  of microbenchmarks. Further, PBFT actually does not need the service state hash within this message.
    //  Or maybe it does with authenticators? Or with authenticators and a weak quorum?
    public static class Checkpoint extends AbstractCheckpointMessage
    {

        private final int           m_viewno; // Include view number, even if it differes among replicas, to allow view-relative sender numbers.
        private final ImmutableData m_svcstate;
        private final long[]        m_resmap;
        private final boolean       m_isfull;

        // TODO: Must not be part of the message itself -> Digestable message element.
        private transient ImmutableData m_statehash;


        public Checkpoint(short sender, short shardno, int viewno, long orderno, ImmutableData svcstate, long[] resmap, boolean isfull)
        {
            super( sender, shardno, orderno );

            m_viewno   = viewno;
            m_svcstate = svcstate;
            m_resmap   = resmap;
            m_isfull   = isfull;

            initStateHash();
        }


        private void initStateHash()
        {
            if( m_resmap==null )
            {
                if( m_svcstate==null )
                    m_statehash = ImmutableData.EMPTY;
                else if( !m_isfull )
                    m_statehash = m_svcstate;
            }
        }


        @Override
        public void writeTypeContentTo(ByteBuffer out, MessageMapper mapper)
        {
            super.writeTypeContentTo( out, mapper );

            byte flags = 0;
            if( m_isfull )
                flags |= 1;
            if( m_svcstate!=null )
                flags |= 2;
            if( m_resmap!=null )
                flags |= 4;
            out.put( flags );

            out.putInt( m_viewno );

            if( m_svcstate!=null )
            {
                out.putInt( m_svcstate.size() );
                m_svcstate.writeTo( out );
            }

            if( m_resmap!=null )
            {
                out.putInt( m_resmap.length );

                for( long cliprog : m_resmap )
                    out.putLong( cliprog );
            }
        }


        @Override
        public int calculateTypePlainPrefixSize(MessageMapper mapper)
        {
            return super.calculateTypePlainPrefixSize( mapper ) + Byte.BYTES + Integer.BYTES;
        }


        @Override
        public int calculateTypeContentSize(MessageMapper mapper)
        {
            return ( m_svcstate!=null ? Integer.BYTES+m_svcstate.size() : 0 ) +
                   ( m_resmap!=null ? Integer.BYTES+Long.BYTES*m_resmap.length : 0 );
        }


        public Checkpoint(ByteBuffer in, MessageMapper mapper, Object extcntxt) throws IOException
        {
            super( in );

            byte flags = in.get();

            m_isfull  = ( flags & 1 ) != 0;
            m_viewno  = in.getInt();

            if( ( flags & 2 ) == 0 )
                m_svcstate = null;
            else
                m_svcstate = ImmutableData.wrapFrom( in, in.getInt() );

            if( ( flags & 4 ) == 0 )
                m_resmap = null;
            else
            {
                m_resmap = new long[ in.getInt() ];
                for( int i = 0; i < m_resmap.length; i++ )
                    m_resmap[i] = in.getLong();
            }

            initStateHash();
        }


        @Override
        public void prepareDigestion(MessageDigester digester)
        {
            super.prepareDigestion( digester );

            if( m_statehash==null )
            {
                int statesize = getContentSize()-getPlainPrefixSize();

                if( statesize==0 )
                    m_statehash = ImmutableData.EMPTY;
                else
                    m_statehash = digester.digestTypeContent( this, 0, statesize );
            }
        }


        @Override
        public void digestTypeContentTo(MessageDigestSink sink)
        {
            // The plain prefix has been already digested by the mapper!
            sink.putData( m_statehash );
        }


        @Override
        public int getTypeID()
        {
            return CHECKPOINT_ID;
        }


        @Override
        public String toString()
        {
            return String.format( "{%s|%d}", idString(), m_svcstate==null ? -1 : m_svcstate.size() );
        }


        @Override
        protected String getTypeName()
        {
            return "CHECKPOINT";
        }


        public int getViewNumber()
        {
            return m_viewno;
        }


        public ImmutableData getServiceState()
        {
            return m_svcstate;
        }


        public long[] getResultMap()
        {
            return m_resmap;
        }


        public boolean isFullCheckpoint()
        {
            return m_isfull;
        }


        public ImmutableData getStateHash()
        {
            return m_statehash;
        }


        public ImmutableData getStateForExecutor(int exectid)
        {
            ByteBuffer buf = m_svcstate.byteBuffer();

            while( exectid-- > 0 )
                buf.position( buf.getInt() + buf.position() );

            int statesize = buf.getInt();

            return new ImmutableDataBuffer( buf, 0, statesize );
        }

    }


    public static class CheckpointStable implements CheckpointMessage
    {
        private final long m_orderno;

        public CheckpointStable(long orderno)
        {
            m_orderno = orderno;
        }

        @Override
        public int getTypeID()
        {
            return CHECKPOINT_STABLE_ID;
        }

        @Override
        public String toString()
        {
            return "{CHECKPOINT_STABLE}";
        }

        @Override
        public long getOrderNumber()
        {
            return m_orderno;
        }
    }


    public static class Snapshot implements Message
    {
        private final long          m_orderno;
        private final ImmutableData m_svcstate;
        private final long[]        m_resmap;

        public Snapshot(long orderno, ImmutableData svcstate, long[] resmap)
        {
            assert svcstate!=null;

            m_orderno  = orderno;
            m_svcstate = svcstate;
            m_resmap   = resmap;
        }

        @Override
        public int getTypeID()
        {
            return SNAPSHOT_ID;
        }

        @Override
        public String toString()
        {
            return "{SNAPSHOT}";
        }

        public long getOrderNumber()
        {
            return m_orderno;
        }

        public ImmutableData getServiceState()
        {
            return m_svcstate;
        }

        public ImmutableData getServiceStatePartition(short partno)
        {
            ByteBuffer buf = m_svcstate.byteBuffer();

            while( partno-- > 0 )
                buf.position( buf.getInt() + buf.position() );

            int statesize = buf.getInt();

            return new ImmutableDataBuffer( buf, 0, statesize );
        }

        // FIXME: (Hashes of) return values are required as well.
        public long[] getResultMap()
        {
            return m_resmap;
        }
    }

}

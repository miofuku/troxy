package refit.hybstero.checkpoint;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Objects;

import reptor.distrbt.com.AbstractNetworkMessage;
import reptor.distrbt.com.MessageMapper;
import reptor.distrbt.common.data.ImmutableData;
import reptor.replct.MessageCategoryID;
import reptor.replct.ProtocolID;
import reptor.replct.ShardMessage;
import reptor.replct.agree.common.checkpoint.CheckpointMessage;
import reptor.replct.agree.common.checkpoint.CheckpointMessageID;
import reptor.replct.agree.common.checkpoint.CheckpointNetworkMessage;
import reptor.replct.agree.common.checkpoint.CheckpointMessages.Checkpoint;


public class HybsterCheckpointMessages
{

    private static final int HYBSTER_CHECKPOINT_BASE            = ProtocolID.HYBSTER | MessageCategoryID.CHECKPOINT;
    public  static final int HYBSTER_CHECKPOINT_ID              = HYBSTER_CHECKPOINT_BASE + 1;
    public  static final int HYBSTER_TC_VERIFICATION_ID         = HYBSTER_CHECKPOINT_BASE + 2;
    public  static final int HYBSTER_SHARD_CHECKPOINT_STABLE_ID = HYBSTER_CHECKPOINT_BASE + 3;


    public interface HybsterCheckpointNetworkMessage extends CheckpointNetworkMessage, ShardMessage
    {

    }


    public static class HybsterCheckpoint extends Checkpoint implements HybsterCheckpointNetworkMessage
    {

        public HybsterCheckpoint(short sender, int viewno, long orderno, ImmutableData payload, long[] nodeprogs,
                                 boolean isfull, short shardno)
        {
            super( sender, shardno, viewno, orderno, payload, nodeprogs, isfull );
        }


        public HybsterCheckpoint(ByteBuffer in, MessageMapper mapper, Object extcntxt) throws IOException
        {
            super( in, mapper, extcntxt );
        }


        @Override
        public int getTypeID()
        {
            return HYBSTER_CHECKPOINT_ID;
        }


        @Override
        protected String getTypeName()
        {
            return "HYBSTER_CHECKPOINT";
        }

    }


    public static class HybsterTCVerification extends AbstractNetworkMessage implements HybsterCheckpointNetworkMessage
    {

        private final short               m_sender;
        private final int                 m_viewno;
        private final CheckpointMessageID m_chkid;
        private final ImmutableData       m_certdata;


        public static HybsterTCVerification createFor(short sender, HybsterCheckpoint chkpt)
        {
            return new HybsterTCVerification( sender, chkpt );
        }


        protected HybsterTCVerification(short sender, HybsterCheckpoint chkpt)
        {
            m_sender   = sender;
            m_chkid    = CheckpointMessageID.createFor( chkpt );
            m_viewno   = chkpt.getViewNumber();
            m_certdata = chkpt.getCertificateData().immutable();
        }


        @Override
        public void writeTypeContentTo(ByteBuffer out, MessageMapper mapper)
        {
            super.writeTypeContentTo( out, mapper );

            out.putShort( m_sender );
            m_chkid.writeTo( out );
            out.putInt( m_viewno );
            out.putInt( m_certdata.size() );
            m_certdata.writeTo( out );
        }

        @Override
        public int calculateTypePlainPrefixSize(MessageMapper mapper)
        {
            return super.calculateTypePlainPrefixSize( mapper ) + Short.BYTES + m_chkid.getSize() + Integer.BYTES + Integer.BYTES + m_certdata.size();
        }


        public HybsterTCVerification(ByteBuffer in, MessageMapper mapper, Object extcntxt) throws IOException
        {
            super( in );

            m_sender  = in.getShort();
            m_chkid   = CheckpointMessageID.readFrom( HYBSTER_CHECKPOINT_ID, in );
            m_viewno  = in.getInt();

            int certsize =  in.getInt();
            m_certdata = ImmutableData.readFrom( in, certsize );
        }


        @Override
        public int getTypeID()
        {
            return HYBSTER_TC_VERIFICATION_ID;
        }



        @Override
        public String toString()
        {
            return String.format( "%s|%d:%d-%d:%d", getClass().getSimpleName(), m_sender,
                                  m_chkid.getSender(), m_chkid.getShardNumber(), m_chkid.getOrderNumber() );
        }


        @Override
        public short getSender()
        {
            return m_sender;
        }


        public int getViewNumber()
        {
            return m_viewno;
        }


        @Override
        public long getOrderNumber()
        {
            return m_chkid.getOrderNumber();
        }


        @Override
        public short getShardNumber()
        {
            return m_chkid.getShardNumber();
        }


        public short getCheckpointSender()
        {
            return m_chkid.getSender();
        }


        public CheckpointMessageID getCheckpointID()
        {
            return m_chkid;
        }


        public ImmutableData getCheckpointCertificateData()
        {
            return m_certdata;
        }



        @Override
        public boolean equals(Object obj)
        {
            if( obj==this )
                return true;

            if( obj==null || !( obj instanceof HybsterTCVerification ) )
                return false;

            // TODO: Include payload.
            HybsterTCVerification other = (HybsterTCVerification) obj;

            return other.m_sender==m_sender && other.m_chkid.equals( m_chkid ) && other.m_viewno==m_viewno;
        }


        @Override
        public int hashCode()
        {
            return Objects.hash( getTypeID(), m_sender, m_chkid );
        }

    }


    public static class HybsterShardCheckpointStable implements CheckpointMessage
    {
        private final HybsterCheckpointCertificate m_cert;

        public HybsterShardCheckpointStable(HybsterCheckpointCertificate cert)
        {
            m_cert = cert;
        }

        @Override
        public int getTypeID()
        {
            return HYBSTER_SHARD_CHECKPOINT_STABLE_ID;
        }

        @Override
        public String toString()
        {
            return "{HYBSTER_SHARD_CHECKPOINT_STABLE}";
        }

        @Override
        public long getOrderNumber()
        {
            return m_cert.getOrderNumber();
        }

        public short getShardNumber()
        {
            return m_cert.getShardNumber();
        }

        public HybsterCheckpointCertificate getShardCertificate()
        {
            return m_cert;
        }
    }

}

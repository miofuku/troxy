package refit.pbfto.view;

import java.io.IOException;
import java.nio.ByteBuffer;

import refit.pbfto.order.PBFTOrderMessages.PBFTPrePrepare;
import reptor.distrbt.com.MessageDigestSink;
import reptor.distrbt.com.MessageDigester;
import reptor.distrbt.com.MessageMapper;
import reptor.replct.MessageCategoryID;
import reptor.replct.ProtocolID;
import reptor.replct.agree.common.checkpoint.CheckpointMessages.Checkpoint;
import reptor.replct.agree.common.order.OrderNetworkMessage;
import reptor.replct.agree.common.view.AbstractViewChangeMessage;
import reptor.replct.agree.common.view.StableView;
import reptor.replct.agree.common.view.View;
import reptor.replct.agree.common.view.ViewChangeMessages;
import reptor.replct.agree.common.view.InternalViewChangeMessages.InternalViewChangeMessage;


public class PBFTViewChangeMessages
{

    private static final int PBFT_VIEW_CHANGE_BASE = ProtocolID.PBFT | MessageCategoryID.VIEW_CHANGE;
    public  static final int PBFT_VIEW_CHANGE_ID   = PBFT_VIEW_CHANGE_BASE + 1;
    public  static final int PBFT_NEW_VIEW_ID      = PBFT_VIEW_CHANGE_BASE + 2;


    public static class PBFTViewChange extends AbstractViewChangeMessage
    {

        private Checkpoint[]             m_chkptproof;
        private OrderNetworkMessage[][] m_prepproofs;


        public PBFTViewChange(short sender, int viewno,
                              short shardno, Checkpoint[] chkptproof, OrderNetworkMessage[][] prepproofs)
        {
            super( sender, shardno, viewno );

            assert chkptproof!=null && prepproofs!=null;

            m_chkptproof = chkptproof;
            m_prepproofs = prepproofs;
        }


        @Override
        public void writeTypeContentTo(ByteBuffer out, MessageMapper mapper)
        {
            super.writeTypeContentTo( out, mapper );

            out.put( (byte) m_chkptproof.length );
            out.putInt( m_prepproofs.length );
            out.put( (byte) ( m_prepproofs.length==0 ? 0 : m_prepproofs[ 0 ].length ) );

            mapper.writeMessagesTo( out, m_chkptproof );

            if( m_prepproofs.length>0 )
                for( OrderNetworkMessage[] pp : m_prepproofs )
                    mapper.writeMessagesTo( out, pp );
        }


        @Override
        public int calculateTypePlainPrefixSize(MessageMapper mapper)
        {
            return super.calculateTypePlainPrefixSize( mapper ) + Byte.BYTES + Integer.BYTES + Byte.SIZE;
        }


        @Override
        public int calculateTypeContentSize(MessageMapper mapper)
        {
            int size = 0;

            size += mapper.calculateMessageSizes( m_chkptproof );

            if( m_prepproofs.length>0 )
                for( OrderNetworkMessage[] pp : m_prepproofs )
                    size += mapper.calculateMessageSizes( pp );

            return size;
        }


        public PBFTViewChange(ByteBuffer in, MessageMapper mapper, Object extcntxt) throws IOException
        {
            super( in );

            byte nchkptproofs = in.get();
            int  nprepproofs  = in.getInt();
            byte proofsize    = in.get();

            m_chkptproof = mapper.readMessagesFrom( in, new Checkpoint[ nchkptproofs ], Checkpoint.class );

            m_prepproofs = new OrderNetworkMessage[ nprepproofs ][];

            if( nprepproofs>0 )
            {
                for( int i=0; i<nprepproofs; i++ )
                {
                    m_prepproofs[ i ] =
                        mapper.readMessagesFrom( in, new OrderNetworkMessage[ proofsize ], OrderNetworkMessage.class );
                }
            }
        }


        @Override
        public void prepareDigestion(MessageDigester digester)
        {
            super.prepareDigestion( digester );

            digester.prepareInnerMessagesForDigestion( m_chkptproof );

            for( OrderNetworkMessage[] pp : m_prepproofs )
                digester.prepareInnerMessagesForDigestion( pp );
        }


        @Override
        public void digestTypeContentTo(MessageDigestSink sink)
        {
            sink.putInnerMessages( m_chkptproof );

            for( OrderNetworkMessage[] pp : m_prepproofs )
                sink.putInnerMessages( pp );
        }


        @Override
        public int getTypeID()
        {
            return PBFT_VIEW_CHANGE_ID;
        }


        public Checkpoint[] getCheckpointCertificate()
        {
            return m_chkptproof;

        }


        public OrderNetworkMessage[][] getPrepareCertificates()
        {
            return m_prepproofs;
        }

    }


    public static class PBFTNewView extends AbstractViewChangeMessage
    {

        private PBFTViewChange[] m_vcproof;
        private PBFTPrePrepare[] m_newprepreps;


        public PBFTNewView(short sender, int viewno, byte shardno, PBFTViewChange[] vcproof, PBFTPrePrepare[] newprepreps)
        {
            super( sender, shardno, viewno );

            assert vcproof!=null && newprepreps!=null;

            m_vcproof     = vcproof;
            m_newprepreps = newprepreps;
        }


        @Override
        public void writeTypeContentTo(ByteBuffer out, MessageMapper mapper)
        {
            super.writeTypeContentTo( out, mapper );

            out.put( (byte) m_vcproof.length );
            out.putInt( m_newprepreps.length );

            mapper.writeMessagesTo( out, m_vcproof );
            mapper.writeMessagesTo( out, m_newprepreps );
        }


        @Override
        public int calculateTypePlainPrefixSize(MessageMapper mapper)
        {
            return super.calculateTypePlainPrefixSize( mapper ) + Byte.BYTES + Integer.BYTES;
        }


        @Override
        public int calculateTypeContentSize(MessageMapper mapper)
        {
            int size = 0;

            size += mapper.calculateMessageSizes( m_vcproof );
            size += mapper.calculateMessageSizes( m_newprepreps );

            return size;
        }


        public PBFTNewView(ByteBuffer in, MessageMapper mapper, Object extcntxt) throws IOException
        {
            super( in );

            byte nvcproof  = in.get();
            int  nnewpreps = in.getInt();

            m_vcproof     = mapper.readMessagesFrom( in, new PBFTViewChange[ nvcproof ], PBFTViewChange.class );
            m_newprepreps = mapper.readMessagesFrom( in, new PBFTPrePrepare[ nnewpreps ], PBFTPrePrepare.class );
        }


       @Override
       public void prepareDigestion(MessageDigester digester)
       {
           super.prepareDigestion( digester );

           digester.prepareInnerMessagesForDigestion( m_vcproof );
           digester.prepareInnerMessagesForDigestion( m_newprepreps );
       }


       @Override
       public void digestTypeContentTo(MessageDigestSink sink)
       {
           sink.putInnerMessages( m_vcproof );
           sink.putInnerMessages( m_newprepreps );
       }


       @Override
       public int getTypeID()
       {
           return PBFT_NEW_VIEW_ID;
       }


       public PBFTViewChange[] getViewChangeProof()
       {
           return m_vcproof;
       }


       public PBFTPrePrepare[] getNewPrePrepares()
       {
           return m_newprepreps;
       }

    }


    public static class PBFTNewViewStable extends InternalViewChangeMessage implements StableView
    {

        private final View          m_view;
        private final PBFTNewView[] m_nvshards;


        public PBFTNewViewStable(View view, PBFTNewView[] nvshards)
        {
            super( view.getNumber() );

            m_view     = view;
            m_nvshards = nvshards;
        }


        @Override
        public int getTypeID()
        {
            return ViewChangeMessages.NEW_VIEW_STABLE_ID;
        }


        @Override
        public View getView()
        {
            return m_view;
        }


        public PBFTNewView[] getNewViewShards()
        {
            return m_nvshards;
        }

    }

}

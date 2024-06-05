package reptor.replct.replicate.hybster.view;

import java.io.IOException;
import java.nio.ByteBuffer;

import reptor.distrbt.com.MessageDigestSink;
import reptor.distrbt.com.MessageDigester;
import reptor.distrbt.com.MessageMapper;
import reptor.replct.MessageCategoryID;
import reptor.replct.ProtocolID;
import reptor.replct.agree.View;
import reptor.replct.agree.checkpoint.CheckpointMessages.Checkpoint;
import reptor.replct.agree.checkpoint.OnePhaseCheckpointShard.CheckpointCertificate;
import reptor.replct.agree.order.OrderNetworkMessage;
import reptor.replct.agree.view.AbstractViewChangeMessage;
import reptor.replct.agree.view.StableView;
import reptor.replct.agree.view.ViewChangeMessages;
import reptor.replct.agree.view.InternalViewChangeMessages.InternalViewChangeMessage;
import reptor.replct.replicate.hybster.order.HybsterOrderMessages.HybsterPrepare;


public class HybsterViewChangeMessages
{

    private static final int HYBSTER_VIEW_CHANGE_BASE   = ProtocolID.HYBSTER | MessageCategoryID.VIEW_CHANGE;
    public  static final int HYBSTER_VIEW_CHANGE_ID     = HYBSTER_VIEW_CHANGE_BASE + 1;
    public  static final int HYBSTER_NEW_VIEW_ID        = HYBSTER_VIEW_CHANGE_BASE + 2;


    public static class HybsterViewChange extends AbstractViewChangeMessage
    {

        private final CheckpointCertificate m_chkptcert;
        private final OrderNetworkMessage[] m_prepmsgs;
        private final int                   m_viewno_from;
        private final HybsterViewChange     m_vc_from;
        private final int                   m_viewno_last;
        private final long                  m_orderno_last;


        public static HybsterViewChange createFromStableView(short sender, int viewno, short shardno,
                                  CheckpointCertificate chkptcert, OrderNetworkMessage[] prepmsgs,
                                  long orderno_last)
        {
            return new HybsterViewChange( sender, viewno, shardno, chkptcert, prepmsgs, orderno_last );
        }


        public static HybsterViewChange createFromIntermediateView(short sender, int viewno, short shardno,
                                                                    CheckpointCertificate chkptcert, OrderNetworkMessage[] prepmsgs,
                                                                    HybsterViewChange vc_from, int viewno_last)
        {
            return new HybsterViewChange( sender, viewno, shardno, chkptcert, prepmsgs, vc_from, viewno_last );
        }


        public HybsterViewChange(short sender, int viewno, short shardno,
                                  CheckpointCertificate chkptcert, OrderNetworkMessage[] prepmsgs,
                                  long orderno_last)
        {
            super( sender, shardno, viewno );

            assert prepmsgs!=null;

            m_chkptcert    = chkptcert;
            m_prepmsgs     = prepmsgs;
            m_viewno_last  = viewno-1;
            m_viewno_from  = m_viewno_last;
            m_vc_from      = null;
            m_orderno_last = orderno_last;
        }


        public HybsterViewChange(short sender, int viewno, short shardno,
                                  CheckpointCertificate chkptcert, OrderNetworkMessage[] prepmsgs,
                                  HybsterViewChange vc_from, int viewno_last)
        {
            super( sender, shardno, viewno );

            assert prepmsgs!=null;
            assert vc_from!=null;

            m_chkptcert    = chkptcert;
            m_prepmsgs     = prepmsgs;
            m_viewno_last  = viewno_last;
            m_viewno_from  = vc_from.getViewNumber();
            m_vc_from      = vc_from;
            m_orderno_last = 0;
        }


        @Override
        public void writeTypeContentTo(ByteBuffer out, MessageMapper mapper)
        {
            super.writeTypeContentTo( out, mapper );

            out.putInt( m_viewno_last );
            out.put( m_chkptcert==null ? 0 : (byte) m_chkptcert.getCheckpoints().length );
            out.putInt( m_prepmsgs.length );

            if( isFromStableView() )
                out.putLong( m_orderno_last );
            else
                mapper.writeMessageTo( out, m_vc_from );

            if( m_chkptcert!=null )
                mapper.writeMessagesTo( out, m_chkptcert.getCheckpoints() );
            mapper.writeMessagesTo( out, m_prepmsgs );
        }


        @Override
        public int calculateTypePlainPrefixSize(MessageMapper mapper)
        {
            int size = super.calculateTypePlainPrefixSize( mapper ) + Integer.BYTES + Byte.BYTES + Integer.BYTES;

            if( isFromStableView() )
                size += Long.BYTES;

            return size;
        }


        @Override
        public int calculateTypeContentSize(MessageMapper mapper)
        {
            int size = 0;

            if( !isFromStableView() )
                size += mapper.calculateMessageSize( m_vc_from );

            if( m_chkptcert!=null )
                size += mapper.calculateMessageSizes( m_chkptcert.getCheckpoints() );
            size += mapper.calculateMessageSizes( m_prepmsgs );

            return size;
        }


        public HybsterViewChange(ByteBuffer in, MessageMapper mapper, Object extcntxt) throws IOException
        {
            super( in );

            m_viewno_last   = in.getInt();

            byte nchkptmsgs = in.get();
            int  nprepmsgs  = in.getInt();

            if( isFromStableView() )
            {
                m_orderno_last = in.getLong();
                m_vc_from      = null;
                m_viewno_from  = m_viewno_last;
            }
            else
            {
                m_vc_from      = (HybsterViewChange) mapper.readMessageFrom( in );
                m_orderno_last = 0;
                m_viewno_from  = m_vc_from.getViewNumber();
            }

            if( nchkptmsgs==0 )
                m_chkptcert = null;
            else
                m_chkptcert = new CheckpointCertificate( mapper.readMessagesFrom( in, new Checkpoint[ nchkptmsgs ], Checkpoint.class ) );

            m_prepmsgs = mapper.readMessagesFrom( in, new OrderNetworkMessage[ nprepmsgs ], OrderNetworkMessage.class );
        }


        @Override
        public void prepareDigestion(MessageDigester digester)
        {
            super.prepareDigestion( digester );

            if( m_chkptcert!=null )
                digester.prepareInnerMessagesForDigestion( m_chkptcert.getCheckpoints() );
            digester.prepareInnerMessagesForDigestion( m_prepmsgs );
        }


        @Override
        public void digestTypeContentTo(MessageDigestSink sink)
        {
            if( m_chkptcert!=null )
                sink.putInnerMessages( m_chkptcert.getCheckpoints() );
            sink.putInnerMessages( m_prepmsgs );
        }


        @Override
        public int getTypeID()
        {
            return HYBSTER_VIEW_CHANGE_ID;
        }


        @Override
        protected String getTypeName()
        {
            return "HYBSTER-VIEW-CHANGE";
        }


        @Override
        public String toString()
        {
            String chkptstr = m_chkptcert==null ? "-" : Long.toString( m_chkptcert.getOrderNumber() );
            String prepstr  = m_prepmsgs.length==0 ?
                    "-" : String.format( "%d-%d", m_prepmsgs[ 0 ].getOrderNumber(), m_prepmsgs[ m_prepmsgs.length-1 ].getOrderNumber() );

            return String.format( "{%s|%s|%s|%d|%d-%d}", idString(), chkptstr, prepstr, m_viewno_from, m_orderno_last, m_viewno_last );
        }


        public boolean isFromStableView()
        {
            return m_viewno_last==m_viewno-1;
        }


        public CheckpointCertificate getCheckpointCertificate()
        {
            return m_chkptcert;
        }


        public OrderNetworkMessage[] getPrepareMessages()
        {
            return m_prepmsgs;
        }


        public int getNumberOfLastStableView()
        {
            return m_viewno_from;
        }


        public HybsterViewChange getViewChangeFromStableView()
        {
            return m_vc_from;
        }


        public int getNumberOfLastView()
        {
            return m_viewno_last;
        }


        public long getNumberOfLastActiveInstance()
        {
            return m_orderno_last;
        }

    }


    public static class HybsterNewView extends AbstractViewChangeMessage
    {

        private final HybsterViewChange[]  m_vcs;
        private final HybsterPrepare[]     m_newpreps;
        private final CheckpointCertificate m_chkptcert;


        public HybsterNewView(short sender, HybsterViewChange[] vcs, HybsterPrepare[] newpreps)
        {
            super( sender, vcs[ 0 ].getShardNumber(), vcs[ 0 ].getViewNumber() );

            assert vcs!=null;
            assert newpreps!=null;

            m_vcs       = vcs;
            m_newpreps  = newpreps;
            m_chkptcert = latestCheckpoint( vcs );

            assert newpreps.length==0 || newpreps[ 0 ].getOrderNumber()>getMinimumOrderNumber();
        }


        @Override
        public int getTypeID()
        {
            return HYBSTER_NEW_VIEW_ID;
        }


        public CheckpointCertificate getCheckpointCertificate()
        {
            return m_chkptcert;
        }


        public HybsterViewChange[] getViewChanges()
        {
            return m_vcs;
        }


        public HybsterPrepare[] getNewPrepares()
        {
            return m_newpreps;
        }


        public long getMinimumOrderNumber()
        {
            return m_chkptcert==null ? 0 : m_chkptcert.getOrderNumber();
        }


        public long getMaximumOrderNumber()
        {
            return m_newpreps.length==0 ? getMinimumOrderNumber() : m_newpreps[ m_newpreps.length-1 ].getOrderNumber();
        }


        @Override
        public void writeTypeContentTo(ByteBuffer out, MessageMapper mapper)
        {
            super.writeTypeContentTo( out, mapper );

            out.put( (byte) m_vcs.length );
            out.putInt( m_newpreps.length );

            mapper.writeMessagesTo( out, m_vcs );
            mapper.writeMessagesTo( out, m_newpreps );
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

            size += mapper.calculateMessageSizes( m_vcs );
            size += mapper.calculateMessageSizes( m_newpreps );

            return size;
        }


        public HybsterNewView(ByteBuffer in, MessageMapper mapper, Object extcntxt) throws IOException
        {
            super( in );

            byte nvcs      = in.get();
            int  nnewpreps = in.getInt();

            m_vcs       = mapper.readMessagesFrom( in, new HybsterViewChange[ nvcs ], HybsterViewChange.class );
            m_newpreps  = mapper.readMessagesFrom( in, new HybsterPrepare[ nnewpreps ], HybsterPrepare.class );
            m_chkptcert = latestCheckpoint( m_vcs );
        }


        @Override
        public void prepareDigestion(MessageDigester digester)
        {
            super.prepareDigestion( digester );

            digester.prepareInnerMessagesForDigestion( m_vcs );
            digester.prepareInnerMessagesForDigestion( m_newpreps );
        }


        @Override
        public void digestTypeContentTo(MessageDigestSink sink)
        {
            sink.putInnerMessages( m_vcs );
            sink.putInnerMessages( m_newpreps );
        }


        @Override
        public String toString()
        {
            String chkptstr = m_chkptcert==null ? "-" : Long.toString( m_chkptcert.getOrderNumber() );
            String prepstr  = m_newpreps.length==0 ?
                    "-" : String.format( "%d-%d", m_newpreps[ 0 ].getOrderNumber(), m_newpreps[ m_newpreps.length-1 ].getOrderNumber() );

            return String.format( "{%s|%s|%s}", idString(), chkptstr, prepstr );
        }


        private static CheckpointCertificate latestCheckpoint(HybsterViewChange[] vcs)
        {
            assert vcs.length>0;

            CheckpointCertificate latest = null;

            for( int i=0; i<vcs.length; i++ )
                if( latest==null || vcs[ i ].getCheckpointCertificate()!=null && vcs[ i ].getCheckpointCertificate().getOrderNumber()>latest.getOrderNumber() )
                    latest = vcs[ i ].getCheckpointCertificate();

            return latest;
        }

    }


    public static class HybsterNewViewStable extends InternalViewChangeMessage implements StableView
    {

        private final View             m_view;
        private final HybsterNewView[] m_nvshards;


        public HybsterNewViewStable(View view, HybsterNewView[] nvshards)
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
        protected String getTypeName()
        {
            return "NEW_VIEW_STABLE";
        }


        @Override
        public View getView()
        {
            return m_view;
        }


        public HybsterNewView[] getNewViewShards()
        {
            return m_nvshards;
        }

    }

}

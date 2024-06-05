package refit.hybstero.view;

import java.io.IOException;
import java.nio.ByteBuffer;

import reptor.distrbt.com.MessageDigestSink;
import reptor.distrbt.com.MessageDigester;
import reptor.distrbt.com.MessageMapper;
import reptor.replct.MessageCategoryID;
import reptor.replct.ProtocolID;
import reptor.replct.agree.common.view.AbstractViewChangeMessage;
import reptor.replct.agree.common.view.StableView;
import reptor.replct.agree.common.view.View;
import reptor.replct.agree.common.view.ViewChangeMessages;
import reptor.replct.agree.common.view.InternalViewChangeMessages.InternalViewChangeMessage;


public class HybsterViewChangeMessages
{

    private static final int HYBSTER_VIEW_CHANGE_BASE   = ProtocolID.HYBSTER | MessageCategoryID.VIEW_CHANGE;
    public  static final int HYBSTER_VIEW_CHANGE_ID     = HYBSTER_VIEW_CHANGE_BASE + 1;
    public  static final int HYBSTER_NEW_VIEW_ID        = HYBSTER_VIEW_CHANGE_BASE + 2;
    public  static final int HYBSTER_SWITCH_VIEW_ID     = HYBSTER_VIEW_CHANGE_BASE + 3;


    public static class HybsterViewChange extends AbstractViewChangeMessage
    {

        public HybsterViewChange(short sender, int viewno, short shardno)
        {
            super( sender, shardno, viewno );
        }


        public HybsterViewChange(ByteBuffer in, MessageMapper mapper, Object extcntxt) throws IOException
        {
            super( in );
        }


        @Override
        public int getTypeID()
        {
            return HYBSTER_VIEW_CHANGE_ID;
        }

    }


    public static class HybsterNewView extends AbstractViewChangeMessage
    {

        private final HybsterViewChange[] m_vcs;


        public HybsterNewView(short sender, HybsterViewChange[] vcs)
        {
            super( sender, vcs[ 0 ].getShardNumber(), vcs[ 0 ].getViewNumber() );

            m_vcs = vcs;
        }


        @Override
        public int getTypeID()
        {
            return HYBSTER_NEW_VIEW_ID;
        }


        public HybsterViewChange[] getViewChanges()
        {
            return m_vcs;
        }


        public HybsterNewView(ByteBuffer in, MessageMapper mapper, Object extcntxt) throws IOException
        {
            super( in );

            m_vcs = mapper.readMessagesFrom( in, new HybsterViewChange[ in.get() ], HybsterViewChange.class );
        }


        @Override
        public void writeTypeContentTo(ByteBuffer out, MessageMapper mapper)
        {
            super.writeTypeContentTo( out, mapper );

            out.put( (byte) m_vcs.length );
            mapper.writeMessagesTo( out, m_vcs );
        }


        @Override
        public int calculateTypeContentSize(MessageMapper mapper)
        {
            return mapper.calculateMessageSizes( m_vcs );
        }


        @Override
        public void prepareDigestion(MessageDigester digester)
        {
            super.prepareDigestion( digester );

            digester.prepareInnerMessagesForDigestion( m_vcs );
        }


        @Override
        public void digestTypeContentTo(MessageDigestSink sink)
        {
            sink.putInnerMessages( m_vcs );
        }

    }


    public static class HybsterSwitchView extends AbstractViewChangeMessage
    {

        private final byte[]  m_vote;

        public HybsterSwitchView(short sender, int viewno, byte[] vote, short shardno)
        {
            super( sender, shardno, viewno );

            m_vote = vote;
        }


        @Override
        public void writeTypeContentTo(ByteBuffer out, MessageMapper mapper)
        {
            super.writeTypeContentTo( out, mapper );

            out.putShort( (short) m_vote.length );
            out.put( m_vote );
        }

        @Override
        public int calculateTypePlainPrefixSize(MessageMapper mapper)
        {
            return super.calculateTypePlainPrefixSize( mapper ) + Short.BYTES + m_vote.length;
        }

        public HybsterSwitchView(ByteBuffer in, MessageMapper mapper, Object extcntxt) throws IOException
        {
            super( in );

            m_vote = new byte[ in.getShort() ];
            in.get( m_vote );
        }

        @Override
        public int getTypeID()
        {
            return HYBSTER_SWITCH_VIEW_ID;
        }

        public byte[] getVote()
        {
            return m_vote;
        }

    }


    public static class HybsterNewViewStable extends InternalViewChangeMessage implements StableView
    {
        private final View m_view;

        public HybsterNewViewStable(View view)
        {
            super( view.getNumber() );

            m_view = view;
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
    }

}

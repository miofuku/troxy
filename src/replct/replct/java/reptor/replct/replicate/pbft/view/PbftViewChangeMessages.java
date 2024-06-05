package reptor.replct.replicate.pbft.view;

import java.io.IOException;
import java.nio.ByteBuffer;

import reptor.distrbt.com.MessageDigestSink;
import reptor.distrbt.com.MessageDigester;
import reptor.distrbt.com.MessageMapper;
import reptor.replct.MessageCategoryID;
import reptor.replct.ProtocolID;
import reptor.replct.agree.View;
import reptor.replct.agree.view.AbstractViewChangeMessage;
import reptor.replct.agree.view.StableView;
import reptor.replct.agree.view.ViewChangeMessages;
import reptor.replct.agree.view.InternalViewChangeMessages.InternalViewChangeMessage;


public class PbftViewChangeMessages
{

    private static final int PBFT_VIEW_CHANGE_BASE   = ProtocolID.PBFT | MessageCategoryID.VIEW_CHANGE;
    public  static final int PBFT_VIEW_CHANGE_ID     = PBFT_VIEW_CHANGE_BASE + 1;
    public  static final int PBFT_NEW_VIEW_ID        = PBFT_VIEW_CHANGE_BASE + 2;


    public static class PbftViewChange extends AbstractViewChangeMessage
    {

        public PbftViewChange(short sender, int viewno, short shardno)
        {
            super( sender, shardno, viewno );
        }


        public PbftViewChange(ByteBuffer in, MessageMapper mapper, Object extcntxt) throws IOException
        {
            super( in );
        }


        @Override
        public int getTypeID()
        {
            return PBFT_VIEW_CHANGE_ID;
        }

    }


    public static class PbftNewView extends AbstractViewChangeMessage
    {

        private final PbftViewChange[] m_vcs;


        public PbftNewView(short sender, PbftViewChange[] vcs)
        {
            super( sender, vcs[ 0 ].getShardNumber(), vcs[ 0 ].getViewNumber() );

            m_vcs = vcs;
        }


        @Override
        public int getTypeID()
        {
            return PBFT_NEW_VIEW_ID;
        }


        public PbftViewChange[] getViewChanges()
        {
            return m_vcs;
        }


        public PbftNewView(ByteBuffer in, MessageMapper mapper, Object extcntxt) throws IOException
        {
            super( in );

            m_vcs = mapper.readMessagesFrom( in, new PbftViewChange[ in.get() ], PbftViewChange.class );
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


    public static class PbftNewViewStable extends InternalViewChangeMessage implements StableView
    {
        private final View m_view;

        public PbftNewViewStable(View view)
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

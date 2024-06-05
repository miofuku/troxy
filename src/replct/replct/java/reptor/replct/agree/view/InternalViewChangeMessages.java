package reptor.replct.agree.view;


public class InternalViewChangeMessages
{

    public static abstract class InternalViewChangeMessage implements ViewDependentMessage
    {
        protected final int m_viewno;

        public InternalViewChangeMessage(int viewno)
        {
            m_viewno = viewno;
        }

        @Override
        public final int getViewNumber()
        {
            return m_viewno;
        }

        @Override
        public String toString()
        {
            return String.format( "{%s}", idString() );
        }

        protected String idString()
        {
            return String.format( "%s|%d", getTypeName(), m_viewno );
        }

        protected String getTypeName()
        {
            return getClass().getSimpleName().toUpperCase();
        }
    }


    public static abstract class InternalViewChangeShardMessage extends InternalViewChangeMessage implements ViewChangeShardMessage
    {
        private final short m_shardno;

        public InternalViewChangeShardMessage(short shardno, int viewno)
        {
            super( viewno );

            m_shardno = shardno;
        }

        @Override
        public short getShardNumber()
        {
            return m_shardno;
        }

        @Override
        protected String idString()
        {
            return String.format( "%s|%d:%d", getTypeName(), m_shardno, m_viewno );
        }

    }


    public abstract static class ViewChangeVerificationMessage<M> extends InternalViewChangeMessage
    {
        private final M m_msg;

        public ViewChangeVerificationMessage(int viewno, M msg)
        {
            super( viewno );

            m_msg = msg;
        }

        public M getMessage()
        {
            return m_msg;
        }
    }

}

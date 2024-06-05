package reptor.replct.replicate.hybster.view;

import reptor.replct.agree.checkpoint.OnePhaseCheckpointShard.CheckpointCertificate;
import reptor.replct.agree.order.OrderNetworkMessage;
import reptor.replct.agree.view.ViewChangeMessages;
import reptor.replct.agree.view.InternalViewChangeMessages.InternalViewChangeMessage;
import reptor.replct.agree.view.InternalViewChangeMessages.InternalViewChangeShardMessage;


public class HybsterLocalViewChangePreparationMessages
{

    public static class HybsterOrderShardViewChange extends InternalViewChangeShardMessage
    {
        private final OrderNetworkMessage[] m_prepmsgs;
        private final long                  m_orderno_last;

        public HybsterOrderShardViewChange(short shardno, int viewno, OrderNetworkMessage[] prepmsgs, long orderno_last)
        {
            super( shardno, viewno );

            assert prepmsgs!=null;

            m_prepmsgs     = prepmsgs;
            m_orderno_last = orderno_last;
        }

        @Override
        public int getTypeID()
        {
            return ViewChangeMessages.ORDER_SHARD_VIEW_CHANGE_ID;
        }

        @Override
        public String toString()
        {
            if( m_prepmsgs.length==0 )
                return String.format( "{%s|-}", idString() );
            else
                return String.format( "{%s|%d-%d|%d}", idString(), m_prepmsgs[ 0 ].getOrderNumber(),
                                      m_prepmsgs[ m_prepmsgs.length-1 ].getOrderNumber(), m_orderno_last );
        }

        @Override
        public String getTypeName()
        {
            return "ORDER_SHARD_VIEW_CHANGE";
        }

        public OrderNetworkMessage[] getPrepareMessages()
        {
            return m_prepmsgs;
        }

        public long getNumberOfLastActiveInstance()
        {
            return m_orderno_last;
        }
    }


    public static class HybsterCheckpointShardViewChange extends InternalViewChangeShardMessage
    {
        private final CheckpointCertificate m_chkptcert;

        public HybsterCheckpointShardViewChange(short shardno, int viewno, CheckpointCertificate chkptcert)
        {
            super( shardno, viewno );

            m_chkptcert = chkptcert;
        }

        @Override
        public int getTypeID()
        {
            return ViewChangeMessages.CHECKPOINT_SHARD_VIEW_CHANGE_ID;
        }

        @Override
        public String toString()
        {
            if( m_chkptcert==null )
                return String.format( "{%s|-}", idString() );
            else
                return String.format( "{%s|%d}", idString(), m_chkptcert.getOrderNumber() );
        }

        @Override
        public String getTypeName()
        {
            return "CHECKPOINT_SHARD_VIEW_CHANGE";
        }

        public CheckpointCertificate getCheckpointCertificate()
        {
            return m_chkptcert;
        }
    }

    public static class HybsterViewChangeShardViewChange extends InternalViewChangeShardMessage
    {
        public HybsterViewChangeShardViewChange(short shardno, int viewno)
        {
            super( shardno, viewno );
        }

        @Override
        public int getTypeID()
        {
            return ViewChangeMessages.VIEW_SHARD_VIEW_CHANGE_ID;
        }

        @Override
        public String getTypeName()
        {
            return "VIEW_SHARD_VIEW_CHANGE";
        }
    }


    public static class HybsterViewChangeReady extends InternalViewChangeMessage
    {
        private final CheckpointCertificate          m_chkptcert;
        private final HybsterOrderShardViewChange[] m_vcs;

        public HybsterViewChangeReady(int viewno, CheckpointCertificate chkptcert, HybsterOrderShardViewChange[] vcs)
        {
            super( viewno );

            m_chkptcert = chkptcert;
            m_vcs       = vcs;
        }

        @Override
        public int getTypeID()
        {
            return ViewChangeMessages.VIEW_CHANGE_READY_ID;
        }

        @Override
        public String toString()
        {
            return String.format( "{%s|%d}", idString(), m_chkptcert.getOrderNumber() );
        }

        @Override
        public String getTypeName()
        {
            return "VIEW_CHANGE_READY";
        }

        public CheckpointCertificate getCheckpointCertificate()
        {
            return m_chkptcert;
        }

        public HybsterOrderShardViewChange[] getOrderViewChanges()
        {
            return m_vcs;
        }
    }

}

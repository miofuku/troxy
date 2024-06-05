package refit.pbfto.view;

import reptor.replct.agree.common.checkpoint.CheckpointMessages.Checkpoint;
import reptor.replct.agree.common.order.OrderNetworkMessage;
import reptor.replct.agree.common.view.ViewChangeMessages;
import reptor.replct.agree.common.view.InternalViewChangeMessages.InternalViewChangeMessage;
import reptor.replct.agree.common.view.InternalViewChangeMessages.InternalViewChangeShardMessage;


public class PBFTLocalViewChangePreparationMessages
{

    public static class PBFTOrderShardViewChange extends InternalViewChangeShardMessage
    {
        private final OrderNetworkMessage[][] m_prepproofs;

        public PBFTOrderShardViewChange(short shardno, int viewno, OrderNetworkMessage[][] prepproofs)
        {
            super( shardno, viewno );

            m_prepproofs = prepproofs;
        }

        @Override
        public int getTypeID()
        {
            return ViewChangeMessages.ORDER_SHARD_VIEW_CHANGE_ID;
        }

        @Override
        public String getTypeName()
        {
            return "PBFT_ORDER_SHARD_VIEW_CHANGE";
        }

        public OrderNetworkMessage[][] getPrepareProofs()
        {
            return m_prepproofs;
        }
    }


    public static class PBFTCheckpointShardViewChange extends InternalViewChangeShardMessage
    {
        private final Checkpoint[] m_chkptproof;

        public PBFTCheckpointShardViewChange(short shardno, int viewno, Checkpoint[] chkptproof)
        {
            super( shardno, viewno );

            m_chkptproof = chkptproof;
        }

        @Override
        public int getTypeID()
        {
            return ViewChangeMessages.CHECKPOINT_SHARD_VIEW_CHANGE_ID;
        }

        @Override
        public String getTypeName()
        {
            return "PBFT_CHECKPOINT_SHARD_VIEW_CHANGE";
        }

        public Checkpoint[] getCheckpointProof()
        {
            return m_chkptproof;
        }
    }

    public static class PBFTViewChangeShardViewChange extends InternalViewChangeShardMessage
    {
        public PBFTViewChangeShardViewChange(short shardno, int viewno)
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
            return "PBFT_VIEW_SHARD_VIEW_CHANGE";
        }
    }


    public static class PBFTViewChangeReady extends InternalViewChangeMessage
    {
        private final Checkpoint[]               m_chkptproof;
        private final PBFTOrderShardViewChange[] m_vcs;

        public PBFTViewChangeReady(int viewno, Checkpoint[] chkptproof, PBFTOrderShardViewChange[] vcs)
        {
            super( viewno );

            m_chkptproof = chkptproof;
            m_vcs        = vcs;
        }

        @Override
        public int getTypeID()
        {
            return ViewChangeMessages.VIEW_CHANGE_READY_ID;
        }

        public Checkpoint[] getCheckpointProof()
        {
            return m_chkptproof;
        }

        public PBFTOrderShardViewChange[] getOrderViewChanges()
        {
            return m_vcs;
        }
    }

}

package reptor.replct.replicate.hybster.view;

import reptor.replct.agree.view.ViewChangeMessages;
import reptor.replct.agree.view.InternalViewChangeMessages.InternalViewChangeMessage;
import reptor.replct.agree.view.InternalViewChangeMessages.ViewChangeVerificationMessage;
import reptor.replct.replicate.hybster.view.HybsterViewChangeMessages.HybsterViewChange;


public class HybsterLocalNewViewMessages
{

    public static class HybsterConfirmViewChange extends ViewChangeVerificationMessage<HybsterViewChange[]>
    {
        public HybsterConfirmViewChange(HybsterViewChange[] msg)
        {
            super( msg[ 0 ].getViewNumber(), msg );
        }

        @Override
        public int getTypeID()
        {
            return ViewChangeMessages.CONFIRM_VIEW_CHANGE_ID;
        }
    }

    public static class HybsterViewChangeShardConfirmed extends ViewChangeVerificationMessage<HybsterViewChange>
    {
        public HybsterViewChangeShardConfirmed(HybsterViewChange msg)
        {
            super( msg.getViewNumber(), msg );
        }

        @Override
        public int getTypeID()
        {
            return ViewChangeMessages.VIEW_CHANGE_CONFIRMED_ID;
        }
    }

    public static class HybsterNewViewReady extends InternalViewChangeMessage
    {
        private final HybsterViewChange[][] m_nvproof;
        private final long m_mins;
        private final long m_maxs;

        public HybsterNewViewReady(int viewno, HybsterViewChange[][] nvproof, long mins, long maxs)
        {
            super( viewno );

            m_nvproof = nvproof;
            m_mins    = mins;
            m_maxs    = maxs;
        }

        @Override
        public int getTypeID()
        {
            return ViewChangeMessages.NEW_VIEW_READY_ID;
        }

        public HybsterViewChange[][] getNewViewProofs()
        {
            return m_nvproof;
        }

        public long getMinS()
        {
            return m_mins;
        }

        public long getMaxS()
        {
            return m_maxs;
        }
    }

}

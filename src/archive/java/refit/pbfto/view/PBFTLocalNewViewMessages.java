package refit.pbfto.view;

import refit.pbfto.view.PBFTViewChangeMessages.PBFTViewChange;
import reptor.replct.agree.common.view.ViewChangeMessages;
import reptor.replct.agree.common.view.InternalViewChangeMessages.InternalViewChangeMessage;
import reptor.replct.agree.common.view.InternalViewChangeMessages.ViewChangeVerificationMessage;


public class PBFTLocalNewViewMessages
{

    public static class PBFTConfirmViewChange extends ViewChangeVerificationMessage<PBFTViewChange[]>
    {
        public PBFTConfirmViewChange(PBFTViewChange[] msg)
        {
            super( msg[ 0 ].getViewNumber(), msg );
        }

        @Override
        public int getTypeID()
        {
            return ViewChangeMessages.CONFIRM_VIEW_CHANGE_ID;
        }
    }

    public static class PBFTViewChangeShardConfirmed extends ViewChangeVerificationMessage<PBFTViewChange>
    {
        public PBFTViewChangeShardConfirmed(PBFTViewChange msg)
        {
            super( msg.getViewNumber(), msg );
        }

        @Override
        public int getTypeID()
        {
            return ViewChangeMessages.VIEW_CHANGE_CONFIRMED_ID;
        }
    }

    public static class PBFTNewViewReady extends InternalViewChangeMessage
    {
        private final PBFTViewChange[][] m_nvproof;
        private final long m_mins;
        private final long m_maxs;

        public PBFTNewViewReady(int viewno, PBFTViewChange[][] nvproof, long mins, long maxs)
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

        public PBFTViewChange[][] getNewViewProofs()
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

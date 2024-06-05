package refit.pbfto.view;

import refit.pbfto.view.PBFTViewChangeMessages.PBFTNewView;
import reptor.replct.agree.common.view.ViewChangeMessages;
import reptor.replct.agree.common.view.InternalViewChangeMessages.ViewChangeVerificationMessage;


public class PBFTLocalStableViewMessages
{

    public static class PBFTConfirmNewView extends ViewChangeVerificationMessage<PBFTNewView[]>
    {
        public PBFTConfirmNewView(PBFTNewView[] msg)
        {
            super( msg[ 0 ].getViewNumber(), msg );
        }

        @Override
        public int getTypeID()
        {
            return ViewChangeMessages.CONFIRM_NEW_VIEW_ID;
        }
    }

    public static class PBFTNewViewShardConfirmed extends ViewChangeVerificationMessage<PBFTNewView>
    {
        public PBFTNewViewShardConfirmed(PBFTNewView msg)
        {
            super( msg.getViewNumber(), msg );
        }

        @Override
        public int getTypeID()
        {
            return ViewChangeMessages.NEW_VIEW_SHARD_CONFIRMED_ID;
        }
    }

}

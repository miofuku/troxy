package reptor.replct.replicate.hybster.view;

import reptor.replct.agree.view.ViewChangeMessages;
import reptor.replct.agree.view.InternalViewChangeMessages.ViewChangeVerificationMessage;
import reptor.replct.replicate.hybster.view.HybsterViewChangeMessages.HybsterNewView;


public class HybsterLocalStableViewMessages
{

    public static class HybsterConfirmNewView extends ViewChangeVerificationMessage<HybsterNewView[]>
    {
        public HybsterConfirmNewView(HybsterNewView[] msg)
        {
            super( msg[ 0 ].getViewNumber(), msg );
        }

        @Override
        public int getTypeID()
        {
            return ViewChangeMessages.CONFIRM_NEW_VIEW_ID;
        }
    }

    public static class HybsterNewViewShardConfirmed extends ViewChangeVerificationMessage<HybsterNewView>
    {
        public HybsterNewViewShardConfirmed(HybsterNewView msg)
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

package reptor.replct.agree.view;

import reptor.replct.agree.view.InternalViewChangeMessages.InternalViewChangeMessage;


public class LocalViewChangePreparationMessages
{

    public static class RequestViewChange extends InternalViewChangeMessage
    {
        public RequestViewChange(int viewno)
        {
            super( viewno );
        }

        @Override
        public int getTypeID()
        {
            return ViewChangeMessages.REQUEST_VIEW_CHANGE_ID;
        }
    }


    public static class PrepareViewChange extends InternalViewChangeMessage
    {
        public PrepareViewChange(int viewno)
        {
            super( viewno );
        }

        @Override
        public int getTypeID()
        {
            return ViewChangeMessages.PREPARE_VIEW_CHANGE_ID;
        }
    }

}

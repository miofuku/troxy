package reptor.replct.agree.view;

import reptor.replct.MessageCategoryID;
import reptor.replct.ProtocolID;


public class ViewChangeMessages
{

    private static final int VIEW_CHANGE_BASE                = ProtocolID.COMMON | MessageCategoryID.VIEW_CHANGE;
    public  static final int REQUEST_VIEW_CHANGE_ID          = VIEW_CHANGE_BASE + 1;
    public  static final int PREPARE_VIEW_CHANGE_ID          = VIEW_CHANGE_BASE + 2;
    public  static final int ORDER_SHARD_VIEW_CHANGE_ID      = VIEW_CHANGE_BASE + 3;
    public  static final int VIEW_SHARD_VIEW_CHANGE_ID       = VIEW_CHANGE_BASE + 4;
    public  static final int CHECKPOINT_SHARD_VIEW_CHANGE_ID = VIEW_CHANGE_BASE + 5;
    public  static final int VIEW_CHANGE_READY_ID            = VIEW_CHANGE_BASE + 6;
    public  static final int CONFIRM_VIEW_CHANGE_ID          = VIEW_CHANGE_BASE + 7;
    public  static final int VIEW_CHANGE_CONFIRMED_ID        = VIEW_CHANGE_BASE + 8;
    public  static final int NEW_VIEW_READY_ID               = VIEW_CHANGE_BASE + 9;
    public  static final int CONFIRM_NEW_VIEW_ID             = VIEW_CHANGE_BASE + 10;
    public  static final int NEW_VIEW_SHARD_CONFIRMED_ID     = VIEW_CHANGE_BASE + 11;
    public  static final int NEW_VIEW_STABLE_ID              = VIEW_CHANGE_BASE + 12;

}

package reptor.replct;


public class MessageCategoryID
{

    public static final int CLIENT          = MessageTypeID.BASE | 0;
    public static final int ORDER           = MessageTypeID.BASE | ( 1 << MessageTypeID.CATEGORY_SHIFT );
    public static final int CHECKPOINT      = MessageTypeID.BASE | ( 2 << MessageTypeID.CATEGORY_SHIFT );
    public static final int VIEW_CHANGE     = MessageTypeID.BASE | ( 3 << MessageTypeID.CATEGORY_SHIFT );
    public static final int EXECUTION       = MessageTypeID.BASE | ( 4 << MessageTypeID.CATEGORY_SHIFT );
    public static final int WORKER          = MessageTypeID.BASE | ( 5 << MessageTypeID.CATEGORY_SHIFT );
    public static final int RECONFIGURATION = MessageTypeID.BASE | ( 6 << MessageTypeID.CATEGORY_SHIFT );
    public static final int COMMUNICATION   = MessageTypeID.BASE | ( 7 << MessageTypeID.CATEGORY_SHIFT );


    protected MessageCategoryID()
    {

    }

}

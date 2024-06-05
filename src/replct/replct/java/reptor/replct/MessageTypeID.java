package reptor.replct;

// Protocol (P) & Category (C) & Number (N)
// -------1 -----PPP CCCCCCCC NNNNNNNN

public class MessageTypeID
{

    public static final int UNSET = 0;
    public static final int BASE  = 1 << 24;

    public static final int RESERVED_SHIFT = 19;
    public static final int PROTOCOL_SHIFT = 16;
    public static final int CATEGORY_SHIFT = 8;

    public static final int PROTOCOL_MASK  = BASE | ( 1 << RESERVED_SHIFT-PROTOCOL_SHIFT ) - 1 << PROTOCOL_SHIFT;
    public static final int CATEGORY_MASK  = BASE | ( 1 << PROTOCOL_SHIFT-CATEGORY_SHIFT ) - 1 << CATEGORY_SHIFT;;
    public static final int NUMBER_MASK    = BASE | ( 1 << CATEGORY_SHIFT ) - 1;


    public static final int protocol(int typeid)
    {
        return typeid & PROTOCOL_MASK;
    }


    public static final int category(int typeid)
    {
        return typeid & CATEGORY_MASK;
    }


    public static final int number(int typeid)
    {
        return typeid & NUMBER_MASK;
    }


    protected MessageTypeID()
    {

    }

}

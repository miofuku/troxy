package reptor.replct;


public class ProtocolID
{

    public static final int COMMON   = MessageTypeID.BASE | 0;
    public static final int PBFT     = MessageTypeID.BASE | ( 1 << MessageTypeID.PROTOCOL_SHIFT );
    public static final int HYBSTER  = MessageTypeID.BASE | ( 2 << MessageTypeID.PROTOCOL_SHIFT );


    protected ProtocolID()
    {

    }

}

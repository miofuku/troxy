package reptor.replct.agree.checkpoint;


public enum CheckpointMode
{

    CREATE( 0 ),
    SEND( 1 ),
    APPLY( 2 ),
    NODE_PROGRESS( 3 ),
    REGULAR( 4 );

    private final int m_order;

    private CheckpointMode(int order)
    {
        m_order = order;
    }

    public final boolean includes(CheckpointMode mode)
    {
        return m_order >= mode.m_order;
    }

}
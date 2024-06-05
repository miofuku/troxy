package reptor.bench.apply.zk;

public enum MWZooKeeperOperation
{

    CREATE( false ),
    DELETE( false ),
    GET_DATA( true ),
    SET_DATA( false ),
    CLOSE_SESSION( false ); // Server-internal operation

    private boolean m_isreadonly;

    private MWZooKeeperOperation(boolean isreadonly)
    {
        m_isreadonly = isreadonly;
    }

    public boolean isReadOnly()
    {
        return m_isreadonly;
    }

}

package reptor.replct.replicate.pbft.order;


public enum PbftOrderVariant
{

    AgreeOnFullCommand( false, false ),
    FullProposalHashedVotes( false, true );

    private final boolean m_hashprops;
    private final boolean m_hashvotes;


    private PbftOrderVariant(boolean hashprops, boolean hashvotes)
    {
        m_hashprops = hashprops;
        m_hashvotes = hashvotes;
    }

    public boolean useHashedProposals()
    {
        return m_hashprops;
    }

    public boolean useHashedVotes()
    {
        return m_hashvotes;
    }

}

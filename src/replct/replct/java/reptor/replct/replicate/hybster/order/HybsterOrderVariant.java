package reptor.replct.replicate.hybster.order;


public enum HybsterOrderVariant
{

    AgreeOnFullCommand( false, false, false ),
    FullProposalHashedVotes( false, true, false ),
    FullProposalCertificateVotes( false, false, true );

    // TODO: Future work.
//    HashedProposalHashedVotes( true, true, false ),
//    HashedProposalCertificateVotes( true, false, true );


    private final boolean m_hashprops;
    private final boolean m_hashvotes;
    private final boolean m_certvotes;


    private HybsterOrderVariant(boolean hashprops, boolean hashvotes, boolean certvotes)
    {
        m_hashprops = hashprops;
        m_hashvotes = hashvotes;
        m_certvotes = certvotes;
    }

    public boolean useHashedProposals()
    {
        return m_hashprops;
    }

    public boolean useHashedVotes()
    {
        return m_hashvotes;
    }

    public boolean useCertificateVotes()
    {
        return m_certvotes;
    }

}

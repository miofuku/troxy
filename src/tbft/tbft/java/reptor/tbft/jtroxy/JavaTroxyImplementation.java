package reptor.tbft.jtroxy;

import reptor.distrbt.certify.mac.MacCertification;
import reptor.tbft.Troxy;
import reptor.tbft.TroxyImplementation;
import reptor.tbft.invoke.TransBFTInvocation;


public class JavaTroxyImplementation implements TroxyImplementation
{

    private final TransBFTInvocation    m_invoke;


    public JavaTroxyImplementation(TransBFTInvocation invoke)
    {
        m_invoke = invoke;
    }


    @Override
    public String toString()
    {
        return "jTroxy";
    }


    @Override
    public MacCertification getProposalCertification()
    {
        return JavaTroxy.PROPOSAL_CERTIFICATION;
    }


    @Override
    public Troxy createTroxy(byte repno)
    {
        return new JavaTroxy( repno, m_invoke );
    }

}

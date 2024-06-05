package reptor.tbftc.ctroxy;

import reptor.distrbt.certify.mac.MacCertification;
import reptor.tbft.Troxy;
import reptor.tbft.TroxyImplementation;
import reptor.tbft.invoke.TransBFTInvocation;

public class CTroxyImplementation implements TroxyImplementation
{
    private final TransBFTInvocation m_invoke;

    private boolean m_useenclave;

    public CTroxyImplementation(TransBFTInvocation invoke, boolean useenclave)
    {
        m_invoke     = invoke;
        m_useenclave = useenclave;
    }

    @Override
    public MacCertification getProposalCertification()
    {
        return null; // ????
    }

    @Override
    public Troxy createTroxy(byte repno)
    {
        try
        {
            return new CTroxy( repno, m_invoke, m_useenclave );
        } catch(Exception e)
        {
            e.printStackTrace();
            System.exit(-2);
        }
        return null;
    }
}

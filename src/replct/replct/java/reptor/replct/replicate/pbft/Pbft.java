package reptor.replct.replicate.pbft;

import reptor.replct.agree.AgreementProtocol;
import reptor.replct.common.quorums.QuorumDefinition;
import reptor.replct.common.quorums.QuorumDefinitions;


public class Pbft implements AgreementProtocol
{

    public static final Pbft INSTANCE = new Pbft();


    @Override
    public String toString()
    {
        return "PBFT";
    }


    @Override
    public QuorumDefinition getDefaultQuorum()
    {
        return QuorumDefinitions.CORRECT_INTERSECTION;
    }

}

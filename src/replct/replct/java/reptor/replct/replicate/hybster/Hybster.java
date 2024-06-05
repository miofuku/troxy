package reptor.replct.replicate.hybster;

import reptor.replct.agree.AgreementProtocol;
import reptor.replct.common.quorums.QuorumDefinition;
import reptor.replct.common.quorums.QuorumDefinitions;


public class Hybster implements AgreementProtocol
{

    public static final Hybster INSTANCE = new Hybster();


    @Override
    public String toString()
    {
        return "Hybster";
    }


    @Override
    public QuorumDefinition getDefaultQuorum()
    {
        return QuorumDefinitions.CORRECT_INTERSECTION;
    }

}

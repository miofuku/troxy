package reptor.tbft;

import reptor.distrbt.certify.mac.MacCertification;


public interface TroxyImplementation
{
    MacCertification    getProposalCertification();

    Troxy               createTroxy(byte repno);
}

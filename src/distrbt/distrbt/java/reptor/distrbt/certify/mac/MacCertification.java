package reptor.distrbt.certify.mac;

import reptor.chronos.Immutable;
import reptor.distrbt.certify.BidirectionalCertification;


@Immutable
public interface MacCertification extends BidirectionalCertification<MacAuthorityInstanceHolder, SharedKeyHolder>
{

    @Override
    MacAlgorithm getProofAlgorithm();

    MacProvider  createCertificationProvider(MacAuthorityInstance authinst);

    @Override
    default MacProvider createCertificationProvider(MacAuthorityInstanceHolder authinst)
    {
        return createCertificationProvider( authinst.getMacAuthorityInstance() );
    }

}

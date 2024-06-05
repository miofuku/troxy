package reptor.distrbt.certify.hash;

import reptor.chronos.Immutable;
import reptor.distrbt.certify.BidirectionalCertification;


@Immutable
public interface HashCertification extends BidirectionalCertification<HashAuthorityInstanceHolder, Object>
{

    @Override
    HashProofAlgorithm  getProofAlgorithm();

    HashProvider        createCertificationProvider(HashAuthorityInstance authinst);

    @Override
    default HashProvider createCertificationProvider(HashAuthorityInstanceHolder authinst)
    {
        return createCertificationProvider( authinst.getHashAuthorityInstance() );
    }

}

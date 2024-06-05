package reptor.distrbt.certify.debug;

import reptor.chronos.Immutable;
import reptor.distrbt.certify.BidirectionalCertification;


@Immutable
public interface DigestMacCertification extends BidirectionalCertification<DigestMacAuthorityInstanceHolder, ProcessIDHolder>
{

    @Override
    DigestMacAlgorithm getProofAlgorithm();

    DigestMacProvider  createCertificationProvider(DigestMacAuthorityInstance authinst);

    @Override
    default DigestMacProvider createCertificationProvider(DigestMacAuthorityInstanceHolder authinst)
    {
        return createCertificationProvider( authinst.getDigestMacAuthorityInstance() );
    }

}

package reptor.distrbt.certify.mac;

import reptor.distrbt.certify.SingleProofFormat;


public interface SingleMacFormat extends SingleProofFormat, MacCertification
{
    @Override
    MacAlgorithm getProofAlgorithm();

    default MacProvider createCertificationProvider(MacAuthorityInstance authinst)
    {
        return authinst.createMacProvider( this );
    }

    @Override
    default MacProvider createCertificationProvider(MacAuthorityInstanceHolder authinst)
    {
        return createCertificationProvider( authinst.getMacAuthorityInstance() );
    }

    @Override
    default SingleMacFormat getCertificateFormat()
    {
        return this;
    }
}

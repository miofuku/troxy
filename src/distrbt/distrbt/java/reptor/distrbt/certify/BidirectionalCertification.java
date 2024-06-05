package reptor.distrbt.certify;

import reptor.chronos.Immutable;

@Immutable
public interface BidirectionalCertification<I, K> extends SingleAlgorithmCertification<I, K>
{
    CertificateFormat               getCertificateFormat();

    @Override
    CertificateProvider<? super K>  createCertificationProvider(I authinst);
}

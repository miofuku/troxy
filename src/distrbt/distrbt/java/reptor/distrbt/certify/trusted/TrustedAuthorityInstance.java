package reptor.distrbt.certify.trusted;

import reptor.chronos.Commutative;
import reptor.distrbt.certify.CertificationAuthorityInstance;

@Commutative
public interface TrustedAuthorityInstance extends CertificationAuthorityInstance
{
    TrustedMacProvider          createTrustedMacProvider(SingleTrustedMacFormat certformat);
    TrustedCounterProvider      createTrustedCounterProvider(SingleTrustedMacFormat certformat, int ctrno);
    SequentialCounterProvider   createSequentialCounterProvider(SingleCounterFormat certformat, int ctrno);
}

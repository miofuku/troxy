package reptor.distrbt.certify;

import reptor.chronos.Commutative;
import reptor.chronos.Orphic;


@Commutative
public interface VerifierGroup extends Orphic
{

    Verifier getVerifier(int index);

    int size();

}

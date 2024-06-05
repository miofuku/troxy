package reptor.distrbt.certify;

import java.util.HashSet;
import java.util.Set;

import reptor.chronos.Immutable;


@Immutable
public interface CertificationMethod<I, K>
{

    ProofAlgorithm                      getCertificationAlgorithm();
    ProofAlgorithm                      getVerificationAlgorithm();

    CertificationProvider<? super K>    createCertificationProvider(I insts);

    default boolean usesProofAlgorithm(Class<?> algo)
    {
        return algo.isInstance( getCertificationAlgorithm() ) || algo.isInstance( getVerificationAlgorithm() );
    }

    default Set<KeyType> getRequiredKeyTypes()
    {
        HashSet<KeyType> set = new HashSet<>();

        if( getCertificationAlgorithm()!=null )
            set.addAll( getCertificationAlgorithm().getRequiredKeyTypes() );

        if( getVerificationAlgorithm()!=null )
            set.addAll( getVerificationAlgorithm().getRequiredKeyTypes() );

        return set;
    }

}

package reptor.distrbt.certify;

import java.util.Set;

import reptor.chronos.ImmutableObject;
import reptor.jlib.entities.Named;


public interface ProofAlgorithm extends ImmutableObject, Named
{

    ProofType       getProofType();
    int             getMaximumProofSize();
    Set<KeyType>    getRequiredKeyTypes();

    @Override
    boolean     equals(Object obj);
    @Override
    int         hashCode();

}

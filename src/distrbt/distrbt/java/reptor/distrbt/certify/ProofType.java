package reptor.distrbt.certify;

import reptor.chronos.ImmutableObject;
import reptor.jlib.entities.Named;


public interface ProofType extends ImmutableObject, Named
{

    boolean ensuresIntegrity();
    boolean isAuthentic();
    boolean isNonRepudiable();
    boolean isUnique();

}

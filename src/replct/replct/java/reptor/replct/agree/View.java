package reptor.replct.agree;

import reptor.replct.ReplicaPeerGroup;

public interface View
{

    int                 getNumber();
    ReplicaPeerGroup    getReplicaGroup();
    // This is perhaps a part of the group configuration.
    byte                getCoordinator(int viewno);

    default byte getReplicaNumber()
    {
        return getReplicaGroup().getReplicaNumber();
    }

    default byte getNumberOfReplicas()
    {
        return getReplicaGroup().size();
    }

    default byte getNumberOfTolerableFaults()
    {
        return getReplicaGroup().getNumberOfTolerableFaults();
    }

}

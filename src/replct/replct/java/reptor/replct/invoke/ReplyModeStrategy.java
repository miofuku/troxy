package reptor.replct.invoke;

import reptor.chronos.Immutable;
import reptor.replct.ReplicaPeerGroup;


@Immutable
public interface ReplyModeStrategy
{
    boolean                     getUseHashedReplies();

    ReplyModeStrategyInstance   createInstance(short repno, short nreplicas, short nfaults);

    default ReplyModeStrategyInstance createInstance(ReplicaPeerGroup repgroup)
    {
        return createInstance( repgroup.getReplicaNumber(), repgroup.size(),
                               repgroup.getNumberOfTolerableFaults() );
    }
}

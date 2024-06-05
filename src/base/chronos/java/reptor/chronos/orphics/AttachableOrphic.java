package reptor.chronos.orphics;

import reptor.chronos.Orphic;

public interface AttachableOrphic<M> extends Orphic
{
    void    bindToMaster(M master);
    // Optional
    void    unbindFromMaster(M master);
}

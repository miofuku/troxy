package reptor.chronos.context;

import reptor.chronos.Orphic;

public interface MasterContext<D extends ChronosDomainContext> extends Orphic
{
    // If a branch is migrated, the context could change. However, all subjects should be migrated too
    // and thus notified.
    D getDomainContext();
}

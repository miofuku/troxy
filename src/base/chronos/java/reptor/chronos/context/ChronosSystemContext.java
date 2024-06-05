package reptor.chronos.context;

import reptor.chronos.Commutative;

@Commutative
public interface ChronosSystemContext
{
    void    shutdownDomains();
}

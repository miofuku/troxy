package reptor.replct.service;

import reptor.distrbt.common.data.ImmutableData;


public interface ServiceInstance
{

    // TODO: cmd.execute( svcinst ) (cmd could get the client number during its construction, if required.)
    ImmutableData   processCommand(int clino, ImmutableData command, boolean readonly);

    void            applyUpdate(ImmutableData update);

    // TODO: Introduce ServiceCheckpoint
    ImmutableData   createCheckpoint();
    void            applyCheckpoint(ImmutableData checkpoint);
    boolean         createsFullCheckpoints();

}

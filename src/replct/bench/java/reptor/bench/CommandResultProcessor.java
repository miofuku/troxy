package reptor.bench;

import reptor.replct.service.ServiceCommand;


public interface CommandResultProcessor<C extends ServiceCommand>
{
    void    processResult(C command);
}

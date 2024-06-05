package reptor.replct.service;

import reptor.chronos.orphics.AttachableOrphic;
import reptor.distrbt.common.data.ImmutableData;

// TODO: ServiceInvocation = Command + Result?
public interface ServiceCommand extends AttachableOrphic<Object>
{
    ImmutableData   getData();
    boolean         isReadOnly();

    void            setResult(ImmutableData result);

    void            processResult();
}

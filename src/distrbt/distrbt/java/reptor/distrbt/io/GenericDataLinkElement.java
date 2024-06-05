package reptor.distrbt.io;

import reptor.chronos.com.SynchronousLinkElement;
import reptor.chronos.orphics.AttachableOrphic;
import reptor.distrbt.domains.SelectorDomainContext;


public interface GenericDataLinkElement extends SynchronousLinkElement, AttachableOrphic<DataChannelContext<? extends SelectorDomainContext>>
{

}
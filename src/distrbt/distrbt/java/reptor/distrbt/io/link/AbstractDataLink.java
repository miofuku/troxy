package reptor.distrbt.io.link;

import java.util.Objects;

import com.google.common.base.Preconditions;

import reptor.distrbt.domains.SelectorDomainContext;
import reptor.distrbt.io.AbstractDataLinkElement;
import reptor.distrbt.io.DataChannelContext;
import reptor.distrbt.io.DataLink;
import reptor.distrbt.io.GenericDataLinkElement;


public abstract class AbstractDataLink extends AbstractDataLinkElement
                                       implements DataLink, DataChannelContext<SelectorDomainContext>
{

    private DataChannelContext<? extends SelectorDomainContext>  m_master;


    @Override
    protected DataChannelContext<? extends SelectorDomainContext> master()
    {
        return m_master;
    }


    protected abstract GenericDataLinkElement source();


    protected abstract GenericDataLinkElement sink();


    @Override
    public void bindToMaster(DataChannelContext<? extends SelectorDomainContext> master)
    {
        Preconditions.checkState( m_master==null );

        m_master = master;

        source().bindToMaster( this );
        sink().bindToMaster( this );
    }


    @Override
    public void unbindFromMaster(DataChannelContext<? extends SelectorDomainContext> master)
    {
        Preconditions.checkArgument( Objects.requireNonNull( master )==m_master );

        clearReady();

        source().unbindFromMaster( this );
        sink().unbindFromMaster( this );

        m_master = null;
    }
}

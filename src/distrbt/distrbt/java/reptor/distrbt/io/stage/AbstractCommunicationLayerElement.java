package reptor.distrbt.io.stage;

import java.util.Objects;

import com.google.common.base.Preconditions;

import reptor.chronos.Notifying;
import reptor.chronos.com.CommunicationLayerElement;
import reptor.distrbt.domains.SelectorDomainContext;
import reptor.distrbt.io.AbstractDataLinkElement;
import reptor.distrbt.io.DataChannelContext;
import reptor.distrbt.io.GenericDataLinkElement;


public abstract class AbstractCommunicationLayerElement extends AbstractDataLinkElement
                                                        implements CommunicationLayerElement, GenericDataLinkElement
{
    private DataChannelContext<? extends SelectorDomainContext>  m_master;

    // The activation state is externally controlled, the enable state is internally controlled,
    // and the ready state signals the master if some work has to be carried out.
    // !activated -> !enabled -> !ready
    private boolean m_isactivated = false;

    @Override
    public void bindToMaster(DataChannelContext<? extends SelectorDomainContext> master)
    {
        Preconditions.checkState( m_master==null );
        Preconditions.checkState( !m_isactivated );

        m_master = master;
    }


    @Override
    public void unbindFromMaster(DataChannelContext<? extends SelectorDomainContext> master)
    {
        Preconditions.checkArgument( Objects.requireNonNull( master )==m_master );
        Preconditions.checkState( !m_isactivated );

        m_master = null;
    }


    @Override
    protected DataChannelContext<? extends SelectorDomainContext> master()
    {
        return m_master;
    }


    @Override
    @Notifying
    public void activate()
    {
        if( m_isactivated )
            return;

        Preconditions.checkState( m_master!=null );

        m_isactivated = true;
    }


    @Override
    public void deactivate()
    {
        if( !m_isactivated )
            return;

        Preconditions.checkState( m_master!=null );

        disable();

        m_isactivated = false;
    }


    protected void disable()
    {
        clearReady();
    }


    @Override
    public boolean isActivated()
    {
        return m_isactivated;
    }

}

package reptor.distrbt.io.select;

import java.util.Objects;

import com.google.common.base.Preconditions;

import reptor.chronos.com.CommunicationStage;
import reptor.chronos.com.SynchronousEndpoint;
import reptor.chronos.com.SynchronousLinkElement;
import reptor.chronos.com.SynchronousSink;
import reptor.chronos.com.SynchronousSource;
import reptor.distrbt.domains.SelectorDomainContext;
import reptor.distrbt.io.DataChannelContext;
import reptor.distrbt.io.GenericDataLinkElement;


public abstract class AbstractDataEndpointSelector<E extends SynchronousEndpoint & GenericDataLinkElement, I extends SynchronousSink, O extends SynchronousSource>
        implements CommunicationStage<I, O>, SynchronousEndpoint, GenericDataLinkElement, DataChannelContext<SelectorDomainContext>
{

    protected DataChannelContext<? extends SelectorDomainContext> m_master;

    protected E           m_selected    = null;
    protected boolean     m_isactivated = false;


    @Override
    public SelectorDomainContext getDomainContext()
    {
        return m_master.getDomainContext();
    }


    @Override
    public String getChannelName()
    {
        return m_master.getChannelName();
    }


    @Override
    public void bindToMaster(DataChannelContext<? extends SelectorDomainContext> master)
    {
        Preconditions.checkState( m_master==null );
        Preconditions.checkState( !m_isactivated );

        m_master = Objects.requireNonNull( master );

        if( m_selected!=null )
            m_selected.bindToMaster( this );
    }


    @Override
    public void unbindFromMaster(DataChannelContext<? extends SelectorDomainContext> master)
    {
        Preconditions.checkArgument( Objects.requireNonNull( master )==m_master );
        Preconditions.checkState( !m_isactivated );

        if( m_selected!=null )
            m_selected.unbindFromMaster( this );

        m_master = null;
    }


    public void select(E endpoint)
    {
        Preconditions.checkState( !m_isactivated );

        unselect();

        m_selected = endpoint;

        if( m_master!=null )
            m_selected.bindToMaster( this );
    }


    public void unselect()
    {
        Preconditions.checkState( !m_isactivated );

        if( m_selected==null )
            return;

        if( m_master!=null )
            m_selected.unbindFromMaster( this );

        m_selected = null;
    }


    @Override
    public void activate()
    {
        if( m_isactivated )
            return;

        Preconditions.checkState( m_master!=null );

        m_isactivated = true;

        if( m_selected!=null && m_selected.isReady() )
            m_master.dataReady( this );
    }


    @Override
    public void deactivate()
    {
        if( !m_isactivated )
            return;

        m_isactivated = false;
    }


    @Override
    public boolean isActivated()
    {
        return m_isactivated;
    }


    public void clear()
    {
        m_isactivated = false;
        unselect();
    }


    @Override
    public void dataReady(SynchronousLinkElement elem)
    {
        m_master.dataReady( this );
    }

}

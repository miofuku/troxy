package reptor.distrbt.io.channel;

import java.util.Objects;

import com.google.common.base.Preconditions;

import reptor.chronos.com.SynchronousLinkElement;
import reptor.chronos.com.SynchronousSink;
import reptor.chronos.com.SynchronousSource;
import reptor.distrbt.domains.SelectorDomainContext;
import reptor.distrbt.io.AbstractDataLinkElement;
import reptor.distrbt.io.DataChannelContext;
import reptor.distrbt.io.GenericDataLinkElement;


public abstract class BinaryDataChannel<I extends SynchronousSink, O extends SynchronousSource>
        extends AbstractDataLinkElement
        implements DataChannelContext<SelectorDomainContext>,
                   GenericDataLinkElement
{

    private DataChannelContext<? extends SelectorDomainContext> m_master;


    protected abstract GenericDataLinkElement               in();
    protected abstract GenericDataLinkElement               out();


    @Override
    public void bindToMaster(DataChannelContext<? extends SelectorDomainContext> master)
    {
        Preconditions.checkState( m_master==null );

        m_master = master;

        in().bindToMaster( this );
        out().bindToMaster( this );
    }


    @Override
    public void unbindFromMaster(DataChannelContext<? extends SelectorDomainContext> master)
    {
        Preconditions.checkArgument( Objects.requireNonNull( master )==m_master );

        clearReady();

        in().unbindFromMaster( this );
        out().unbindFromMaster( this );

        m_master = null;
    }


    @Override
    protected DataChannelContext<? extends SelectorDomainContext> master()
    {
        return m_master;
    }


    @Override
    public void dataReady(SynchronousLinkElement elem)
    {
        notifyReady();
    }

}
